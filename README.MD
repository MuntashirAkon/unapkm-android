# UnApkm Android

A utility app to convert apkm files to apks on Android.

[![](https://jitpack.io/v/MuntashirAkon/unapkm-android.svg)](https://jitpack.io/#MuntashirAkon/unapkm-android)

Objectives:
- To work as a utility app to convert apkm to apks
- To provide an interface to apps such as App Manager which can use the app as an extension

To use the app, open an apkm file using UnApkm from your favourite file manager. You will be prompted for a save location.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/io.github.muntashirakon.unapkm/)

## Quick start

### Install
UnApkm for Android is available via JitPack.

```groovy
// Top level build file
repositories {
    maven { url "https://jitpack.io" }
}

// Add to dependencies section
dependencies {
    implementation 'com.github.MuntashirAkon:unapkm-android:1.2'
}
```

### Example usage
See the example module for a working example.

```java
// You can also use InputStream but make sure that it's not a PipedInputStream or a pipe created by
// ParcelFileDescriptor#createPipe().
ParcelFileDescriptor inputPdf = ...;
OutputStream outputStream = ...;
// Service connection is handled automatically in the constructor, be sure to run it in a worker thread
UnApkm unApkm = new UnApkm(MainActivity.this, UN_APKM_PKG);
// Decrypt the APKM file
unApkm.decryptFile(descriptor, outputStream);
```

## Credits
- [terl/lazysodium-android](https://github.com/terl/lazysodium-android)
- [souramoo/unapkm](https://github.com/souramoo/unapkm)
