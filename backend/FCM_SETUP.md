# FCM delivery setup

The Android app registers its opaque FCM token at `POST /api/v1/student/push-devices` after login and removes it at logout. Apply database migration `003_push_devices.sql` before enabling delivery.

## Required Firebase configuration

1. Register both `edu.bnbu.student.mvp` and `edu.bnbu.student.mvp.debug` in the same Firebase Android project.
2. Download the resulting `google-services.json` and place it at `app/google-services.json`. Release builds deliberately fail without it.
3. Give the trusted notification worker (not the Android app) permission to send Firebase Cloud Messaging messages. Keep its service-account credential in the deployment secret store; never add it to this repository, an APK, or a mobile request.

## Mandatory message contract

Send **data-only** FCM messages. The payload may contain only an opaque notification identifier:

```json
{
  "token": "<registered FCM token>",
  "data": { "notification_id": "<opaque notification UUID>" },
  "android": { "priority": "high" }
}
```

Do not set the FCM `notification` object and do not send a title, body, student number, name, email, course, grade, review outcome, or any other personal information. The Android client discards server-provided display text and shows a fixed generic alert; after the student opens it, the in-app notification center fetches authorized details from the existing API.

Treat registration tokens as personal data: restrict table access to the trusted sender, never expose them in APIs or logs, and invalidate tokens reported as no longer registered by FCM.

## iOS follow-up

This repository contains no iOS target. The iOS student app must configure the same Firebase project with APNs authentication, request notification permission, and use the same authenticated device-registration contract with an `ios` platform only after the backend platform enum and validation are expanded in that app's delivery change.
