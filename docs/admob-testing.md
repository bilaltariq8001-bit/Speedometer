# AdMob test setup

Debug builds use Google's official demo units for app-open, anchored adaptive banner, and interstitial ads. Release builds read ad-unit IDs from Firebase Remote Config, with the same demo units as their in-app defaults until production IDs are supplied.

UMP remains the consent gate for every format. The demo units and optional test-device registration do not override consent.

To register a physical device, make an ad request and copy the hashed test-device ID that Google Mobile Ads prints in Logcat (look for `Use RequestConfiguration.Builder.setTestDeviceIds`). Pass that value as a Gradle project property:

```powershell
.\gradlew.bat :app:assembleDebug -PadmobTestDeviceId=HASH_FROM_LOGCAT
```

The project passes the supplied value to `RequestConfiguration` before initializing the SDK. Leave the property unset when no device-specific registration is needed. Do not use the ADB serial number as the AdMob test-device ID.

Replace the AdMob App ID in `app/src/main/res/values/strings.xml` and configure production ad-unit IDs in Firebase Remote Config before serving production ads.
