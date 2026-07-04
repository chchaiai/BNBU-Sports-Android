# Android Week 1 Test Progress

Last updated: 2026-07-04

## Status Legend

- Passed: API/code path verified and no blocker found.
- Partial: main path works, but there is a field/UI/scope issue to fix or confirm.
- Failed: required function is missing or cannot pass with current Android/API surface.
- Pending: not tested yet in this round.

## 14 Core Modules

| No. | Module | Android Entry / Code Path | API / Server Path | Current Result | Status | Evidence / Issue |
|---:|---|---|---|---|---|---|
| 1 | 用户认证 | Login screen, `StudentApiClient`, `StudentLoginRequest` | `POST /api/auth/login` | Student login works with test account and returns token/user/defaultRoute. | Passed | Login verified with student account. |
| 2 | Dashboard / 首页 | Dashboard screen, workspace summary | `GET /api/sport/summary` | Summary loads course/general hours, pending count, teacher, course and rule data. | Passed | Health and summary API verified. |
| 3 | 组织管理 / 组织身份 | Profile/Dashboard membership display | `GET /api/sport/identity` | API returns team/club identity records for student. | Partial | UI labels have mojibake risk in membership type mapping. |
| 4 | 系统配置 | API base URL, cleartext config, server checks | Android base URL uses `http://123.207.5.70:96/api`; Tencent/Baota/LightCOS checked manually | Android points to current API; server and firewall are running. LightCOS bucket is visible in Tencent Cloud. | Partial | Server running, firewall 22/80/443/8888/96/100 open; LightCOS bucket confirmed: `bnbu-sports-1443273655`, `ap-guangzhou`, private read/write. |
| 5 | 通知系统 | Dashboard/Profile notices, mark-read action | `GET /api/common/notifications`, `PUT /api/common/notifications/{id}/read` | Notification list works; mark-read works; review/deadline/org categories returned. | Partial | Notification copy leaks backend enum `course`. |
| 6 | 课程管理 | Courses screen, course/task linkage | `GET /api/sport/summary`, `GET /api/student/tasks` | Course `GEPE101 / Section 1004` loads; tasks link to `courseId=gepe`. | Passed | Course and task relation verified. |
| 7 | 成绩管理 | Grades screen, `GradeRow` | `GET /api/student/grades` | API returns real scores. Android DTO/workspace mapping was fixed locally to use `checkin`/`overallTotal`; Android Studio manual UI check shows non-zero grade data. | Passed | Fixed locally; grade page screenshot captured. |
| 8 | 成绩归档 | Not found in Android source | Tried student archive paths plus teacher/admin delivery paths | No Android archive page/model/endpoint found. Teacher delivery GET returned 404; admin deliveries returned 404. | Failed | P1: grade archive not implemented/needs scope confirmation. |
| 9 | 免测申请 | Exemption screen | `GET /api/student/exemptions`, `POST /api/student/exemptions` | List works; submit works with another student account; duplicate submit returns 409 as expected. Android now tolerates nullable/missing `studentName`. | Partial | Fixed local nullable mapping; needs Android Studio manual UI check. |
| 10 | 审核流程 | Check-in records, dashboard counts, supplement entry | `GET /api/sport/records`, `POST /api/sport/records/{id}/supplements` | Student can see pending/approved/rejected/supplement/offset statuses and review feedback. Supplement endpoint exists but not mutated yet. | Passed | Record states and review notices verified. |
| 11 | 耐力跑换算 | Profile quick action, Endurance scoring screen | `POST /api/scoring/convert-endurance` | Page opens and API is reachable, but backend returns abnormal scores for slow times, e.g. male/freshman 1000m 13:00 returns 100. Android now guards out-of-range inputs locally. | Partial | Backend scoring rule/matching needs confirmation; Android local guard compiled. |
| 12 | 打卡签到 | Check-in screen, submit record path | `POST /api/sport/records` | New check-in submit was tested in Android Studio/emulator with image proof and video proof. App switched to records and showed submitted records as pending with proof counts. | Passed | Android manual path passed; backend review approval still requires teacher/admin action. |
| 13 | 文件上传 | Proof picker/upload flow | `POST /api/upload/proof` | Android picker opened, simulator image/video files were added, and submit created pending records with proof files. Android validation now matches max 6 photos/photo <= 8MB/max 1 video/video <= 100MB. | Partial | Android image/video upload UI passed, but earlier API response suggested server local `/uploads` storage rather than verified LightCOS object. Needs backend COS confirmation. |
| 14 | 数据导入导出 | Not an Android student UI path in current source | Teacher/admin Web API paths | Teacher export precheck/export returned 200; roster import preview returned 404; admin export-template and deliveries returned 404. | Partial | Export partly works on Web API; import/archive admin endpoints not implemented or not on current API. Needs scope confirmation. |

## Environment Progress

| Area | What Was Checked | Result | Status |
|---|---|---|---|
| GitHub | Branch `feat/kuan-android-week1-first-version` pushed to remote repo | Branch exists on GitHub and tracks origin | Passed |
| Android build | Gradle/JBR/SDK/local.properties | `compileDebugKotlin` and `assembleDebug` succeeded locally | Passed |
| Tencent Lighthouse | Instance status/IP/region | Server is running, IP is `123.207.5.70`, region is Guangzhou | Passed |
| Firewall | Ports 22, 80, 443, 8888, 96, 100, 3333, ICMP | Required current ports are open | Passed |
| Baota projects | PHP/Node project list | Existing 96/100 sites are running; `bnbu_api_v1` Node project is stopped and should not be touched without confirmation | Partial |
| Database | Existing DB/test accounts used through API | Login and feature data can be read from current DB through API | Partial |
| COS / LightCOS | Tutorial received; LightCOS bucket list checked in Tencent Cloud; upload API tested | Bucket `bnbu-sports-1443273655` is visible in Guangzhou (`ap-guangzhou`) with private read/write permission. Upload API currently returns `/uploads/...`, so backend-to-LightCOS storage is not yet proven. | Partial |

## Manual Android Studio QA

This section is for the user's real app walkthrough in Android Studio/emulator/phone. It is different from API checks: API checks prove the server/data path, while manual QA proves the Android UI, navigation, permissions, and real user flow.

| Step | Manual Action | Expected Result | Status |
|---:|---|---|---|
| M1 | Open the project in Android Studio and wait for Gradle sync | Project opens with no sync blocker | Passed |
| M2 | Run `app` on emulator or Android phone | App launches to the login screen | Passed |
| M3 | Log in with a student test account | User enters the main app/home page | Passed |
| M4 | Check Dashboard/Home | Summary, hours, pending items, and main cards display normally | Passed |
| M5 | Check Courses | Course and task information display normally | Passed |
| M6 | Check Check-in/Records | Existing records, audit states, and feedback display normally | Passed |
| M7 | Try check-in draft and proof picker | Image/video picker, camera/file permissions, and local validation work | Passed for photo/video picker: simulator image and generated video were selected through the Android picker and submitted successfully. Camera button now shows an emulator camera-unavailable fallback instead of leaving/crashing; true camera capture needs real-device verification. |
| M8 | Check Grades | Grade page displays server-backed data or confirms current known gap | Passed |
| M9 | Check Notifications/Profile/Organization | Profile data, notifications, and organization identity display normally | Passed: profile center, notifications, exemption entry, and endurance entry were manually checked in Android Studio/emulator. |
| M10 | Check Exemption and Endurance scoring | Exemption page and endurance conversion can be opened and used | Entry/page passed. Compact back action was unreliable near the top status-bar area; Android now clears focus, uses a 48dp click target, and applies status-bar safe padding to content while keeping the grid background full-screen. Rerun verification passed. Endurance slow-time scoring backend anomaly found; Android range guard fixed locally. |
| M11 | Capture screenshots/short recording for weekly report | Evidence is ready for Feishu/weekly meeting | Partial: check-in records and grades screenshots captured; endurance/COS evidence available from earlier screenshots; APK path recorded. |

## Weekly Report Material

- APK build proof: local debug APK generated successfully at `app/build/outputs/apk/debug/app-debug.apk`.
- API proof: health, login, summary, notifications, courses, grades, exemptions, records, endurance scoring, proof upload, and check-in submit all tested.
- Issue proof: `ANDROID_WEEK1_ISSUES.md` records P1/P2/P3 findings.
- Manual cloud proof: Tencent Lighthouse, firewall, and Baota project screenshots can be shown.
- LightCOS proof: bucket `bnbu-sports-1443273655` is visible in Tencent Cloud, but backend upload still needs COS confirmation because API currently returns `/uploads/...`.
- Manual Android proof: Android Studio/emulator screenshots or short recording should be added after manual QA.
- Next demo target: show the progress table, selected API results, manual app screenshots, and issue list.

## Next Recommended Steps

1. Run the Android app manually in Android Studio and complete Manual Android Studio QA steps M1-M11.
2. Confirm with backend/cloud owner whether `/api/upload/proof` should already upload to LightCOS or whether current `/uploads/...` response is temporary/local fallback.
3. Check Baota/backend upload environment without touching existing 96/100 production folders if backend owner approves.
4. Confirm grade archive and data import/export scope with product/backend owner because current Android source has no archive UI and several admin endpoints return 404.
5. Decide whether to push the local Android fixes after manual UI verification.
