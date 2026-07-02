# BNBU Student Android

Android student app scaffold aligned with the existing iOS SwiftUI MVP and Web backend handoff.

## Current Step

Step 16 adds Android release readiness notes and R8/Gson guardrails:

- Native Android app with Kotlin + Jetpack Compose
- Package namespace: `edu.bnbu.student.mvp`
- BNBU Swiss-style design tokens translated from the iOS app
- Core student data models translated from the iOS app
- Mock student workspace and AppState translated from the iOS app
- Five root tabs aligned with iOS: Dashboard, Courses, Check-In, Grades, Profile
- Login screen aligned with iOS `LoginView`: BNBU hero, demo login panel, and student demo entry
- Dashboard screen aligned with iOS `DashboardView`: sports credit progress, metrics, risk, action panel, weekly plan, recent tasks, and notices
- Courses screen aligned with iOS `CoursesView`: course list, `courseCode / Section`, course facts, completion progress, and in-app course detail view with class tasks and related records
- Check-in screen aligned with iOS `CheckInView`: Tasks / Submit / Records segments, task filters, record filters, active-task selection, local note input, hour stepper, demo proof attachments, validation rules, draft restore/clear, submit confirmation, pending-record creation, and supplement/re-submit flow
- Grades screen aligned with iOS `GradesView`: total score estimate, four weighted component cards, transparent formula breakdown, missing-item risk panel, and source trace
- Profile screen aligned with iOS `ProfileView`: student header, organization offset status, notification filters and detail view, mark-read actions, local sync readiness, settings, logout, reset demo data action, and debug state
- Backend-ready Android API boundary aligned with iOS `StudentAPIClient`: auth, sport summary, record submit/detail/supplement, sport identity, notifications, mark-read request specs, and unit coverage for endpoint construction
- Android local persistence aligned with iOS `AppLocalStore`: `SharedPreferences` workspace and draft storage, boot-time restore, stale draft discard, save health state, reset handling, and JSON round-trip tests
- Native Android proof attachment picker for check-in submissions: system image/video selection, persistable URI access where available, filename/size/duration metadata, upload-rule validation, and demo proof fallback
- Shell cleanup for handoff: removed unused placeholder shell code, trimmed App root imports, and kept the running tabs pointed only at production MVP screens
- QA and handoff runbook: Android Studio run steps, APK path, command verification, five-tab test flow, persistence checks, backend boundary, MVP limits, and handoff criteria
- Release readiness notes: signing, minification, privacy, storage, upload, backend, security, and release-build verification checklist

No backend integration is implemented yet. The app will mirror iOS first, then keep the same reserved API boundary for future backend work.

## QA Handoff

Use this runbook for Android MVP verification:

```text
ANDROID_MVP_QA_HANDOFF.md
```

## Release Readiness

Use this checklist before any production-facing release:

```text
ANDROID_RELEASE_READINESS.md
```

## Open In Android Studio

Open this folder:

```text
android/BNBUStudentAndroid
```

Then sync Gradle and run the `app` target.
