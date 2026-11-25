# Social Macro Controls

Android 13+ prototype for four MacroDroid flows: TikTok TESTING 100, TikTok CHANCE 20, Instagram Feed, and Instagram Reels.

The app stores a single Gemini key in Android Keystore-backed storage and prepares a MacroDroid import on the device. The bundled template contains four disabled test macros, no Gemini key, and no reusable command token. The app generates the command token locally. The prototype uses Android Accessibility to identify views, type characters into a verified composer, and check that Comments is closed before advancing.

## Verification status

The setup screen and selective import of one disabled test macro were checked on a phone while debugging was enabled. The four end-to-end flows with debugging disabled are unverified. Automatic following from the older Shizuku flows is absent. Treat this as test source and keep the existing flows installed until you verify the replacement.

## Build

With JDK 17, Android SDK Platform 36, and Android Build Tools 36.0.0 installed, set the JDK and SDK paths and run:

```powershell
./build.ps1 -JdkRoot 'C:/path/to/jdk-17' -AndroidSdk 'C:/path/to/Android/Sdk'
```

This creates a locally signed development APK in `build/`. The development signing key also stays under `build/`. On the test phone, grant the app's Accessibility permission, enter a Gemini API key once, and choose **Open four-macro import**. In MacroDroid use **Select items to import** to preserve existing macros. Four test entries require four available slots. The device-only navigation checks require both USB and Wireless debugging to be off and do not Like or post.
