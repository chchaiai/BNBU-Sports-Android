# BNBU Sports Android

Android student client for BNBU Sports, built with Kotlin and Jetpack Compose.

## Requirements

- JDK 17
- Android SDK 35

## Build and test

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration

- Set `BNBU_API_BASE_URL` to override the debug API endpoint.
- Release builds require an HTTPS API URL ending in `/api`.
- Copy `keystore.properties.example` to the ignored `keystore.properties` only on a protected machine when configuring release signing.
- `app/google-services.json` and signing credentials are intentionally not tracked.
