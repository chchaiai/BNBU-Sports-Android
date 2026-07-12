# Android Week 1 Issues

Last updated: 2026-07-04

## P1 - Backend enum values are inconsistent

- Area: check-in records, notifications
- Evidence: `/api/sport/records` returns `creditType` values including `course`, `课程相关`, and `系统抵扣`.
- Impact: Android mapping currently expects Chinese labels in several places. English values such as `course` may fall through to the default type and display incorrectly.
- Suggested owner: backend / 陈昊确认字段规范
- Suggested fix: standardize API enum values, or add Android compatibility mapping for both English and Chinese values.
- Status: open

## P1 - Numeric fields are returned as strings

- Area: tasks, records
- Evidence: `/api/student/tasks` returns `requiredHours` as `"2.0"`; `/api/sport/records` returns `hours` and `approvedHours` as strings.
- Impact: Android DTOs define these fields as `Double`. Gson may parse numeric strings, but strict clients or future serializers may fail.
- Suggested owner: backend / 陈昊确认字段规范
- Suggested fix: return JSON numbers instead of strings, or keep Android compatibility parsing.
- Status: open

## P2 - Notification copy leaks backend enum value

- Area: notifications
- Evidence: `/api/common/notifications` returned message text like `course 0.5h 已提交，等待老师审核`.
- Impact: User-facing copy is not polished and exposes internal enum wording.
- Suggested owner: backend / notification template owner
- Suggested fix: render `course` as a Chinese user-facing label such as `课程相关`.
- Status: open

## P1 - Android proof upload rules do not match product requirement

- Area: check-in proof upload
- Evidence: Product requirement says max 6 photos, each photo max 8MB, max 1 video, video max 100MB. Android previously defined `ProofUploadRule.maxAttachmentCount = 8`, `maxImageBytes = 10_000_000`, `maxVideoBytes = 80_000_000`, and added a 30-second video duration limit. This has now been updated locally to max 6 images, max 1 video, image <= 8MB, video <= 100MB.
- Impact: Android local validation now matches the agreed product rule. Android manual QA can select image/video files and submit pending check-in records with proof files, but real LightCOS storage still depends on backend configuration.
- Suggested owner: Android / product confirmation
- Suggested fix: confirm backend COS storage behavior after Android UI upload validation.
- Status: fixed locally; Android image/video UI upload and submit passed; backend LightCOS confirmation still pending

## P2 - Camera proof capture is unstable on emulator

- Area: check-in proof upload
- Evidence: Manual Android Studio QA found that tapping the camera upload button returned to the emulator home screen. Code review found the camera callback created `proof_*.jpg` but later looked up a different `camera_*.jpg`, so captured-photo attachment creation could fail or behave inconsistently. After the local fix and rerun, the app stayed on the submit page and displayed a camera-unavailable fallback message on the emulator.
- Impact: Gallery/photo picker upload works. Camera capture is now guarded in Android, but cannot be marked fully passed on this emulator because no usable camera app/device is available.
- Suggested owner: Android
- Suggested fix: store the real temporary camera file alongside its FileProvider URI and build the proof attachment from that same file; show a fallback message if the emulator cannot launch a camera.
- Status: fixed locally and rerun verified on emulator fallback path; true camera capture still needs real-device verification

## P1 - LightCOS bucket confirmed; backend upload still needs verification

- Area: COS / file upload environment
- Evidence: LightCOS bucket list is visible in Tencent Cloud. The project bucket is `bnbu-sports-1443273655`, region `ap-guangzhou`, permission `private read/write`. `POST /api/upload/proof` returned 200 for a small PNG, but the response was `/uploads/...`, and the file was directly readable from `123.207.5.70:96/uploads/...`.
- Impact: The previous bucket-visibility blocker is resolved, and the upload endpoint can receive files. COS-backed upload still cannot be marked as passed because the observed response suggests server-local upload storage rather than verified LightCOS storage.
- Suggested owner: backend / Android QA
- Suggested fix: confirm whether `/api/upload/proof` is expected to return local `/uploads/...` paths or COS keys/signed URLs; verify backend COS environment variables before weekly demo.
- Status: bucket confirmed; API upload passed; backend-to-LightCOS storage unproven

## P2 - Endurance scoring API works but Android scoring UI has mojibake labels

- Area: endurance scoring
- Evidence: `POST /api/scoring/convert-endurance` succeeded for both female/sophomore 800m-style input and male/freshman 1000m-style input, returning `score`, `tier`, `timeSeconds`, `gender`, `gradeLevel`, and `gradeGroup`. Android maps `tier=pass` to mojibake text in `EnduranceScoreResult.tierLabel`, and the scoring screen contains mojibake labels/errors.
- Impact: The endurance conversion integration is reachable, but the Android UI may show garbled Chinese labels for score tier, input time, and error messages.
- Suggested owner: Android
- Suggested fix: replace mojibake literals with valid UTF-8 Chinese labels, then rerun UI/manual test in Android Studio.
- Status: open

## P1 - Endurance scoring backend returns abnormal score for slow times

- Area: endurance scoring
- Evidence: Manual QA found that entering 13 minutes for male 1000m returned 100 points. Direct API checks confirmed backend responses: male/freshman 1000m 180s -> 100, 300s -> 40, 420s -> 100, 780s -> 100.
- Impact: Endurance scoring cannot be marked fully correct because the backend rule matching appears non-monotonic or falls back to an excellent score outside the valid range.
- Suggested owner: backend / conversion rule owner
- Suggested fix: inspect `endurance_scoring_rules` / `conversion_rules_admin` matching logic and define the valid slow-time boundary. Android now blocks obviously out-of-range inputs before showing a misleading 100.
- Status: backend issue open; Android out-of-range guard fixed locally

## P1 - Exemption API returns nullable studentName but Android expects non-null

- Area: exemption applications
- Evidence: `GET /api/student/exemptions` returned records where `studentName` is missing or `null`. Android maps `ExemptionResponse.studentName` directly into a non-null `Exemption.studentName`.
- Impact: The exemption list/submit API is reachable. Android previously risked unstable parsing/display when `studentName` was missing.
- Suggested owner: backend / Android
- Suggested fix: backend should return `studentName` consistently; Android now makes the field nullable and maps null to an empty string.
- Status: fixed locally; pending Android UI manual check

## P1 - Grade archive function is not implemented in Android/API surface

- Area: grade archive
- Evidence: Android source has no grade archive page, model, repository method, or endpoint. Common student grade archive paths such as `/api/student/grades/archive`, `/api/student/grades/archives`, `/api/student/grade-archives`, `/api/grades/archive`, and `/api/grades/archives` all returned 404. Teacher delivery GET and admin deliveries GET also returned 404 in the current online API.
- Impact: The required "grade archive" function cannot be marked as passed on Android unless it is confirmed as out of scope or backend provides the final endpoint.
- Suggested owner: product/backend/Android confirmation
- Suggested fix: confirm expected Android grade archive behavior and final API path. If in scope this week, add endpoint model, repository method, UI section, and manual test case.
- Status: open

## P1 - Student grades API is not wired into the displayed grade page

- Area: grades
- Evidence: `/api/student/grades` returns `grades[0].checkin` and `grades[0].overallTotal`, while Android originally expected `checkinScore` and `total`. `ApiStudentRepository.buildWorkspace` originally filled `GradeRow` with zero placeholder values.
- Impact: The grades API is reachable. Android would have displayed zero/stale placeholder scores before the local fix.
- Suggested owner: Android / backend field confirmation with Chen Hao
- Suggested fix: keep Android compatibility for both field-name sets, and ask backend whether the final contract should be `checkin/overallTotal` or `checkinScore/total`.
- Status: fixed locally; compile passed; Android grade page manual check passed

## P1 - Data import/export is only partially available on the current online API

- Area: teacher/admin data import/export
- Evidence: Teacher export precheck and export returned 200 on `/api/teacher/courses/gepe/export/precheck` and `/api/teacher/courses/gepe/export`. Roster import preview returned 404 `Endpoint not implemented`; admin export-template and admin deliveries also returned 404.
- Impact: The required "data import/export" module cannot be marked fully passed. Export is partly reachable on teacher Web API, but import and some admin configuration/archive endpoints are missing on the current online API.
- Suggested owner: backend / web / product confirmation
- Suggested fix: confirm whether Android is responsible for any import/export behavior. If not, mark this as Web/admin scope and ask backend owner for final API status.
- Status: partial; needs scope confirmation

## P2 - Android source contains mojibake Chinese UI labels

- Area: organization identity, notifications, source comments/default labels
- Evidence: `/api/sport/identity` returns valid `type` values such as `team` and `club`, but Android source currently maps them to mojibake strings in `Membership.typeTitle`; notification category mappings also contain mojibake string literals.
- Impact: The organization identity API is usable, but some Chinese labels may display as garbled text in Android UI.
- Suggested owner: Android
- Suggested fix: replace mojibake literals with valid UTF-8 Chinese labels and rerun the app UI check.
- Status: open

## P2 - Tool subpage back navigation did not respond reliably near status bar

- Area: profile tools / navigation
- Evidence: Manual Android Studio QA found that the Endurance Scoring and Exemption pages opened normally, but returning to the Profile page did not respond reliably. The small back action worked when a temporary full-width button pushed it lower on the screen, while the full-width button at the very top did not respond, indicating a top safe-area/touch-zone issue.
- Impact: Users could get stuck on a tool subpage during normal use.
- Suggested owner: Android
- Suggested fix: add Compose `BackHandler` for profile-launched subpages, clear text-field focus before returning, keep a compact 48dp back click target, and apply status-bar safe padding to tool subpages.
- Status: fixed locally and verified in Android Studio/emulator after safe-area diagnosis

## P3 - Intermittent 502 during API checks

- Area: server/API stability
- Evidence: During course/task verification, `/api/health` returned 200, but the login + task check chain returned `502 Bad Gateway` twice before succeeding on retry.
- Impact: Android functionality may appear flaky during manual testing even when endpoint contracts are correct.
- Suggested owner: backend/server
- Suggested fix: monitor Baota/process logs and retry the affected checks before marking the function failed.
- Status: monitoring

## P2 - Project docs lag behind current source state

- Area: README / handoff docs
- Evidence: README and handoff notes still say backend integration is not implemented, while current source includes `ApiStudentRepository`, real login, endpoint calls, and upload flow.
- Impact: New developers may misunderstand the current project status.
- Suggested owner: Android
- Suggested fix: update README and handoff docs after first round of API verification.
- Status: open
