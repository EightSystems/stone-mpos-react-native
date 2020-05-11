
import { NativeModules } from 'react-native';

const { RNStoneMposReactNative } = NativeModules;

const Stone = {
    isDeviceConnected: () => {
        return RNStoneMposReactNative.isDeviceConnected();
    },
    displayMessageAtPinpad: (message) => {
        return RNStoneMposReactNative.displayMessageAtPinpad(message);
    },

    makeTransaction: (transactionSetup, successMessage) => {
        return RNStoneMposReactNative.makeTransaction(transactionSetup, successMessage);
    },

    /**
    * transactionCode is the mposId field in the transaction object
    */
    cancelTransaction: (transactionCode) => {
        return RNStoneMposReactNative.cancelTransaction(transactionCode);
    },

    setEnvironment: (environment) => {
        /*
        PRODUCTION,
        INTERNAL_HOMOLOG,
        SANDBOX,
        STAGING,
        INTERNAL_CERTIFICATION;
        */
        return RNStoneMposReactNative.setEnvironment(environment);
    },

    initSDK: (stoneCode, environment = "") => {
        return RNStoneMposReactNative.initSDK(stoneCode, environment);
    },

    activateStoneCode: (stoneCode) => {
        return RNStoneMposReactNative.activateStoneCode(stoneCode)
    },

    deactivateStoneCode: (stoneCode) => {
        return RNStoneMposReactNative.deactivateStoneCode(stoneCode)
    },

    /**
    * @returns Promise(ArrayOf({ name: String, address: String }))
    */
    getPairedBluetoothDevices() {
        return RNStoneMposReactNative.getPairedBluetoothDevices()
    },

    /**
    * Device: { name: String, address: String }
    */
    connectDevice(device, dialogMessage = "Aguarde") {
        return RNStoneMposReactNative.connectDevice(device.name, device.address, dialogMessage);
    },

    /**
    * Returns null if none is connected
    */
    getConnectedDevice() {
        return RNStoneMposReactNative.getConnectedDevice()
    },

    /**
    * @returns ArrayOf({
    *  mposId: string,
    *  amount: string,
    *  status: string,
    *  initiatorTransactionKey: string,
    *  recipientTransactionIdentification: string
    *  cardHolderName: string,
    *  cardNumber: string,
    *  cardBrand: string,
    *  authorizationCode: string
    *  sak: string
    * })
    */
    getAllTransactionsOrderByIdDesc() {
        return RNStoneMposReactNative.getAllTransactionsOrderByIdDesc();
    },

    /**
    *
    * Status:
    *     TRANSACTION_WAITING_CARD,
    *     TRANSACTION_WAITING_SWIPE_CARD,
    *     TRANSACTION_WAITING_PASSWORD,
    *     TRANSACTION_SENDING,
    *     TRANSACTION_REMOVE_CARD,
    *     REVERSING_TRANSACTION_WITH_ERROR,
    *     TRANSACTION_CARD_REMOVED,
    *     TRANSACTION_TYPE_SELECTION;
    */
    async makeTransaction(transactionSetup, opts) {
        opts = opts || {}

        let subscription
        if ( opts.onStatusChange ) {
            subscription = DeviceEventEmitter.addListener(
                RNStone.EVT_TRANSACTION_STATUS_CHANGED,
                opts.onStatusChange
            )
        }

        const result = await RNStoneMposReactNative.sendTransaction(transactionSetup);

        if(subscription) {
            subscription.remove();
        }

        return result
    },

    findTransactionWithInitiatorTransactionKey(initiatorTransactionKey) {
        return RNStoneMposReactNative.findTransactionWithInitiatorTransactionKey(initiatorTransactionKey);
    },

    findTransactionWithId(transactionId) {
        return RNStoneMposReactNative.findTransactionWithId(transactionId);
    },

    findTransactionWithAuthorizationCode(authorizationCode) {
        return RNStoneMposReactNative.findTransactionWithAuthorizationCode(authorizationCode);
    },

    getLastTransactionId() {
        return RNStoneMposReactNative.getLastTransactionId();
    },

    getLastTransaction() {
        return RNStoneMposReactNative.getLastTransaction();
    },

    environment: {
        PRODUCTION: "PRODUCTION",
        INTERNAL_HOMOLOG: "INTERNAL_HOMOLOG",
        SANDBOX: "SANDBOX",
        STAGING: "STAGING",
        INTERNAL_CERTIFICATION: "INTERNAL_CERTIFICATION"
    },

    transactionMethod: {
        DEBIT: 'DEBIT',
        CREDIT: 'CREDIT'
    },

    transactionInstalment: {
        ONE_INSTALMENT: 'ONE_INSTALMENT',
        TWO_INSTALMENT_NO_INTEREST: 'TWO_INSTALMENT_NO_INTEREST',
        THREE_INSTALMENT_NO_INTEREST: 'THREE_INSTALMENT_NO_INTEREST',
        FOUR_INSTALMENT_NO_INTEREST: 'FOUR_INSTALMENT_NO_INTEREST',
        FIVE_INSTALMENT_NO_INTEREST: 'FIVE_INSTALMENT_NO_INTEREST',
        SIX_INSTALMENT_NO_INTEREST: 'SIX_INSTALMENT_NO_INTEREST',
        SEVEN_INSTALMENT_NO_INTEREST: 'SEVEN_INSTALMENT_NO_INTEREST',
        EIGHT_INSTALMENT_NO_INTEREST: 'EIGHT_INSTALMENT_NO_INTEREST',
        NINE_INSTALMENT_NO_INTEREST: 'NINE_INSTALMENT_NO_INTEREST',
        TEN_INSTALMENT_NO_INTEREST: 'TEN_INSTALMENT_NO_INTEREST',
        ELEVEN_INSTALMENT_NO_INTEREST: 'ELEVEN_INSTALMENT_NO_INTEREST',
        TWELVE_INSTALMENT_NO_INTEREST: 'TWELVE_INSTALMENT_NO_INTEREST',
        TWO_INSTALMENT_WITH_INTEREST: 'TWO_INSTALMENT_WITH_INTEREST',
        THREE_INSTALMENT_WITH_INTEREST: 'THREE_INSTALMENT_WITH_INTEREST',
        FOUR_INSTALMENT_WITH_INTEREST: 'FOUR_INSTALMENT_WITH_INTEREST',
        FIVE_INSTALMENT_WITH_INTEREST: 'FIVE_INSTALMENT_WITH_INTEREST',
        SIX_INSTALMENT_WITH_INTEREST: 'SIX_INSTALMENT_WITH_INTEREST',
        SEVEN_INSTALMENT_WITH_INTEREST: 'SEVEN_INSTALMENT_WITH_INTEREST',
        EIGHT_INSTALMENT_WITH_INTEREST: 'EIGHT_INSTALMENT_WITH_INTEREST',
        NINE_INSTALMENT_WITH_INTEREST: 'NINE_INSTALMENT_WITH_INTEREST',
        TEN_INSTALMENT_WITH_INTEREST: 'TEN_INSTALMENT_WITH_INTEREST',
        ELEVEN_INSTALMENT_WITH_INTEREST: 'ELEVEN_INSTALMENT_WITH_INTEREST',
        TWELVE_INSTALMENT_WITH_INTEREST: 'TWELVE_INSTALMENT_WITH_INTEREST'
    }
}

export default Stone;
