# BNBU Student Android Release Readiness

Last updated: 2026-06-14

This document records the current Android release posture for the native MVP. The app is suitable for internal MVP demonstration and QA, but it is not ready for public production launch until the blocking items below are completed.

## Current Release Status

- Debug APK builds successfully.
- Release APK can be built for technical verification.
- App uses native Kotlin + Jetpack Compose.
- All current app data is local mock data.
- Backend request boundaries are reserved but not connected.
- Local persistence uses `SharedPreferences`.
- Android backup is disabled via manifest and XML rules.
- Image/video proof selection uses Android system document picker.

## Current Build Identity

- Namespace: `edu.bnbu.student.mvp`
- Release application id: `edu.bnbu.student.mvp`
- Debug application id: `edu.bnbu.student.mvp.debug`
- Version code: `1`
- Version name: `0.1.0-mvp`
- Min SDK: `26`
- Target SDK: `35`
- Compile SDK: `35`

## Verification Commands

Run from:

```text
D:\DT\soprts\android\BNBUStudentAndroid
```

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
.\gradlew.bat :app:assembleRelease --console=plain --no-daemon
```

Expected result for MVP handoff: all commands complete successfully.

## Blocking Before Production Launch

- Configure official release signing in a secure keystore flow.
- Replace mock repository with authenticated backend repository.
- Implement production login and token storage.
- Implement real file upload for selected proof media.
- Decide whether release minification/R8 should be enabled and validate it on a real device matrix.
- Add backend error states, retry, loading, timeout, and offline behavior.
- Add privacy policy and data collection disclosure.
- Complete accessibility review.
- Complete visual QA on small phone, large phone, and tablet-like widths.
- Complete security review for local storage, media URI retention, and API transport.
- Add CI build checks.

## Current Security And Privacy Notes

- `android:allowBackup="false"` is set.
- `backup_rules.xml` excludes shared preferences.
- `data_extraction_rules.xml` excludes shared preferences from cloud backup and device transfer.
- No dangerous runtime permissions are declared in the manifest.
- System media picker is used instead of broad storage permission.
- Proof attachments currently store metadata and URI source only; no media bytes are uploaded.
- Release signing is not configured yet.

## R8 And Gson Notes

`proguard-rules.pro` now includes keep rules for model and local persistence classes. These rules protect Gson-based local JSON restore if R8 is enabled later.

Current `release` build type still has:

```kotlin
isMinifyEnabled = false
```

This is acceptable for MVP demo builds, but production release should make an explicit minification decision after backend DTOs, upload models, and persistence migration strategy are finalized.

## Release Build Outputs

Debug APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Release APK, unsigned unless signing is configured:

```text
app\build\outputs\apk\release\app-release-unsigned.apk
```

## Stage Handoff Decision

The Android MVP can be handed off for internal review when:

- Debug and release build commands pass.
- QA handoff checklist passes on at least one emulator.
- Five tabs visually align with the iOS MVP.
- Check-in proof selection, draft, submit, supplement, and persistence flows work locally.
- Known MVP limits are communicated to reviewers.

The app should not be described as production-ready until all blocking items above are closed.
