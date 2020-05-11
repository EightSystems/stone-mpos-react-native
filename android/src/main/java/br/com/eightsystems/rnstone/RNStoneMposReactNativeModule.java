package br.com.eightsystems.rnstone;

/*
 * All this work is based on https://github.com/hashlab/plugin-react-native and https://github.com/hashlab/plugin-react-native/pull/2
 * I just updated it to the latest version, and created the IOS version.
 * Big Thanks to https://github.com/jgabrielfaria and https://github.com/felipeblassioli
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rustfisher.btscanner.BtDeviceItem;
import com.rustfisher.btscanner.BtScanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import stone.application.StoneStart;
import stone.application.enums.Action;
import stone.application.enums.InstalmentTransactionEnum;
import stone.application.enums.TransactionStatusEnum;
import stone.application.enums.TypeOfTransactionEnum;
import stone.application.interfaces.StoneActionCallback;
import stone.application.interfaces.StoneCallbackInterface;
import stone.database.transaction.TransactionDAO;
import stone.database.transaction.TransactionObject;
import stone.environment.Environment;
import stone.providers.ActiveApplicationProvider;
import stone.providers.BluetoothConnectionProvider;
import stone.providers.CancellationProvider;
import stone.providers.DisplayMessageProvider;
import stone.providers.TransactionProvider;
import stone.utils.PinpadObject;
import stone.utils.Stone;

public class RNStoneMposReactNativeModule extends ReactContextBaseJavaModule {

    private static final String TAG = "RNStoneMposModule";
    private static final String EVT_TRANSACTION_STATUS_CHANGED = "EVT_TRANSACTION_STATUS_CHANGED";
    private static final List<Callback> bluetoothSearchScannerInstances = new ArrayList<>();
    private static List<BtDeviceItem> bluetoothSearchScannerDevices = new ArrayList<>();
    private static BtDeviceItem bluetoothSearchScannerPairingDevice = null;
    private static Callback bluetoothSearchScannerPairingCallback = null;
    private static BtScanner mScannerInstance = null;
    private final ReactApplicationContext reactContext;

    public static class PairingRequest extends BroadcastReceiver {
        public PairingRequest() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if ( intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED) ) {
                if ( bluetoothSearchScannerPairingDevice != null ) {
                    try {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                        if ( device.getAddress() == bluetoothSearchScannerPairingDevice.getAddress() ) {
                            if ( bluetoothSearchScannerPairingCallback != null ) {
                                int currentState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                                bluetoothSearchScannerPairingCallback.invoke(currentState == BluetoothDevice.BOND_BONDED);

                                bluetoothSearchScannerPairingCallback = null;
                                bluetoothSearchScannerPairingDevice = null;
                            }
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public RNStoneMposReactNativeModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        this.reactContext.registerReceiver(
                new PairingRequest(),
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        );
    }

    @Override
    public String getName() {
        return "RNStoneMposReactNative";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(EVT_TRANSACTION_STATUS_CHANGED, EVT_TRANSACTION_STATUS_CHANGED);
        return constants;
    }

    /*
     * Init SDK, you need to call it before calling any method
     */
    @ReactMethod
    public void initSDK(String appName, String environment, final Promise promise) {
        try {
            StoneStart.init(this.reactContext);
            Stone.setAppName(appName);

            if ( !environment.isEmpty() ) {
                Stone.setEnvironment(Environment.valueOf(environment));
            }

            promise.resolve(true);
        } catch(Exception e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    public void setEnvironment(String environment, final Promise promise) {
        try {
            Stone.setEnvironment(Environment.valueOf(environment));
            promise.resolve(true);
        } catch(Exception e) {
            promise.reject(e);
        }
    }

    /*
     * Activation methods
     */
    private ActiveApplicationProvider getStoneActivationProvider(String dialogMessage, String dialogTitle, final Promise promise) {
        final ActiveApplicationProvider activeApplicationProvider = new ActiveApplicationProvider(reactContext);
        activeApplicationProvider.setDialogMessage(dialogMessage);
        activeApplicationProvider.setDialogTitle(dialogTitle);
        activeApplicationProvider.useDefaultUI(false);
        activeApplicationProvider.setConnectionCallback(new StoneCallbackInterface() {
            public void onSuccess() {
                promise.resolve(true);
            }

            public void onError() {
                promise.reject("201", activeApplicationProvider.getListOfErrors().toString());
            }
        });

        return activeApplicationProvider;
    }

    private boolean isSDKInitializedPromise(Promise promise) {
        if ( ! Stone.isInitialized() ) {
            promise.reject("301", "Stone SDK is not initialized");
            return false;
        }

        return true;
    }

    @ReactMethod
    public void activateStoneCode(String stoneCode, String dialogMessage, String dialogTitle, Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            getStoneActivationProvider(dialogMessage, dialogTitle, promise).activate(stoneCode);
        }
    }

    @ReactMethod
    public void deactivateStoneCode(String stoneCode, String dialogMessage, String dialogTitle, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            getStoneActivationProvider(dialogMessage, dialogTitle, promise).deactivate(stoneCode);
        }
    }


    /*
     * Bluetooth device scanning helpers
     */
    @ReactMethod
    public void startScanBluetoothDevices(final String channelId, int inputScanPeriod, final Callback scanResultsCallback) {
        if ( mScannerInstance == null ) {
            BtScanner mScanner = new BtScanner(inputScanPeriod);

            mScanner.setLoadBondDevice(true); //Load the paired (bond) devices too
            mScanner.setNotifyInterval(500); //Notify every half a second
            mScanner.setScanPeriod(30000); //Scan for 30 seconds
            mScanner.addListener(new BtScanner.Listener() {
                @Override
                public void onDeviceListUpdated(ArrayList<BtDeviceItem> list) {
                    //Update local list for when we need to bond the device
                    bluetoothSearchScannerDevices = list;

                    WritableMap params = Arguments.createMap();
                    WritableArray rList = Arguments.createArray();

                    for ( BtDeviceItem mDevice: list ) {
                        WritableMap deviceInfo = Arguments.createMap();
                        deviceInfo.putString("name", mDevice.getName());
                        deviceInfo.putString("address", mDevice.getAddress());
                        deviceInfo.putBoolean("isPaired", mDevice.getBluetoothDevice().getBondState() == BluetoothDevice.BOND_BONDED);
                        deviceInfo.putBoolean("isPairing", mDevice.getBluetoothDevice().getBondState() == BluetoothDevice.BOND_BONDING);

                        rList.pushMap(deviceInfo);
                    }

                    params.putString("type", "scanResults");
                    params.putArray("list", rList);

                    notifyBluetoothScanResults(params);
                }

                @Override
                public void onScanning(boolean scan) {
                    WritableMap params = Arguments.createMap();
                    params.putString("type", "scanStatus");
                    params.putBoolean("status", scan);

                    notifyBluetoothScanResults(params);
                }
            });
        }

        bluetoothSearchScannerInstances.add(scanResultsCallback);
    }

    private void notifyBluetoothScanResults(WritableMap mResult) {
        for ( Callback mScanResultCallback: bluetoothSearchScannerInstances ) {
            try {
                mScanResultCallback.invoke(mResult);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    @ReactMethod
    public void stopScanBluetoothDevices(Promise promise) {
        if ( mScannerInstance != null ) {
            if ( mScannerInstance.isScanning() ) {
                mScannerInstance.stopScan();
                mScannerInstance.clearListener();
                mScannerInstance = null;
                bluetoothSearchScannerInstances.clear();

                promise.resolve(true);
            }
            else {
                promise.reject("101", "Channel is not scanning");
            }
        }
        else {
            promise.reject("102", "Channel doesn't exist");
        }
    }

    @ReactMethod
    public void pairBluetoothDevice(String deviceAddress, Callback pairingCallback) {
        if ( bluetoothSearchScannerDevices.size() > 0 ) {
            for ( BtDeviceItem mDeviceItem: bluetoothSearchScannerDevices ) {
                if ( mDeviceItem.getAddress().equalsIgnoreCase(deviceAddress) ) {
                    bluetoothSearchScannerPairingDevice = mDeviceItem;
                    bluetoothSearchScannerPairingCallback = pairingCallback;
                    mDeviceItem.getBluetoothDevice().createBond();

                    return;
                }
            }
        }

        pairingCallback.invoke(-1);
    }

    @ReactMethod
    public void getPairedBluetoothDevices(Callback scanResultsCallback) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        WritableArray array = Arguments.createArray();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                WritableMap obj = Arguments.createMap();
                obj.putString("name", device.getName());
                obj.putString("address", device.getAddress());
                obj.putBoolean("isPaired", true);
                obj.putBoolean("isPairing", false);

                array.pushMap(obj);
            }
        }

        scanResultsCallback.invoke(array);
    }

    /*
     * Pinpad Methods
     * Ex: Connection, disconnecting, checking connection status, displaying messages, and on so.
     */
    @ReactMethod
    public void connectDevice(String deviceName, String deviceAddress, String dialogMessage, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            if ( ! Stone.isConnectedToPinpad() ) {
                PinpadObject pinpadObject = new PinpadObject(deviceName, deviceAddress, false);

                final BluetoothConnectionProvider bluetoothConnectionProvider = new BluetoothConnectionProvider(reactContext, pinpadObject);
                bluetoothConnectionProvider.setDialogMessage(dialogMessage);
                bluetoothConnectionProvider.setConnectionCallback(new StoneCallbackInterface() {
                    public void onSuccess() {
                        promise.resolve(true);
                    }

                    public void onError() {
                        promise.reject("103", "Connection Error: " + bluetoothConnectionProvider.getListOfErrors().toString());
                    }
                });

                bluetoothConnectionProvider.execute();
            }
            else {
                promise.reject("302", "This module only supports connecting to one pinpad at a time");
            }
        }
    }

    @ReactMethod
    public void isDeviceConnected(Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            promise.resolve(
                    Stone.isConnectedToPinpad()
            );
        }
    }

    @ReactMethod
    public void getConnectedDevice(final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            if (Stone.isConnectedToPinpad()) {
                PinpadObject p = Stone.getPinpadFromListAt(0);
                WritableMap device = Arguments.createMap();
                device.putString("name", p.getName());
                device.putString("address", p.getMacAddress());
                promise.resolve(device);
            } else {
                promise.resolve(null);
            }
        }
    }

    @ReactMethod
    public void displayMessageAtPinpad(String message, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            try {
                if ( Stone.isConnectedToPinpad() ) {
                    PinpadObject pinpadObject = Stone.getPinpadFromListAt(0);
                    final DisplayMessageProvider displayMessageProvider = new DisplayMessageProvider(reactContext, message, pinpadObject);
                    displayMessageProvider.setWorkInBackground(false);

                    displayMessageProvider.setConnectionCallback(new StoneCallbackInterface() {
                        @Override
                        public void onSuccess() {
                            promise.resolve(true);
                        }

                        @Override
                        public void onError() {
                            promise.reject("107", "Connection error: " + displayMessageProvider.getListOfErrors().toString());
                        }
                    });

                    displayMessageProvider.execute();
                } else {
                    promise.reject("105", "No pinpad at list, please, try connecting to a Pinpad");
                }
            } catch (Exception e) {
                promise.reject("106", e.getMessage());
            }
        }
    }

    /*
     * Transaction methods
     * Ex: Authorizing a transaction, listing all the transactions in the current device, and etc
     */

    private WritableMap convertTransactionObjectToWritableMap(TransactionObject trx) {
        WritableMap obj = Arguments.createMap();

        if ( trx != null ) {
            String initiatorKey = String.valueOf(trx.getInitiatorTransactionKey());
            String rcptTrx = String.valueOf(trx.getRecipientTransactionIdentification());
            String cardHolder = String.valueOf(trx.getCardHolderName());
            String cardNumber = String.valueOf(trx.getCardHolderNumber());
            String cardBrand = String.valueOf(trx.getCardBrand());
            String authorizationCode = String.valueOf(trx.getAuthorizationCode());

            obj.putString("mposId", String.valueOf(trx.getIdFromBase()));
            obj.putString("amount", trx.getAmount());
            obj.putString("status", trx.getTransactionStatus().toString());
            obj.putString("initiatorTransactionKey", initiatorKey);
            obj.putString("recipientTransactionIdentification", rcptTrx);
            obj.putString("cardHolderName", cardHolder);
            obj.putString("cardNumber", cardNumber);
            obj.putString("cardBrand", cardBrand);
            obj.putString("authorizationCode", authorizationCode);
            obj.putString("sak", trx.getSaleAffiliationKey());
        }

        return obj;
    }

    /*
     * STARTS TRANSACTION LISTING, AND FINDING
     */
    @ReactMethod
    public void getLastTransaction(final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            TransactionDAO transactionDAO = new TransactionDAO(reactContext);
            TransactionObject trx = transactionDAO.findTransactionWithId(transactionDAO.getLastTransactionId());

            promise.resolve(convertTransactionObjectToWritableMap(trx));
        }
    }

    @ReactMethod
    public void getLastTransactionId(final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            TransactionDAO transactionDAO = new TransactionDAO(reactContext);
            promise.resolve(String.valueOf(transactionDAO.getLastTransactionId()));
        }
    }

    @ReactMethod
    public void findTransactionWithAuthorizationCode(String authorizationCode, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            TransactionDAO transactionDAO = new TransactionDAO(reactContext);
            TransactionObject trx = transactionDAO.findTransactionWithAuthorizationCode(authorizationCode);

            promise.resolve(convertTransactionObjectToWritableMap(trx));
        }
    }

    @ReactMethod
    public void findTransactionWithInitiatorTransactionKey(String initiatorTransactionKey, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            TransactionDAO transactionDAO = new TransactionDAO(reactContext);
            TransactionObject trx = transactionDAO.findTransactionWithInitiatorTransactionKey(initiatorTransactionKey);

            promise.resolve(convertTransactionObjectToWritableMap(trx));
        }
    }

    @ReactMethod
    public void findTransactionWithId(String transactionId, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            TransactionDAO transactionDAO = new TransactionDAO(reactContext);
            TransactionObject trx = transactionDAO.findTransactionWithId(Integer.parseInt(transactionId));

            promise.resolve(convertTransactionObjectToWritableMap(trx));
        }
    }

    @ReactMethod
    public void getAllTransactionsOrderByIdDesc(final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            TransactionDAO transactionDAO = new TransactionDAO(reactContext);

            List<TransactionObject> transactionObjects = transactionDAO.getAllTransactionsOrderByIdDesc();
            try {
                WritableArray array = Arguments.createArray();

                for (TransactionObject transactionObject : transactionObjects) {
                    array.pushMap(convertTransactionObjectToWritableMap(transactionObject));
                }

                promise.resolve(array);
            } catch (Exception e) {
                promise.reject(e);
            }
        }
    }

    /*
     * ENDS TRANSACTION LISTING, AND FINDING
     */

    /*
     * STARTS TRANSACTION CONTROLLER
     */

    @ReactMethod
    public void cancelTransaction(String transactionCode, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {
            try {
                String[] parts = transactionCode.split("_");

                String idOptSelected = parts[0];
                final int transactionId = Integer.parseInt(idOptSelected);

                TransactionDAO transactionDAO = new TransactionDAO(reactContext);
                TransactionObject trx = transactionDAO.findTransactionWithId(transactionId);

                final CancellationProvider cancellationProvider = new CancellationProvider(reactContext, trx);
                cancellationProvider.setConnectionCallback(new StoneCallbackInterface() {
                    public void onSuccess() {
                        promise.resolve(true);
                    }

                    public void onError() {
                        promise.reject("402", "Error cancelling the transaction: " + cancellationProvider.getListOfErrors().toString());
                    }
                });

                cancellationProvider.execute();
            } catch (Exception e) {
                promise.reject("401", e.getMessage());
            }
        }
    }

    @ReactMethod
    public void makeTransaction(ReadableMap transactionSetup, final Promise promise) {
        if ( isSDKInitializedPromise(promise) ) {

            if (!Stone.isConnectedToPinpad()) {
                promise.reject("402", "You need to connect to a pinpad first");
                return;
            }

            try {
                PinpadObject pinpadObject = Stone.getPinpadFromListAt(0);

                final TransactionObject stoneTransaction = new TransactionObject();

                if ( transactionSetup.hasKey("initiatorTransactionKey") ) {
                    if ( transactionSetup.getString("initiatorTransactionKey").length() > 0 ) {
                        stoneTransaction.setInitiatorTransactionKey(transactionSetup.getString("initiatorTransactionKey"));
                    }
                }

                stoneTransaction.setAmount(transactionSetup.getString("amountInCent"));

                stoneTransaction.setUserModel(Stone.getUserModel(0));
                stoneTransaction.setCapture(
                        transactionSetup.hasKey("autoCapture") ? transactionSetup.getBoolean("autoCapture") : true
                );

                stoneTransaction.setShortName(
                        transactionSetup.hasKey("shortName") ? transactionSetup.getString("shortName") : ""
                );

                if (transactionSetup.getString("transactionType").equals("DEBIT")) {
                    stoneTransaction.setInstalmentTransaction(InstalmentTransactionEnum.getAt(0));
                    stoneTransaction.setTypeOfTransaction(TypeOfTransactionEnum.DEBIT);
                } else if (transactionSetup.getString("transactionType").equals("CREDIT")) {
                    stoneTransaction.setInstalmentTransaction(
                            InstalmentTransactionEnum.valueOf(
                                    transactionSetup.hasKey("installmentTransaction") ? transactionSetup.getString("installmentTransaction") : "ONE_INSTALMENT"
                            )
                    );

                    stoneTransaction.setTypeOfTransaction(TypeOfTransactionEnum.CREDIT);
                } else {
                    promise.reject("403", "Invalid Payment Method");
                }


                final TransactionProvider provider = new TransactionProvider(
                        reactContext,
                        stoneTransaction,
                        Stone.getUserModel(0),
                        pinpadObject
                );

                provider.useDefaultUI(false);
                provider.setDialogMessage(transactionSetup.hasKey("dialogMessage") ? transactionSetup.getString("dialogMessage") : "Enviando..");
                provider.setDialogTitle(transactionSetup.hasKey("dialogTitle") ? transactionSetup.getString("dialogTitle") : "Aguarde");

                provider.setConnectionCallback(new StoneActionCallback() {
                    @Override
                    public void onStatusChanged(Action action) {
                        WritableMap payload = Arguments.createMap();
                        payload.putString("initiatorTransactionKey", stoneTransaction.getInitiatorTransactionKey());
                        payload.putString("status", action.name());

                        reactContext
                                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit(EVT_TRANSACTION_STATUS_CHANGED, payload);
                    }

                    public void onSuccess() {
                        if (provider.getTransactionStatus() == TransactionStatusEnum.APPROVED) {
                            promise.resolve(true);
                        } else {
                            promise.reject("404", "Transaction Error: \"" + provider.getMessageFromAuthorize() + "\"");
                        }
                    }

                    public void onError() {
                        promise.reject("405", "Transaction Failed");
                    }
                });
                provider.execute();
            } catch (IndexOutOfBoundsException e) {
                promise.reject("406", "Pinpad not setup");
            } catch (Exception e) {
                promise.reject(e);
            }
        }
    }

    /*
     * ENDS TRANSACTION CONTROLLER
     */
}