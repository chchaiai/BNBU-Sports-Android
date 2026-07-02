# BNBU Student Android MVP QA Handoff

Last updated: 2026-06-14

This document is the Android MVP runbook for reviewers, testers, and teammates. The current build is a native Android Jetpack Compose app aligned with the iOS MVP and Web backend handoff. It uses local mock data, local persistence, and reserved API request boundaries; it does not call a production backend yet.

## Build Profile

- App name: BNBU Student
- Package namespace: `edu.bnbu.student.mvp`
- Debug application id: `edu.bnbu.student.mvp.debug`
- Version: `0.1.0-mvp-debug`
- Min SDK: 26
- Target SDK: 35
- UI framework: Kotlin + Jetpack Compose
- Local data: Mock repository + `SharedPreferences`
- API boundary: request specs under `core/network`

## Open And Run

1. Open Android Studio.
2. Choose `File -> Open`.
3. Select:

```text
D:\DT\soprts\android\BNBUStudentAndroid
```

4. Wait for Gradle Sync to finish.
5. Start an emulator from Device Manager, or connect a physical Android device.
6. Select the `app` run configuration.
7. Click Run.

Expected first screen: login page. Tap the demo/student login action to enter the app.

## APK Path

Latest debug APK is generated here after `assembleDebug`:

```text
D:\DT\soprts\android\BNBUStudentAndroid\app\build\outputs\apk\debug\app-debug.apk
```

Install manually with Android Studio, or with adb:

```text
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Verification Commands

Run from:

```text
D:\DT\soprts\android\BNBUStudentAndroid
```

Use Android Studio's bundled JDK if `JAVA_HOME` is not already configured:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

Expected result: all three commands complete successfully.

## Main QA Flow

### Login

- Open app.
- Confirm BNBU branded login screen appears.
- Tap demo/student login.
- Confirm bottom navigation appears with five tabs:
  - 首页
  - 课程
  - 打卡
  - 成绩
  - 我的

### 首页

- Confirm sports credit progress is visible.
- Confirm course/general hour breakdown is visible.
- Confirm risk/action panel is visible.
- Tap quick actions:
  - 处理打卡 should open 打卡 tab.
  - 看成绩 should open 成绩 tab.
  - 看通知 should open 我的 tab.

### 课程

- Confirm course list shows `GEPE101 / Section 1004` and `GEPE101 / Section 1005`.
- Tap a course card.
- Confirm course detail page shows:
  - course name
  - teacher
  - deadline
  - course progress
  - class tasks
  - related records
- Tap back to return to course list.

### 打卡

- Confirm the three segments exist:
  - 任务
  - 提交
  - 记录
- In 任务, filter by 全部 / 课程相关 / 其他运动.
- Tap 去提交 on an active task.
- In 提交:
  - adjust hours with plus/minus buttons
  - enter note text
  - tap 选择照片/视频 and choose image/video files from the Android picker
  - alternatively tap 添加演示凭证
  - confirm validation reacts to missing or invalid proof
  - tap 保存草稿
  - tap 提交审核 and confirm submission
- In 记录:
  - confirm a new 待审核 record appears after submission
  - open a 需补材料 or 被驳回 item and submit supplement proof

### 成绩

- Confirm total score estimate is visible.
- Confirm four score components are visible:
  - 体育打卡
  - 专项考试
  - 平时表现 / 签到
  - 体测
- Confirm formula breakdown shows weighted contributions.
- Confirm missing/risk panel shows current missing items.
- Confirm source trace is visible.

### 我的

- Confirm student identity header is visible.
- Confirm organization offset cards show 校队/社团 status.
- Test notification filters:
  - 全部
  - 未读
  - 截止
  - 审核
- Tap a notification and mark it as read.
- Use 全部已读 and confirm unread badge decreases.
- Confirm sync panel shows local operations.
- Confirm settings panel can:
  - logout
  - reset local demo data
- Confirm debug panel shows:
  - active tasks
  - local records
  - pending records
  - supplement records
  - queued sync operations
  - workspace storage status
  - draft storage status
  - last write status

## Persistence QA

1. Go to 打卡.
2. Start a submission and save a draft.
3. Stop the app from Android Studio or close it in the emulator.
4. Run the app again.
5. Login again.
6. Go to 打卡 -> 提交.
7. Confirm draft panel appears and can be restored.

Repeat with notification read state:

1. Go to 我的.
2. Mark notifications as read.
3. Restart app.
4. Confirm read state persists.

Use 我的 -> 设置 -> 重置本地演示数据 to clear local state and return to the seeded mock workspace.

## Backend Boundary

The app currently reserves backend request specs under:

```text
app\src\main\java\edu\bnbu\student\mvp\core\network
```

Reserved student endpoints:

- `POST /auth/login`
- `GET /sport/summary`
- `POST /sport/records`
- `GET /sport/records/{id}`
- `POST /sport/records/{id}/supplements`
- `GET /sport/identity`
- `GET /common/notifications`
- `PUT /common/notifications/{id}/read`

The app UI still reads from `MockStudentRepository`. Real backend integration should replace repository internals without changing screen-level UI code.

## Known MVP Limits

- No real backend calls yet.
- No production authentication/token storage yet.
- Media picker stores metadata and URI source only; no multipart upload is implemented.
- Camera capture is not implemented yet.
- Release signing is not configured.
- ProGuard/R8 is not enabled for release yet.
- Accessibility and visual QA should still be done on multiple screen sizes before store submission.

## Handoff Criteria

The Android MVP can be treated as stage-ready when:

- Gradle unit tests pass.
- Debug APK builds.
- Five tabs are visually aligned with iOS MVP.
- Check-in local submit/supplement/draft flow works.
- Local persistence survives app restart.
- Native proof picker can attach image/video metadata.
- README and this QA handoff document are up to date.

For production launch, backend integration, auth, upload, security, release signing, privacy review, accessibility review, and device-matrix QA still need to be completed.
