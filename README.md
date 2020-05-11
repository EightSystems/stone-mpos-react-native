
# -stone-mpos-react-native

## Getting started

`$ npm install -stone-mpos-react-native --save`

### Mostly automatic installation

`$ react-native link -stone-mpos-react-native`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `-stone-mpos-react-native` and add `RNStoneMposReactNative.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNStoneMposReactNative.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import br.com.eightsystems.rnstone.RNStoneMposReactNativePackage;` to the imports at the top of the file
  - Add `new RNStoneMposReactNativePackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':-stone-mpos-react-native'
  	project(':-stone-mpos-react-native').projectDir = new File(rootProject.projectDir, 	'../node_modules/-stone-mpos-react-native/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':-stone-mpos-react-native')
  	```


## Usage
```javascript
import RNStoneMposReactNative from '-stone-mpos-react-native';

// TODO: What to do with the module?
RNStoneMposReactNative;
```
  