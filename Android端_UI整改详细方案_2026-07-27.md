# BNBU Sports Android 端 UI 整改详细方案

> v4.9 更新：删除暂停 6 小时超时自动结束机制。暂停后的运动会话应保持可恢复，不设自动结束时限。

> 基线：`总业务流程审2.md` v4.5/v4.6
> 日期：2026-07-27
> 原则：每个任务独立可执行，标注依赖和预估影响范围

---

## 一、认证体系改造（v4.6 全新方案）

### 任务 A1：LoginScreen — 替换为三种登录方式

**文档依据**：v4.6 §登录页与课堂流程

> 登录页提供：`[邮箱验证码登录]`、`[手机验证码登录]`、`[扫码加入课程]`

**当前文件**：[LoginScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/login/LoginScreen.kt)

**当前状态**：学号/邮箱 + 密码 两个输入框 + 登录按钮，底部"登录前请阅读《隐私政策》"

**目标状态**：登录页三个入口按钮 + 隐私政策链接

**具体修改步骤**：

1. **删除旧的密码输入框 UI**。保留文件中最外层的 `GridBackground`、`UniversityBrandLockup` 和布局结构，删除 `SwissPanel` 内部的账号密码输入区域。
2. **新增三个登录方式选择按钮**，替换原有的表单区域：

```kotlin
// 三个大按钮，纵向排列
// [邮箱验证码登录]  — 图标: Email
// [手机验证码登录]  — 图标: Smartphone
// [扫码加入课程]    — 图标: QR Code Scanner
```

3. **新增回调**：LoginScreen 的签名从 `onLogin: (account, password) -> Unit` 改为三个独立回调：

```kotlin
fun LoginScreen(
    onEmailLogin: () -> Unit,
    onPhoneLogin: () -> Unit,
    onScanJoin: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
)
```

4. **修改 AppRootScreen.kt 接入点**：在 `AppRootScreen` 中，`AuthUiState.Login` 分支调用新的 `LoginScreen`，传入三个回调来切换到对应的子界面。
5. **需要新增的子页面**（后续任务详细描述）：

   - `EmailLoginScreen` — 邮箱验证码登录
   - `PhoneLoginScreen` — 手机验证码登录
   - `ScanJoinScreen` — 扫码加入课程

**影响文件**：

- 修改：`feature/login/LoginScreen.kt`
- 修改：`feature/shell/AppRootScreen.kt`（AuthUiState.Login 分支）

**依赖**：无（纯 UI 改造，三个子页面后续任务完成）

---

### 任务 A2：EmailLoginScreen — 邮箱验证码登录

**文档依据**：v4.6 §联系方式绑定与验证码登录

> 邮箱验证码登录；验证码有效期 10 分钟，只能使用一次；60 秒内最多发送一次

**需要新建文件**：`feature/login/EmailLoginScreen.kt`

**具体实现**：

1. **输入框**：邮箱地址（OutlinedTextField，KeyboardType.Email）
2. **验证码输入**：6 位数字输入框（OutlinedTextField，KeyboardType.Number）+ 右侧"获取验证码"按钮
3. **发送按钮行为**：
   - 点击后调用 `POST /api/v1/auth/login/email` 发送验证码
   - 按钮文案变为倒计时（60 秒～0 秒）
   - 倒计时期间按钮 disabled
   - 同一邮箱 60 秒内只能发送一次
4. **提交登录**：输入完整验证码后点击"登录"，调用接口
5. **错误提示**：验证码错误、过期、发送频率限制的提示文案
6. **返回按钮**：顶部"← 返回"回到 LoginScreen

**回调签名**：

```kotlin
fun EmailLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
)
```

**新增模型**（如需）：

```kotlin
// 在 StudentApiPayloads.kt 新增
data class EmailLoginRequest(val email: String, val code: String)
data class SendEmailCodeRequest(val email: String)
```

**依赖**：后端接口 `POST /api/v1/auth/login/email`（受接口阻塞）

---

### 任务 A3：PhoneLoginScreen — 手机验证码登录

**文档依据**：v4.6 §联系方式绑定与验证码登录

**需要新建文件**：`feature/login/PhoneLoginScreen.kt`

**具体实现**：与 EmailLoginScreen 结构完全一致，区别仅在于：

- 输入框为手机号（KeyboardType.Phone）
- 接口为 `POST /api/v1/auth/login/phone`
- 文案中"邮箱"改为"手机号"

**依赖**：后端接口（受接口阻塞）

---

### 任务 A4：ContactBindingScreen — 首次绑定联系方式

**文档依据**：v4.6 §联系方式绑定与验证码登录

> 首次进入主界面显示"请绑定手机号和邮箱，用于退出登录后重新进入账号"
> 学生分别完成短信验证码和邮箱验证码验证

**需要新建文件**：`feature/login/ContactBindingScreen.kt`

**具体实现**：

1. **页面标题**：`"绑定联系方式"`
2. **说明文案**：`"请绑定手机号和邮箱，用于退出登录后重新进入账号"`
3. **邮箱绑定区**：
   - 邮箱输入框 + "发送验证码"按钮 + 验证码输入框 + "验证"按钮
   - 接口：`POST /api/v1/student/contacts/email/send-code` + `/verify`
4. **手机号绑定区**：
   - 手机号输入框 + "发送验证码"按钮 + 验证码输入框 + "验证"按钮
   - 接口：`POST /api/v1/student/contacts/phone/send-code` + `/verify`
5. **两个都验证通过后才能进入主界面**
6. **账号状态**：绑定完成后 `account_status` 从 `PENDING_CONTACT_BINDING` 变为 `ACTIVE`
7. **不可跳过**：不绑定不能进入，但可以返回上一页（实际会回到登录页）
8. **返回按钮无实际用途**（首次登录必须绑定），可隐藏

**回调签名**：

```kotlin
fun ContactBindingScreen(
    onBindingComplete: () -> Unit,
)
```

**状态检查位置**：`AppRootScreen` 中，在 `AuthenticatedAppContent` 渲染之前检查 `account_status`

```
if isAuthenticated && account_status == PENDING_CONTACT_BINDING → 展示 ContactBindingScreen
if isAuthenticated && account_status == ACTIVE → 展示 AuthenticatedAppContent
```

**依赖**：后端接口（受接口阻塞）、`StudentWorkspace` 或 `StudentProfile` 中新增 `accountStatus` 字段

---

### 任务 A5：RecoveryRequestScreen — 换手机恢复申请

**文档依据**：v4.6 §换手机与人工恢复

> 如果手机号和邮箱都无法使用，必须提交恢复申请，由老师或管理员核对本人身份后绑定新的联系方式

**需要新建文件**：`feature/login/RecoveryRequestScreen.kt`

**具体实现**：

1. **页面入口**：在 LoginScreen 底部文字链接"无法使用绑定的手机号或邮箱？"
2. **表单**：
   - 学号（必填）
   - 姓名（必填）
   - 说明（必填，<=500 字）
   - 新手机号（选填）
   - 新邮箱（选填）
3. **提交按钮**：调用 `POST /api/v1/student/recovery-requests`
4. **提交后展示**：`"恢复申请已提交，请等待老师或管理员联系你"`
5. **可查看状态**：后续在 ProfileScreen 或通知中查看处理结果

**依赖**：后端接口（受接口阻塞）

---

### 任务 A6：LogoutConfirmDialog — 退出登录二次确认

**文档依据**：v4.6 §账号与设备分离

> 退出登录只撤销设备会话，不删除学生身份、课程关系、签到、学时或成绩。

**修改文件**：[ProfileScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/profile/ProfileScreen.kt)

**具体实现**：

1. 在退出登录按钮点击时，弹出 `AlertDialog`：
   ```
   标题：退出登录
   文案：退出登录只会撤销当前设备的登录会话，不会删除你的学生身份、课程关系、签到、学时和成绩。
   按钮：「取消」和「确认退出」
   ```
2. 只有点击「确认退出」后才调用 `appState.logout()`

**影响**：仅 ProfileScreen 中退出按钮的 onClick 行为，约 15 行改动

---

## 二、课程加入申请 UI — 全新模块

### 任务 B1：CourseJoinEntry — 首页/课程页加入入口

**文档依据**：§7.4 — 没有当前课程时，首页显示「扫码加入课程」和「输入邀请码」

**修改文件**：

- [DashboardScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/dashboard/DashboardScreen.kt)
- [CoursesScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/courses/CoursesScreen.kt)

**具体实现**：

1. **DashboardScreen 新增条件渲染**：在 `ProgressPanel` 和 `RiskPanel` 之间插入：

```kotlin
if (appState.workspace.courses.none { it.isCurrent && it.enrollmentStatus == "enrolled" }) {
    item { CourseJoinEntryPanel(onScanJoin, onEnterCode) }
}
```

2. **CourseJoinEntryPanel Composable**：

```kotlin
@Composable
fun CourseJoinEntryPanel(onScanJoin: () -> Unit, onEnterCode: () -> Unit) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("加入体育课程", style = MaterialTheme.typography.titleMedium)
            Text("扫码或输入邀请码加入本学期体育课", ...)
            PrimaryActionButton(title = "扫码加入课程", icon = QR icon, onClick = onScanJoin)
            ActionButton(title = "输入邀请码", icon = TextInput icon, onClick = onEnterCode)
        }
    }
}
```

3. **CoursesScreen 同理**：在空课程列表时展示
4. **回调链**：

   - `DashboardScreen` ← `AppRootScreen` ← `AppTab` 或 subScreen
   - `AppRootScreen` 新增 `SubScreen.ScanJoin` 和 `SubScreen.EnterCode` 状态

**依赖**：无（纯 UI 入口）

---

### 任务 B2：ScanJoinScreen — 扫码加入课程

**文档依据**：§7.4, §7.5 — 学生扫码后显示课程、班级、教师、学期

**需要新建文件**：`feature/courses/ScanJoinScreen.kt`

**具体实现**：

1. **二维码扫描**：使用 CameraX + ML Kit Barcode Scanning 或 ZXing 库

   - 引入依赖：`implementation("com.journeyapps:zxing-android-embedded:4.3.0")`
   - 扫描区域 + 提示文字："将二维码放入框内"
2. **手动输入备选**：在扫描界面底部提供"手动输入邀请码"按钮
3. **扫描结果处理**：解析二维码中的 URL（格式 `https://sports.example.com/join/BNBU-7K3P9Q`）

   - 提取邀请码（URL 最后一段）
   - 调用 `GET /api/v1/course-invites/{code}` 获取课程公开信息
   - 跳转到 B3（课程确认页）
4. **异常处理**：

   - 无法识别的二维码 → 提示"无效的课程二维码，请确认后重试"
   - 邀请码过期 → 提示"该邀请已过期，请联系教师获取新二维码"
   - 网络错误 → 提示重试
5. **权限处理**：首次请求相机权限（已在 Manifest 中声明）

**依赖**：后端接口 `GET /api/v1/course-invites/{code}`（受接口阻塞）

---

### 任务 B3：CourseJoinConfirmScreen — 课程信息确认页

**文档依据**：§7.5 — 系统先显示课程名称、课程编号、Section/班级、授课老师和学期。学生确认课程无误后填写姓名、学号、邮箱

**需要新建文件**：`feature/courses/CourseJoinConfirmScreen.kt`

**具体实现**：

1. **上半部分 — 课程信息展示**（SwissPanel）：

   - 课程名称："篮球基础 B 班"
   - 课程编号 + Section："GEPE101 / Section 1004"
   - 授课老师："张老师"
   - 学期："2026 Spring"
   - 提示文字："请确认以上课程信息无误后再提交申请"
2. **下半部分 — 身份资料填写**（SwissPanel）：

   - 姓名：[________] （必填，<=64 字）
   - 学号：[________] （必填，<=32 字）
   - 邮箱：[________] （选填，KeyboardType.Email）
3. **底部按钮**：

   - 「提交加入申请」— 调用 `POST /api/v1/course-invites/{code}/join-request`
   - 请求体：`{ name, studentNumber, email }`
4. **提交后**：

   - 成功：跳转到 B4（PendingStatusScreen），显示"申请已提交，请等待老师确认。"
   - 失败：展示具体错误（邀请过期、学号冲突等）

**依赖**：后端接口（受接口阻塞）

---

### 任务 B4：JoinRequestStatusScreen — 申请状态查看页

**文档依据**：§7.2, §10.6 — PENDING / NEEDS_CORRECTION / REJECTED / ACTIVE 四种状态各自对应不同 UI

**需要新建文件**：`feature/courses/JoinRequestStatusScreen.kt`

**具体实现**：

1. **统一入口**：从 Dashboard 或 Courses 中的"加入申请"区域进入
2. **PENDING 状态展示**：

```
┌──────────────────────────────┐
│ 申请状态：待教师审核           │
│                              │
│ 课程：GEPE101 / Section 1004 │
│ 班级：篮球基础 B 班           │
│ 教师：张老师                  │
│ 学期：2026 Spring             │
│ 提交时间：2026-02-24 10:30    │
│                              │
│ [联系教师]                    │
└──────────────────────────────┘
```

- 无打卡入口
- 无撤销/取消按钮（由教师控制）

3. **NEEDS_CORRECTION 状态展示**：

```
┌──────────────────────────────┐
│ 申请状态：需补正资料           │
│                              │
│ 教师原因：学号与花名册不一致，  │
│ 请核对后重新提交               │
│                              │
│ [修改并重新提交] → 回到B3表单  │
└──────────────────────────────┘
```

- 点击 → 回到 CourseJoinConfirmScreen，预填之前的信息，允许修改后重新提交

4. **REJECTED 状态展示**：

```
┌──────────────────────────────┐
│ 申请状态：已拒绝               │
│                              │
│ 拒绝原因：该班级已满，请选择    │
│ 其他 Section                  │
│                              │
│ [联系教师]                    │
│ [使用新邀请码重新申请]         │
└──────────────────────────────┘
```

- "使用新邀请码重新申请" → 回到扫码/输入邀请码页面

5. **ACTIVE 状态（已通过）**：

   - 不在此页面展示
   - 直接进入主界面，课程出现在 Dashboard / Courses 中
6. **邀请码过期/已撤销**（从查询接口直接返回错误时展示）：

```
┌──────────────────────────────┐
│ 该邀请已过期或已被教师撤销     │
│                              │
│ 请联系教师获取新的二维码或     │
│ 邀请码                        │
│                              │
│ [返回]                        │
└──────────────────────────────┘
```

**依赖**：

- 后端接口查询申请状态（受接口阻塞）
- `StudentWorkspace` 中需新增 `courseJoinRequest` 字段

---

### 任务 B5：StudentModels + StudentAppState — 课程加入相关模型

**修改文件**：

- [StudentModels.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/core/model/StudentModels.kt)
- [StudentAppState.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/core/state/StudentAppState.kt)

**具体实现**：

1. **新增模型**（StudentModels.kt）：

```kotlin
data class CourseJoinRequest(
    val id: String,
    val inviteCode: String,
    val courseName: String,
    val courseCode: String,
    val section: String,
    val teacherName: String,
    val semester: String,
    val studentName: String,
    val studentNumber: String,
    val email: String,
    val status: JoinRequestStatus,
    val reviewComment: String,    // 教师审核意见
    val submittedAt: String,
    val reviewedAt: String?
)

enum class JoinRequestStatus(val label: String) {
    PENDING("待审核"),
    ACTIVE("已通过"),
    REJECTED("已拒绝"),
    NEEDS_CORRECTION("需补正")
}
```

2. **StudentWorkspace 新增字段**：

```kotlin
val courseJoinRequest: CourseJoinRequest? = null
```

3. **StudentAppState 新增计算属性**：

```kotlin
val hasActiveEnrollment: Boolean
    get() = workspace.courses.any { it.isCurrent && it.enrollmentStatus == "enrolled" }

val hasPendingJoinRequest: Boolean
    get() = workspace.courseJoinRequest != null && 
            workspace.courseJoinRequest?.status != JoinRequestStatus.ACTIVE
```

**依赖**：无（仅新增模型）

---

## 三、首页（Dashboard）整改

### 任务 C1：DashboardScreen — 删除旧任务模块

**修改文件**：[DashboardScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/dashboard/DashboardScreen.kt)

**具体实现**：

1. **删除 `NextTasks` Composable** 及其调用
2. **删除 `TaskRow` Composable**
3. **删除 `FocusPlan` 中的旧文案**：
   - "课程相关不能被组织抵扣替代；当前暂无可提交任务，请等待老师发布。" → "课程相关还需 Xh，可通过课程相关运动打卡完成。"
   - "关注下一次课程任务发布" → "继续保持运动记录，关注课程通知。"
4. **删除 `activeTasks` 计算属性的使用**
5. **修改 `FocusPlan` 中的逻辑**：不依赖 `CourseTask` / `activeTasks`，直接根据学时缺口计算

**影响**：约删除 60 行，修改 20 行

---

### 任务 C2：DashboardScreen — 学时拆分展示（含组织抵扣）

**修改文件**：[DashboardScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/dashboard/DashboardScreen.kt)

**当前展示**：

```
课程相关   10.0h / 10.0h  ✅
其他运动    5.0h / 10.0h  ⚠️
```

**目标展示**：

```
课程相关运动
  已打卡           8.0h
  组织抵扣         2.0h
  合计            10.0h / 10.0h ✅

自主其他运动
  已打卡           4.0h
  组织抵扣         1.0h
  合计             5.0h / 10.0h ⚠️

本学期总完成  15.0h / 20.0h   75%
```

**具体实现**：

1. **修改 ProgressPanel 中课程相关的 ProgressLine**：

```kotlin
// 课程相关 — 增加抵扣拆分
Column {
    Row { Text("课程相关运动") ... StatusBadge("还差 Xh") }
    HourProgressBar(value = courseCompleted, total = courseRequired)
    // 新增：拆分展示
    Row {
        Text("已打卡 ${courseRawCompleted}h", style = bodySmall)
        if (courseOffsetHours > 0) {
            Text(" · 组织抵扣 ${courseOffsetHours}h", style = bodySmall)
        }
    }
}
```

2. **同样的逻辑应用于其他运动**
3. **依赖**：`StudentProgress` 模型中需要新增以下字段（或通过 computed 计算）：

```kotlin
// 现有：
val course: Double    // 课程运动合计（含抵扣）
val general: Double   // 其他运动合计（含抵扣）
val rawGeneral: Double // 其他运动原始打卡（不含抵扣）

// 需要新增：
val rawCourse: Double  // 课程运动原始打卡（不含抵扣）
```

**依赖**：后端返回数据包含抵扣拆分（受接口阻塞）

---

## 四、打卡页（Check-in）整改

### 任务 D1：ExerciseCheckInScreen — 准备页增加课程信息展示

**修改文件**：[ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)

**具体实现**：

1. 在 `ExercisePreparationContent` 的 `SectionTitle("开始运动")` 之前，新增一个 SwissPanel：

```kotlin
// 课程信息摘要（仅当课程相关运动时展示更详细）
if (appState.workspace.courses.any { it.isCurrent }) {
    val currentCourse = appState.workspace.courses.first { it.isCurrent }
    item {
        SwissPanel {
            Text("当前课程", style = labelSmall)
            Text("${currentCourse.displayTitle} · ${currentCourse.name}", style = titleMedium)
            Text("任课教师：${currentCourse.teacher}", style = bodyMedium)
        }
    }
}

// 学时进度摘要
item {
    SwissPanel {
        Row {
            Column(weight=1f) {
                Text("课程相关", style = labelSmall)
                Text("${courseCompleted}h / ${courseRequired}h", style = titleMedium)
            }
            Column(weight=1f) {
                Text("其他运动", style = labelSmall)
                Text("${generalCompleted}h / ${generalRequired}h", style = titleMedium)
            }
            Column(weight=1f) {
                Text("总学时", style = labelSmall)
                Text("${totalCompleted}h / ${totalRequired}h", style = titleMedium)
            }
        }
    }
}
```

2. **今日打卡状态提示**：

```kotlin
// 在"开始运动"按钮之前
if (hasSubmittedToday) {
    item {
        ValidationPanel(message = "今日已打卡（${todayRecordHours}h），每日限提交一次")
    }
}
```

3. **需要从 `appState` 获取的数据**：
   - `appState.workspace.courses` — 当前课程
   - `appState.workspace.progress` — 学时进度
   - `appState.hasSubmittedCheckInToday()` — 已经存在的方法

**依赖**：无（使用现有 appState 数据即可实现 UI 框架）

---

### 任务 D2：ExerciseCheckInScreen — 打卡时间窗展示与校验

**修改文件**：[ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)

**具体实现**：

1. **在 `StudentAppState` 或数据模型中新增时间窗字段**：

```kotlin
// StudentModels.kt 新增
data class CheckInTimeWindow(
    val windowMode: String,          // "semester_wide" or "specified_range"
    val dateRangeStart: String?,     // "2026-02-24"
    val dateRangeEnd: String?,       // "2026-06-28"
    val dailyStartTime: String,      // "06:00"
    val dailyEndTime: String,        // "22:00"
    val excludedDates: List<String>, // ["2026-04-20","2026-04-21",...]
    val semesterDeadline: String?    // "2026-06-28"
)
```

2. **准备页展示时间窗信息**：

```kotlin
item {
    SwissPanel {
        Row(verticalAlignment = CenterVertically) {
            Icon(Clock icon, ...)
            Spacer(8.dp)
            Column {
                Text("打卡时间：每天 ${dailyStartTime} - ${dailyEndTime}", style = bodyMedium)
                // 如果有限定日期范围
                if (windowMode == "specified_range") {
                    Text("开放日期：${dateRangeStart} 至 ${dateRangeEnd}", style = bodySmall)
                }
                // 排除日期
                if (excludedDates.isNotEmpty()) {
                    Text("最近排除日：${formatExcludedDates(excludedDates)}", style = bodySmall)
                }
            }
        }
    }
}
```

3. **开始运动校验**：

```kotlin
// 在"开始运动"按钮 onClick 中增加检查：
fun canStartExercise(): String? {
    val now = LocalTime.now(ZoneId.of("Asia/Shanghai"))
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
  
    // 检查每日时段
    val start = LocalTime.parse(dailyStartTime)
    val end = LocalTime.parse(dailyEndTime)
    if (now < start || now > end) {
        return "当前不在可运动时段（${dailyStartTime} - ${dailyEndTime}）"
    }
    // 检查排除日期
    if (today.toString() in excludedDates) {
        return "今日为特殊排除日，不可开始运动"
    }
    // 检查截止日期
    ...
    return null // 可以开始
}
```

4. **不满足条件时**：禁用"开始运动"按钮，按钮下方以 `ValidationPanel` 展示原因

**依赖**：后端返回时间窗数据（受接口阻塞，但UI框架可以先用本地默认值 `06:00-22:00`）

---

### 任务 D3：健康安全提醒弹窗

**修改文件**：[ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)

**修改文件**：[AndroidAppLocalStore.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/core/local/AndroidAppLocalStore.kt)

**具体实现**：

1. **在 `AndroidAppLocalStore` 中新增**：

```kotlin
// 存储策略：每个账号单独记录（key 格式：health_reminder_shown_{accountId}）
fun hasShownHealthReminder(accountId: String): Boolean
fun markHealthReminderShown(accountId: String)
```

2. **在 `ExerciseSessionController` 中新增状态**：

```kotlin
// ExerciseSessionController 中新增
val shouldShowHealthReminder: Boolean  // 从 localStore 读取
fun dismissHealthReminder()            // 写入 localStore
```

3. **在 `ExerciseFlowContent` 中新增**（与现有 message 的 AlertDialog 类似）：

```kotlin
if (controller.shouldShowHealthReminder) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = {
                controller.dismissHealthReminder()
            }) { Text("我知道了") }
        },
        title = { Text("健康安全提醒") },
        text = { Text("请根据自身身体状况适量运动。如感不适应立即停止，必要时及时就医。") }
    )
}
```

**影响范围**：ExerciseCheckInScreen 约 +25 行，AndroidAppLocalStore 约 +15 行，ExerciseSessionController 约 +10 行

**依赖**：无

---

### 任务 D4：前置条件检查 UI 反馈

**文档依据**：§4 — 7 项前置条件，任一不满足时页面直接说明原因

**修改文件**：[ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)

**具体实现**：

1. **在 `ExerciseSessionController` 或直接在使用处新增校验函数**：

```kotlin
data class CheckInReadiness(
    val canStart: Boolean,
    val blockedReason: String? = null
)

fun evaluateCheckInReadiness(appState: StudentAppState): CheckInReadiness {
    // 1. 账号状态
    if (appState.workspace.student.status != "ACTIVE") {
        return CheckInReadiness(false, "账号状态异常，无法打卡")
    }
    // 2. ACTIVE enrollment
    if (!appState.hasActiveEnrollment) {
        return CheckInReadiness(false, "你尚未加入本学期体育课程，请先扫码或输入邀请码加入")
    }
    // 3. 课程开放状态
    // 4. 学期日期范围
    // 5. 每日时段
    // 6. 今日已打卡
    if (appState.hasSubmittedCheckInToday()) {
        return CheckInReadiness(false, "今日已打卡，每天只能提交一次")
    }
    // 7. 跨设备会话
    // ...
    return CheckInReadiness(true)
}
```

2. **UI 展示**：在准备页顶部，如果不满足条件：

```kotlin
val readiness = remember(appState) { evaluateCheckInReadiness(appState) }
if (!readiness.canStart) {
    item {
        SwissPanel(
            modifier = Modifier.background(/* 浅黄色警告背景 */)
        ) {
            Row {
                Icon(Warning, tint = secondary)
                Spacer(8.dp)
                Text(readiness.blockedReason!!, style = bodyMedium)
            }
        }
    }
}
```

   同时在按钮上禁用：`enabled = readiness.canStart`

3. **特殊处理**：无课程时，显示 B1（加入入口）而不是"不满足条件"

**依赖**：部分校验需要后端数据（账号状态、时间窗、跨设备会话），但 UI 框架可以先搭建，用默认值

---

### 任务 D5：ExerciseCheckInScreen — 完成页摘要字段补齐

**文档依据**：§5.8 — 提交前看到：打卡类别、运动项目、开始和结束时间、计入学时、打卡日期、课程归属、凭证数量、内容真实性确认

**修改文件**：[ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)

**具体实现**：

在 `ExerciseFinishedContent` 的 `LazyColumn` 中，在"选择打卡凭证" section 之后、"确认凭证"按钮之前，新增一个 **提交确认摘要** SwissPanel：

```kotlin
item {
    SectionTitle(eyebrow = "Review", title = "提交确认")
}

item {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("打卡类别", state.details.creditType.label)
            SummaryRow("运动项目", sportLabel(state.details))
            SummaryRow("开始时间", formatDateTime(state.startedAtEpochMillis))
            SummaryRow("结束时间", formatDateTime(state.finishedAtEpochMillis))
            SummaryRow("实际运动时长", formatDuration(state.activeDurationMillis))
            SummaryRow("计入学时", "${creditedExerciseHours(state.activeDurationMillis)}h")
            SummaryRow("打卡日期", formatDate(state.startedAtEpochMillis))
            SummaryRow("关联课程", currentCourseDisplayName)
            SummaryRow("定位状态", if (hasLocation) "已获取位置" else "未获取位置")
            SummaryRow("凭证数量", "${selectedImageCount} 张照片${if (selectedVideoCount > 0) " + ${selectedVideoCount} 个视频" else ""}")
        }
    }
}

// 内容真实性确认勾选
item {
    var confirmed by remember { mutableStateOf(false) }
    SwissPanel {
        Row(verticalAlignment = CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
            Text("我确认以上信息和提交的凭证内容真实有效。", style = bodyMedium)
        }
    }
}
```

其中 `SummaryRow` 是一个简单 Composable：

```kotlin
@Composable
fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = onSurfaceVariant, style = bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(value, color = onSurface, style = bodyMedium, fontWeight = Medium)
    }
}
```

**提交按钮**只有在 `confirmed == true` 时才 enabled。

**依赖**：无（UI 框架，使用 session state 中已有数据）

---

### 任务 D6：开始运动时获取位置

**文档依据**：§5.5 — 点击"开始运动"后尝试获取当前位置

**修改文件**：

- [ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)
- [ExerciseSessionController.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/session/ExerciseSessionController.kt)
- `AndroidManifest.xml`

**具体实现**：

1. **AndroidManifest.xml 新增**：

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

2. **ExerciseSessionController 中**：

```kotlin
// 新增
val locationStatus: StateFlow<LocationStatus>  // Unknown, Acquiring, Acquired(lat, lng), Unavailable

fun requestLocation(context: Context) {
    // 使用 FusedLocationProviderClient.getCurrentLocation()
    // 成功 → locationStatus = Acquired
    // 失败/拒绝 → locationStatus = Unavailable
}
```

3. **ExerciseCheckInScreen 中**：

   - 开始运动时调用 `controller.requestLocation(context)`
   - 运动中的 "未获取位置" StatusBadge 改为读取 `controller.locationStatus`
   - 显示 "已获取位置" 或 "未获取位置"
4. **权限请求**：使用 `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`

**依赖**：需要引入 `play-services-location`（或直接用 Android 原生 `LocationManager`）

---

### 任务 D7：暂停会话保持可恢复（v4.9）

**修改文件**：[ExerciseSessionController.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/session/ExerciseSessionController.kt)

**具体实现**：

不得根据暂停时长调用 `requestFinish()` 或展示自动结束提示。`autoFinishIfNeeded()` 仅保留运动中的 2 小时上限处理；暂停会话可无限期保留，直至学生恢复或主动结束。

---

### 任务 D8：提交成功确认页

**文档依据**：§5.8 — 提交成功后显示"打卡已记录"，清除草稿和进行中状态

**修改文件**：[ExerciseCheckInScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/ExerciseCheckInScreen.kt)

**具体实现**：

在 `ExerciseSessionState` 中新增 `Submitted` 状态：

```kotlin
// ExerciseSessionState.kt
sealed interface ExerciseSessionState {
    // ... 现有
    data object Idle
    data class Active(...)
    data class Paused(...)
    data class Finished(...)
    // 新增
    data class Submitted(val creditedHours: Int, val summary: SubmissionSummary)
}

data class SubmissionSummary(
    val date: String,
    val startTime: String,
    val endTime: String,
    val duration: String,
    val creditedHours: Int,
    val creditType: String,
    val sportType: String,
    val courseName: String,
    val proofCount: Int
)
```

当提交成功（真实 API 调用成功后），状态变为 `Submitted`。UI 展示：

```kotlin
ExerciseSessionState.Submitted -> {
    // 成功页
    SwissPanel {
        Icon(CheckCircle, tint = primary, modifier = size(64.dp))
        Text("打卡已记录", style = headlineMedium)
        Spacer(16.dp)
        // 摘要信息
        ...
        Button("查看记录") { /* 跳转到记录 tab */ }
        Button("返回首页") { /* 重置状态 */ }
    }
}
```

---

## 五、打卡记录页整改

### 任务 E1：CheckInRecord 模型 — 替换旧审核状态为新模型

**文档依据**：§2.1 — 不提供旧审核状态标签（待审核/已通过等），记录默认有效
**文档依据**：§8.3 — 有效/无效两种状态

**修改文件**：[StudentModels.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/core/model/StudentModels.kt)

**具体实现**：

1. **替换 ReviewStatus 枚举**：

```kotlin
// 旧（删除）：
enum class ReviewStatus(val label: String) {
    Pending("待审核"), Approved("已通过"), Rejected("被驳回"),
    Supplement("需补材料"), Offset("系统抵扣")
}

// 新（替换）：
enum class RecordValidity(val label: String) {
    VALID("有效"),           // 默认，计入学时
    INVALID("不计入学时"),    // 教师标记为无效
}
```

2. **CheckInRecord 字段变更**：

```kotlin
data class CheckInRecord(
    // ... 保留字段
    // 删除：status: ReviewStatus
    // 新增：
    val validity: RecordValidity,           // 有效/无效
    val invalidityReason: String?,          // 无效原因（仅 INVALID 时有值）
    // 删除 AI 相关字段：AiReviewStatus, AiRiskLevel, aiRiskCodes 等
    // 修改 feedback：
    val teacherPublicFeedback: String?,     // 教师公开反馈
    val teacherInternalNote: String?,       // 教师内部备注（学生不可见）
)
```

3. **删除 AI 相关枚举**：`AiReviewStatus`、`AiRiskLevel` — 文档明确不提供 AI 查重
4. **删除 CourseTask 相关**：后续 LEG-004 任务处理

**影响范围**：所有引用 `CheckInRecord.status` 的地方需要改为 `validity`，约影响 6 个文件

---

### 任务 E2：RecordCard — 补齐展示字段

**修改文件**：[CheckInRecords.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/checkin/CheckInRecords.kt)、[CoursesScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/courses/CoursesScreen.kt) 中的 RecordCard

**具体实现**：

当前 RecordCard 展示：

```
taskTitle
submittedAt
creditType label · hours
sportType
proofSummary
note
```

目标 RecordCard 展示：

```
┌──────────────────────────────────────┐
│ 跑步 · 课程相关运动          有效    │
│ 2026-03-15                       │
│                                      │
│ 开始  14:30      结束  16:15         │
│ 运动时长  1h45m   计入学时  1h        │
│ 关联课程  GEPE101 / Section 1004     │
│                                      │
│ 凭证：3 张照片                     │
│                                      │
│ ┌ 教师反馈 ──────────────────────┐  │
│ │ "凭证清晰，运动内容合理"         │  │
│ └────────────────────────────────┘  │
│                                      │
│ 运动说明：篮球场训练2小时...         │
└──────────────────────────────────────┘
```

修改 `RecordCard` Composable：

```kotlin
@Composable
fun RecordCard(record: CheckInRecord) {
    SwissPanel {
        Column(verticalArrangement = spacedBy(8.dp)) {
            // 第一行：运动项目 + 类别标签 + 有效性状态
            Row {
                Text(record.sportType ?: "运动", style = titleMedium)
                Spacer(weight=1f)
                StatusBadge(record.creditType.label)
                Spacer(4.dp)
                ValidityBadge(record.validity, record.invalidityReason)
            }
            // 日期
            Text(formatDate(record.businessDate), style = labelMedium)
            // 时间信息
            Row {
                TimeBlock("开始", record.startTime)
                TimeBlock("结束", record.endTime)
                TimeBlock("运动时长", record.actualDuration)
                TimeBlock("计入学时", "${record.hours}h")
            }
            // 课程
            Text("关联课程：${record.courseDisplayName}", style = bodySmall)
            // 凭证
            Text("凭证：${record.proofSummary}", style = bodySmall)
            // 教师反馈（仅公开）
            record.teacherPublicFeedback?.let { feedback ->
                Surface(color = surfaceVariant, shape = small) {
                    Text("教师反馈：$feedback", style = bodySmall)
                }
            }
            // 运动说明
            if (record.note.isNotBlank()) {
                Text(record.note, style = bodySmall, maxLines = 3)
            }
        }
    }
}
```

**依赖**：E1（模型改造）完成后才能进行

---

## 六、课程页整改

### 任务 F1：CoursesScreen — 清理旧任务内容

**修改文件**：[CoursesScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/courses/CoursesScreen.kt)

**具体实现**：

1. **CourseDetail 中删除**：

```kotlin
// 删除整个 section：
item { SectionTitle(eyebrow = "Class Tasks", title = "本教学班任务") }
// 删除 tasks 列表渲染
// 删除 EmptyPlaceholder("暂无教学班任务", "当前教学班还没有可展示任务...")
```

2. **CourseDetail 中删除**：`DetailFactRow(label = "下一截止", value = course.deadline)`
3. **修改 Course 模型**（后续 LEG 任务处理）：删除 `deadline` 字段
4. **清理 import**：删除 `CourseTask`、`TaskStatus`、`task.creditType.courseIcon` 等不再使用的引用

---

### 任务 F2：CoursesScreen — 历史课程展示最终成绩

**修改文件**：[CoursesScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/courses/CoursesScreen.kt)

**具体实现**：

1. 在历史课程的 `CourseCard` 中新增一行：

```kotlin
// 在现有 CourseCard 中，课时进度之后
if (!course.isCurrent && course.finalGrade != null) {
    Row {
        Text("课程成绩：${course.finalGrade} 分", style = bodyMedium, fontWeight = Medium)
        Spacer(4.dp)
        StatusBadge(if (course.finalGrade >= 60) "及格" else "不及格")
    }
}
```

2. **Course 模型新增字段**（或从后端获取）：

```kotlin
val finalGrade: Int? = null  // 已归档课程的最终成绩
val gradeStatus: String? = null  // "pass" / "fail" / null
```

3. **进入历史课程详情时**：课程详情页在历史课程模式下不显示任务，但显示最终成绩和打卡记录（只读）

**依赖**：后端返回历史课程成绩数据（受接口阻塞）

---

## 七、成绩页整改 — 需整体重做

### 任务 G1：GradesScreen — 替换硬编码为动态渲染

**文档依据**：§3.4, §8.1 — 按后端 `visibleBlocks` 和 `displayOrder` 动态渲染

**修改文件**：[GradesScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/grades/GradesScreen.kt)、[StudentModels.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/core/model/StudentModels.kt)

**当前问题**：`GradeRow.gradeComponents()` 硬编码了四块（25%/30%/20%/25%）

**具体实现**：

1. **新增模型**：

```kotlin
data class GradeBlock(
    val id: String,             // 后端唯一 ID，如 "checkin", "exam", "attendance", "physical", "custom_1"
    val name: String,           // 教师自定义名称，如"体育打卡"
    val weight: Double,         // 权重，如 0.25
    val score: Int?,            // null = 未录入
    val scoreDisplay: String,   // 展示文案："未录入" | "待发布" | "缺考（计0）" | "85" | "0"
    val isVisible: Boolean,     // 后端已经过滤，前端只渲染列表中的
    val displayOrder: Int,      // 排序
    val blockType: String,      // "checkin" | "exam" | "attendance" | "physical" | "custom"
    val description: String?,   // 教师填写的说明
    val subItems: List<GradeSubItem>?
)

data class GradeSubItem(
    val name: String,
    val score: Int?,
    val scoreDisplay: String
)
```

2. **GradeRow 改造**：

```kotlin
data class GradeRow(
    val studentId: String,
    val studentName: String,
    val visibleBlocks: List<GradeBlock>,   // 替换旧四字段
    val totalScore: Int?,                  // null = 尚未计算（规则未发布或成绩未发布）
    val totalDisplay: String,              // "待发布" | "85" | "未开放"
    val isPassed: Boolean?,                // null = 不展示及格状态
    val courseGradeStatus: String,         // "rules_not_published" | "in_progress" | "publicizing" | "archived" | "correction_pending"
    val publicizingDeadline: String?,      // 公示截止时间（仅公示中时返回）
    val displayConfigVersion: Int,
    val sourceTrace: String
)
```

3. **删除旧方法**：`GradeRow.gradeComponents()` 整个删除
4. **GradesScreen 重写渲染逻辑**：

```kotlin
@Composable
fun GradesScreen(appState: StudentAppState) {
    val grades = appState.workspace.grades

    LazyColumn(verticalArrangement = spacedBy(16.dp)) {
        // 1. 课程成绩状态（G2 任务详细实现）
        item { CourseGradeStatusBanner(grades.courseGradeStatus) }

        // 2. 总分（仅当 totalScore != null 且 total 块存在时展示）
        if (grades.totalScore != null) {
            item { TotalScorePanel(grades) }
        }

        // 3. 动态渲染 visibleBlocks
        items(grades.visibleBlocks, key = { it.id }) { block ->
            GradeBlockCard(block)
        }

        // 4. 公示信息和申诉入口（仅 publicizing 状态）
        if (grades.courseGradeStatus == "publicizing") {
            item { AppealEntryPanel(grades) }
        }
    }
}
```

**依赖**：后端需要按照新模型返回数据（受接口阻塞），但前端 UI 框架可以先用 mock 数据验证

---

### 任务 G2：GradesScreen — 5 种课程成绩状态 UI

**文档依据**：§1.4 — 规则未发布 / 进行中 / 公示中 / 已归档 / 修正审批中

**修改文件**：[GradesScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/grades/GradesScreen.kt)

**具体实现**：

```kotlin
@Composable
fun CourseGradeStatusBanner(status: String, publicizingDeadline: String?) {
    when (status) {
        "rules_not_published" -> {
            SwissPanel(modifier = /* 浅灰背景 */) {
                Icon(Info, ...)
                Text("请等待教师发布成绩规则", style = titleMedium)
                Text("成绩规则发布后，你将看到课程成绩项目及其权重。在此期间仍可正常打卡。", style = bodySmall)
            }
        }
        "in_progress" -> {
            SwissPanel(modifier = /* 浅蓝背景 */) {
                Text("成绩进行中", style = titleMedium)
                Text("教师正在录入成绩。已开放的成绩块显示当前状态，正式分数发布后将通知你。", style = bodySmall)
            }
        }
        "publicizing" -> {
            // 公示中 banner + 倒计时
            SwissPanel(modifier = /* 浅绿背景 */) {
                Text("成绩公示中", style = titleMedium)
                val daysLeft = calculateDaysLeft(publicizingDeadline)
                Text("公示截止：$publicizingDeadline（剩余 ${daysLeft} 天）", style = bodySmall)
                Text("如对成绩有疑问，请在公示期内提交申诉。", style = bodySmall)
            }
        }
        "archived" -> {
            SwissPanel(modifier = /* 浅灰背景 */) {
                Icon(Lock, ...)
                Text("成绩已归档", style = titleMedium)
                Text("本课程成绩已归档，以下为归档时的成绩快照。", style = bodySmall)
            }
        }
        "correction_pending" -> {
            SwissPanel(modifier = /* 浅黄背景 */) {
                Text("成绩修正审批中", style = titleMedium)
                Text("教师已提交成绩修正申请，等待管理员审批。当前展示为归档快照。", style = bodySmall)
            }
        }
    }
}
```

**依赖**：G1（模型改造）

---

### 任务 G3：GradesScreen — 成绩申诉入口与表单

**文档依据**：§5.3 — 选择争议块、填写理由（≥10字）、同一块存在未处理申诉时不可再提

**需要新建文件**：`feature/grades/GradeAppealScreen.kt`（或作为 GradesScreen 的子屏）

**具体实现**：

1. **申诉入口**（在 GradesScreen 公示中状态下）：

```kotlin
item {
    SwissPanel(modifier = bnbuClickable { onOpenAppeal(null) }) {
        Row {
            Icon(Help/Question, tint = primary)
            Spacer(8.dp)
            Column {
                Text("成绩申诉", style = titleMedium)
                Text("如对成绩有疑问，可在此提交申诉", style = bodySmall)
            }
            Spacer(weight=1f)
            Icon(ChevronRight)
        }
    }
}
```

2. **每个成绩块上的申诉按钮**：

```kotlin
// 在每个 GradeBlockCard 底部
if (grades.courseGradeStatus == "publicizing" && !block.hasPendingAppeal) {
    TextButton(onClick = { onOpenAppeal(block.id) }) {
        Text("对此项成绩有疑问？")
    }
}
```

3. **申诉表单**（新页面/子屏）：

```kotlin
@Composable
fun GradeAppealForm(
    blockId: String?,
    blockName: String,
    existingAppeals: List<GradeAppeal>,
    onSubmit: (GradeAppeal) -> Unit
) {
    // 选择争议块（如果从总入口进来，需要下拉选择；从块进来则自动选中）
    // 申诉理由（OutlinedTextField，minLines=3，字数计数 ≥10）
    // 可选：上传证明（最多 3 张）
    // 提交按钮（理由≥10字才 enabled）
    // 如果有未处理申诉 → 提示"该成绩块已有未处理申诉，请等待教师处理后再提交"
}
```

4. **新增模型**：

```kotlin
data class GradeAppeal(
    val id: String,
    val blockId: String,
    val blockName: String,
    val reason: String,
    val status: String,  // "pending", "accepted", "rejected"
    val teacherResponse: String?,
    val submittedAt: String,
    val resolvedAt: String?
)
```

**依赖**：后端需要申诉接口（受接口阻塞），前端 UI 框架可用 mock 数据搭建

---

## 八、免测与校队社团

### 任务 H1：ExemptionScreen — 体测类型按性别自动默认

**修改文件**：[ExemptionScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/exemption/ExemptionScreen.kt)

**具体实现**：

修改 `NewExemptionForm` 中的 `ExemptionTypeSelector` 逻辑：

```kotlin
// 当前：
fun String?.toExemptionType(): ExemptionType = when (this) { ... else -> ExemptionType.Run800 }

// 改为：
fun String?.toExemptionType(gender: String): ExemptionType = when (this) {
    "1000m" -> ExemptionType.Run1000
    "team" -> ExemptionType.Team
    "club" -> ExemptionType.Club
    else -> when (gender) {
        "male" -> ExemptionType.Run1000
        else -> ExemptionType.Run800
    }
}

// 调用处：
val defaultType = initialExemption?.type.toExemptionType(appState.workspace.student.gender)
```

同时在 UI 上，800m 和 1000m 选项应只展示符合性别的那个（或展示两个但默认选中对应性别）：

```kotlin
// 选项展示逻辑：根据性别决定默认值
val availableRunTypes = if (student.gender == "male") {
    listOf(ExemptionType.Run1000)  // 男生只显示 1000m
} else {
    listOf(ExemptionType.Run800)   // 女生只显示 800m
}
```

---

### 任务 H2：ExemptionScreen — 同类型重复申请限制

**修改文件**：[ExemptionScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/exemption/ExemptionScreen.kt)

**具体实现**：

在 `NewExemptionForm` 渲染前检查：

```kotlin
// 在 ExemptionScreen 中
val hasPendingExemption = exemptions.any { 
    it.status == "待审核" || it.status == "审核中" 
}
val hasPendingSameType = exemptions.any {
    (it.status == "待审核" || it.status == "审核中") && it.type == selectedType.apiValue
}

// 在类型选择器中
if (hasPendingSameType) {
    // 禁用该类型的选项，并显示提示
    ValidationPanel(message = "你已有一个相同类型的待审核申请，请等待教师处理后再提交新申请。")
}
```

---

### 任务 H3：ExemptionScreen — 最多 2 次申请规则

**修改文件**：[ExemptionScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/exemption/ExemptionScreen.kt)

**具体实现**：

```kotlin
// 统计同类型申请的被驳回次数
val rejectionCount = exemptions.count { 
    it.type == selectedType.apiValue && it.status == "已驳回" 
}

// 在 NewExemptionForm 中
if (rejectionCount >= 2) {
    item {
        SwissPanel(/* 浅红背景 */) {
            Icon(Warning, tint = error)
            Text("已达到最大申请次数", style = titleMedium)
            Text(
                "该类型的免测申请已被驳回 ${rejectionCount} 次，无法再次通过系统提交。如需继续申请，请联系你的任课教师线下处理。",
                style = bodyMedium
            )
        }
    }
    // 不显示表单
    return
}

// 如果是首次提交但之前已被驳回 1 次
if (rejectionCount == 1) {
    item {
        ValidationPanel(
            message = "这是你最后一次提交机会。请确保材料完整有效，再次被驳回后将无法通过系统重新提交。"
        )
    }
}
```

**依赖**：无（使用现有 exemptions 数据即可）

---

### 任务 H4：ExemptionScreen — 通过后不可撤销说明

**修改文件**：[ExemptionScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/exemption/ExemptionScreen.kt)

**具体实现**：

在 `ExemptionDetail` 中，当状态为"已通过"时：

```kotlin
if (exemption.status == "已通过" && !exemption.type.toExemptionType().isCheckInExemption) {
    // 体测免测通过后
    item {
        SwissPanel(/* 浅蓝信息背景 */) {
            Row {
                Icon(Info, tint = primary)
                Spacer(8.dp)
                Text(
                    "体测免测申请已获批准，本学期内不可撤销。如需恢复体测，请联系任课教师线下处理。免测学生的体测耐力跑分数由教师自定义评定。",
                    style = bodySmall
                )
            }
        }
    }
}
```

---

## 九、多语言支持 — 整模块搭建

### 任务 I1：Android 端中英双语基础设施

**文档依据**：§1.6, §1.7 — 全部 UI 中文/English 双语

**这是最基础的改造任务，影响所有文件。**

**具体实现**：

1. **创建 strings.xml 英文版**：

```
app/src/main/res/values/strings.xml        ← 中文（默认，已有或新建）
app/src/main/res/values-en/strings.xml     ← 英文（新建）
```

2. **第一步：提取当前硬编码中文**。从以下文件逐一提取到 strings.xml：

   - `LoginScreen.kt`（约 15 个文案）
   - `DashboardScreen.kt`（约 20 个文案）
   - `CoursesScreen.kt`（约 18 个文案）
   - `ExerciseCheckInScreen.kt`（约 30 个文案）
   - `GradesScreen.kt`（约 15 个文案）
   - `ProfileScreen.kt`（约 15 个文案）
   - `ExemptionScreen.kt`（约 20 个文案）
   - `NotificationSheet.kt`（约 10 个文案）
   - `EnduranceScoringScreen.kt`（约 10 个文案）
   - `CheckInRecords.kt`（约 8 个文案）
3. **中英文映射示例**：

```xml
<!-- values/strings.xml (中文) -->
<string name="login_title">BNBU 体育打卡与成绩进度</string>
<string name="login_email_button">邮箱验证码登录</string>
<string name="login_phone_button">手机验证码登录</string>
<string name="login_scan_button">扫码加入课程</string>
<string name="dashboard_greeting">你好，%1$s</string>
<string name="checkin_start">开始运动</string>
<string name="checkin_pause">暂停运动</string>
<string name="grades_total_estimate">总分预估</string>
```

```xml
<!-- values-en/strings.xml (英文) -->
<string name="login_title">BNBU Sports Check-in & Grade Progress</string>
<string name="login_email_button">Email Verification Code</string>
<string name="login_phone_button">SMS Verification Code</string>
<string name="login_scan_button">Scan QR to Join Course</string>
<string name="dashboard_greeting">Hello, %1$s</string>
<string name="checkin_start">Start Exercise</string>
<string name="checkin_pause">Pause Exercise</string>
<string name="grades_total_estimate">Estimated Total</string>
```

4. **语言检测与切换**：

```kotlin
// 在 AppLocalStore 中
fun loadLocale(): String  // "zh" or "en"
fun saveLocale(locale: String)

// 在 MainActivity 或 Application 中
fun applyLocale(locale: String) {
    val config = resources.configuration
    config.setLocale(Locale(locale))
    resources.updateConfiguration(config, resources.displayMetrics)
}

// 首次启动自动检测系统语言：
val systemLocale = Locale.getDefault().language  // "zh" or "en"
val savedLocale = localStore.loadLocale()
val effectiveLocale = if (savedLocale != null) savedLocale 
    else if (systemLocale in listOf("zh", "en")) systemLocale 
    else "en"  // 默认英文
```

5. **ProfileScreen 中新增语言切换**：

```kotlin
// SettingsPanel 中新增
SwissPanel {
    Text("界面语言 / Language", style = titleMedium)
    SegmentedControl(
        values = listOf("中文", "English"),
        selected = currentLocale,
        onSelected = { appState.updateLocale(it) }
    )
}
```

**执行策略**：因为一次修改全部文件工作量太大，建议按文件逐个拆分：

| 子任务 | 文件                      | 文案数 | 预估改动行 |
| ------ | ------------------------- | ------ | ---------- |
| I1a    | LoginScreen.kt            | 15     | +30/-30    |
| I1b    | DashboardScreen.kt        | 20     | +40/-40    |
| I1c    | ExerciseCheckInScreen.kt  | 30     | +60/-60    |
| I1d    | CoursesScreen.kt          | 18     | +36/-36    |
| I1e    | GradesScreen.kt           | 15     | +30/-30    |
| I1f    | ProfileScreen.kt          | 15     | +30/-30    |
| I1g    | ExemptionScreen.kt        | 20     | +40/-40    |
| I1h    | NotificationSheet.kt      | 10     | +20/-20    |
| I1i    | EnduranceScoringScreen.kt | 10     | +20/-20    |
| I1j    | CheckInRecords.kt         | 8      | +16/-16    |

**每个子任务**：提取硬编码字符串 → 添加 strings.xml 条目 → 替换为 `stringResource(id)` → 提供英文翻译 → 构建验证

---

## 十、新模块搭建

### 任务 J1：OnboardingGuideScreen — 首次四步引导

**文档依据**：§2.1 — 学生首次登录后进入四步引导

**需要新建文件**：`feature/guide/OnboardingGuideScreen.kt`

**具体实现**：

1. **四步引导页面**（使用 HorizontalPager 或简单的步骤指示器）：

   - Step 1：加入课程 — 展示扫码/输入邀请码的截图 + "扫描老师提供的课程二维码，或输入邀请码，核对课程信息后提交加入申请。"
   - Step 2：打卡 — 展示打卡界面截图 + "加入课程后，选择运动类型，开始运动计时，拍摄凭证，提交打卡记录。"
   - Step 3：成绩 — 展示成绩页截图 + "随时查看教师已开放的成绩项目和进度。成绩发布后可提交申诉。"
   - Step 4：申请 — 展示免测/认证截图 + "如需免测或校队/社团认证，在此提交申请。"
2. **每步 UI**：

```kotlin
@Composable
fun GuideStep(step: GuideStep) {
    Column(horizontalAlignment = CenterHorizontally) {
        // 插图占位（后续用实际截图）
        Box(modifier = size(280.dp, 200.dp).background(surfaceVariant, shape = medium)) {
            Text("（界面截图）", modifier = align(Center))
        }
        Spacer(24.dp)
        Text(step.title, style = headlineSmall)
        Spacer(12.dp)
        Text(step.description, style = bodyMedium, textAlign = Center)
    }
}
```

3. **底部**：步骤指示器圆点 + 「跳过引导」+ 「下一步/完成」
4. **存储**：在 localStore 中记录 `onboarding_completed_{accountId} = true`，已完成的账号不再展示
5. **帮助中心重新查看**：在 ProfileScreen 或帮助中心中提供"重新查看引导"入口

---

### 任务 K1：HelpCenterScreen — 帮助中心

**文档依据**：§3 — 常见事项、关键词搜索、操作指引、离线基础内容

**需要新建文件**：`feature/help/HelpCenterScreen.kt`

**具体实现**：

1. **搜索栏**（SearchBar with OutlinedTextField）：

```kotlin
var searchQuery by remember { mutableStateOf("") }
OutlinedTextField(
    value = searchQuery,
    onValueChange = { searchQuery = it },
    placeholder = { Text("搜索帮助内容...") },
    leadingIcon = { Icon(Search, ...) },
    singleLine = true
)
```

2. **常见事项列表**（从后端配置加载，离线时用本地缓存）：

```kotlin
data class HelpArticle(
    val id: String,
    val title: String,
    val category: String,  // "login", "checkin", "grades", "exemption", "organization", "notifications"
    val content: String,   // Markdown 或 HTML
    val order: Int
)
```

3. **分类展示**：

   - 登录与账号
   - 扫码/邀请码加入课程
   - 申请补正与审核状态
   - 打卡与凭证上传
   - 草稿恢复
   - 课程与成绩
   - 免测与组织认证
   - 通知
4. **离线支持**：首次加载后缓存到本地（SharedPreferences 或 Room），离线时展示缓存内容
5. **入口**：ProfileScreen 设置面板中新增"帮助中心"入口

**依赖**：后端接口（受接口阻塞），第一步可用本地硬编码的常见问题列表

---

### 任务 L1：FeedbackScreen — 服务反馈表单

**文档依据**：§4.2 — 服务类别、说明、当前页面、客户端版本、最多 3 张截图

**需要新建文件**：`feature/feedback/FeedbackScreen.kt`

**具体实现**：

1. **表单字段**：

```kotlin
// 服务类别下拉
val categories = listOf("功能咨询", "打卡问题", "成绩问题", "课程问题", "账号问题", "免测/认证", "系统故障", "其他")
var selectedCategory by remember { mutableStateOf(categories.first()) }

// 反馈说明（必填，<=2000 字）
var description by remember { mutableStateOf("") }

// 当前页面（自动获取，可手动修改）
var currentPage by remember { mutableStateOf("") }

// 截图（最多 3 张，可选）
var screenshots by remember { mutableStateOf<List<Uri>>(emptyList()) }
```

2. **截图功能**：

   - 使用 `ActivityResultContracts.TakePicture()` 拍照
   - 或从相册选择（`OpenMultipleDocuments`，最多 3 张）
3. **提交**：调用 `POST /api/v1/student/feedback`（接口待确认）
4. **提交后**：显示工单编号和状态查看入口
5. **工单状态查看**：可查看已提交工单的列表和处理状态
6. **入口**：ProfileScreen 设置面板中"服务反馈"

**依赖**：后端接口（受接口阻塞）

---

### 任务 M1：PrivacyConsentScreen — 首次隐私政策强制同意

**文档依据**：§1.5 — 学生首次登录时弹窗展示《隐私政策》，必须点击同意才能进入系统

**修改文件**：[AppRootScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/shell/AppRootScreen.kt)

**具体实现**：

1. **在认证流程中插入**：登录成功后、进入主界面前，检查是否已同意隐私政策

```kotlin
// AppRootScreen 中
var needsPrivacyConsent by remember { mutableStateOf(false) }

// 登录成功回调中
onLoginSuccess = {
    if (!localStore.hasAgreedPrivacyPolicy()) {
        needsPrivacyConsent = true
    } else {
        // 正常进入主界面
    }
}
```

2. **同意弹窗**（不可跳过的 AlertDialog 或全屏页面）：

```kotlin
if (needsPrivacyConsent) {
    AlertDialog(
        onDismissRequest = {},  // 不可点外部关闭
        title = { Text("隐私政策") },
        text = {
            Column {
                Text("欢迎使用 BNBU Sports。使用本系统前，请阅读并同意以下隐私政策：")
                Spacer(12.dp)
                // 隐私政策摘要（完整版可点击链接查看）
                Text("我们将收集你的学号、姓名、运动打卡记录、位置信息（仅运动时）...")
                Spacer(12.dp)
                TextButton(onClick = onOpenFullPrivacy) {
                    Text("查看完整隐私政策")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                localStore.agreePrivacyPolicy(
                    policyVersion = BuildConfig.PRIVACY_POLICY_VERSION,
                    agreedAt = Instant.now().toString()
                )
                needsPrivacyConsent = false
            }) {
                Text("同意并继续")
            }
        },
        dismissButton = {
            TextButton(onClick = { /* 退出 App 或返回登录页 */ }) {
                Text("不同意，退出")
            }
        }
    )
}
```

3. **localStore 中**：

```kotlin
fun hasAgreedPrivacyPolicy(): Boolean
fun agreePrivacyPolicy(policyVersion: String, agreedAt: String)
fun getPrivacyConsentInfo(): Pair<String, String>?  // (version, agreedAt)
```

**依赖**：无

---

### 任务 M2：DataDeletionScreen — 数据删除申请

**文档依据**：§1.5 — 学生可在「个人设置」中提交数据删除申请

**需要新建文件**：`feature/profile/DataDeletionScreen.kt`

**具体实现**：

1. **入口**：ProfileScreen → 设置 → "数据删除申请"
2. **申请页**：

```
┌──────────────────────────────────────┐
│ 数据删除申请                          │
│                                      │
│ 说明：                                │
│ 提交申请后，管理员将审批你的数据删除    │
│ 请求。审批通过后：                    │
│                                      │
│ · 个人打卡记录和凭证文件将被删除       │
│ · 已完成成绩评定、申诉记录将保留至     │
│   毕业后 5 年                         │
│ · 已归档课程的成绩数据不可删除         │
│ · 保留的统计数据将做匿名化处理         │
│                                      │
│ 申请原因：[________________]          │
│ (必填，<=500 字)                     │
│                                      │
│ ⚠️ 此操作不可撤销                     │
│                                      │
│ [提交申请]   [取消]                   │
└──────────────────────────────────────┘
```

3. **确认弹窗**：点击"提交申请"后弹出二次确认，展示删除范围，需要输入学号确认

---

### 任务 M3：ProfileScreen — 修改密码入口（v4.5 兼容）

**注意**：v4.6 改为验证码登录后，此功能可能不适用。但文档 v4.5 §3.5 仍提到"已登录修改密码"。如果后端仍支持密码登录，保留此功能。

**修改文件**：[ProfileScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/profile/ProfileScreen.kt)

**具体实现**：在 SettingsPanel 中新增"修改密码"入口，点击进入 `ChangePasswordScreen`：

```kotlin
// ChangePasswordScreen.kt (新建)
// 表单：旧密码 + 新密码 + 确认新密码
// 提交 → 后端校验 → 成功提示"密码已修改"→ 返回
```

**如果 v4.6 方案确认后废弃密码**，删除此任务即可。

---

## 十一、系统运维 UI

### 任务 N1：MaintenanceScreen — 维护模式/只读模式页面

**文档依据**：§21 — 维护模式显示维护页面，只读模式禁止写入

**修改文件**：[AppRootScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/shell/AppRootScreen.kt)

**具体实现**：

1. **在 AppRootScreen 的最外层包装**：

```kotlin
// 从后端或本地配置读取系统模式
when (appState.systemMode) {
    SystemMode.NORMAL -> { /* 正常渲染 */ }
    SystemMode.READ_ONLY -> {
        // 正常渲染内容 + 顶部固定 banner
        ReadOnlyBanner(appState)
        NormalContent()
    }
    SystemMode.MAINTENANCE -> {
        // 全屏维护页面
        MaintenancePage()
    }
}
```

2. **ReadOnlyBanner**：

```kotlin
@Composable
fun ReadOnlyBanner() {
    Surface(color = warningYellow, modifier = fillMaxWidth()) {
        Row(modifier = padding(12.dp)) {
            Icon(Warning, ...)
            Text("系统当前处于只读模式，暂不支持提交打卡、申请等写入操作。查看已有数据不受影响。")
        }
    }
}
```

3. **MaintenancePage**：

```kotlin
@Composable
fun MaintenancePage() {
    Box(contentAlignment = Center, modifier = fillMaxSize()) {
        Column(horizontalAlignment = CenterHorizontally) {
            Icon(Construction/Build, size = 64.dp, tint = primary)
            Spacer(24.dp)
            Text("系统维护中", style = headlineMedium)
            Spacer(12.dp)
            Text("BNBU Sports 正在进行系统维护，预计恢复时间：...")
            Text("维护期间暂不可使用，请稍后再试。")
        }
    }
}
```

4. **systemMode 的数据来源**：可以在 App 启动时的健康检查接口中返回

**依赖**：后端健康检查接口（受接口阻塞）

---

### 任务 N2：VersionCheckDialog — 最低版本检查

**文档依据**：§24.3 — App 启动时检查服务端最低版本，低于要求时弹窗提示更新

**修改文件**：[MainActivity.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/MainActivity.kt)

**具体实现**：

1. **启动时调用版本检查接口**：

```kotlin
// 在 MainActivity 或 Application 的初始化中
suspend fun checkMinimumVersion() {
    val response = apiClient.getMinimumVersion()  // GET /api/v1/config/minimum-app-version
    val minVersion = response.minimumVersion
    val currentVersion = BuildConfig.VERSION_NAME
    if (compareVersions(currentVersion, minVersion) < 0) {
        showUpdateRequiredDialog(minVersion, response.downloadUrl, response.updateMessage)
    }
}
```

2. **弹窗**：

```kotlin
AlertDialog(
    onDismissRequest = {},  // 不可关闭
    title = { Text("版本过低，需要更新") },
    text = {
        Text("当前版本 ${BuildConfig.VERSION_NAME} 已不再支持，请更新到 ${minVersion} 及以上版本。\n\n${updateMessage}")
    },
    confirmButton = {
        TextButton(onClick = {
            // 跳转到应用商店或下载链接
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            startActivity(intent)
        }) { Text("去更新") }
    },
    // 不提供"取消"或"跳过"按钮（强制更新）
)
```

---

### 任务 N3：ProfileScreen — App 版本号改为读取 BuildConfig

**修改文件**：[ProfileScreen.kt](BNBU-Sports-Android/app/src/main/java/edu/bnbu/student/mvp/feature/profile/ProfileScreen.kt)

**具体实现**：

当前（第 348 行）：

```kotlin
SettingLine(label = "App 版本", value = "BNBU Student MVP 1.0")
```

改为：

```kotlin
SettingLine(label = "App 版本", value = "BNBU Student ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
```

---

## 十二、旧模型清理（LEG 系列）

### 任务 LEG-001～007：删除旧任务、旧审核、AI 模型残留

**说明**：这些是数据模型和逻辑清理任务，而非 UI 新增，但会影响 UI 展示。

**修改文件汇总**：

| 任务    | 文件                       | 操作                                                               |
| ------- | -------------------------- | ------------------------------------------------------------------ |
| LEG-001 | DashboardScreen.kt         | 删除 NextTasks、TaskRow、旧 FocusPlan 文案                         |
| LEG-002 | CoursesScreen.kt           | 删除 CourseDetail 中任务相关 section                               |
| LEG-003 | CheckInScreen.kt（旧页面） | 如果确认 ExerciseCheckInScreen 为主入口，删除旧的 CheckInScreen.kt |
| LEG-004 | StudentModels.kt           | 删除 CourseTask、TaskStatus、StudentTaskList、StudentTaskItem      |
| LEG-005 | StudentModels.kt           | 删除旧 ReviewStatus，替换为 RecordValidity（已在 E1 中处理）       |
| LEG-006 | StudentAppState.kt         | 删除 submitSupplement、submitCheckIn 中的 AI 相关代码              |
| LEG-007 | StudentModels.kt           | 删除 AiReviewStatus、AiRiskLevel 枚举和 CheckInRecord 中的 AI 字段 |

---

## 十三、P2 任务（可延后）

| 任务 | 描述                                | 文件                                |
| ---- | ----------------------------------- | ----------------------------------- |
| P2a  | 端到端测试覆盖（Compose UI 自动化） | app/src/androidTest/                |
| P2b  | 真机测试（Android 12/13/14）        | —                                  |
| P2c  | 正式签名配置                        | build.gradle.kts                    |
| P2d  | FCM Push 通知集成                   | AndroidManifest + Firebase          |
| P2e  | 更新日志页面                        | feature/settings/ChangelogScreen.kt |

---

## 推荐执行顺序

基于依赖关系和 AI 单次处理能力，建议按以下顺序逐个处理：

```
阶段一：模型清理（无外部依赖，可立即执行）
  LEG-001 → LEG-002 → LEG-003 → LEG-004 → LEG-005 → LEG-006 → LEG-007

阶段二：UI 基础设施
  I1a (LoginScreen 双语) → N3 (App 版本号) → M1 (隐私同意)

阶段三：打卡页 UI 增强（不依赖新接口）
  D3 (健康提醒) → D1 (课程信息) → D5 (完成页摘要) → D7 (暂停会话保持可恢复)

阶段四：首页整改
  C1 (删除旧任务) → C2 (学时拆分展示) → B1 (加入入口)

阶段五：记录展示
  E1 (模型替换) → E2 (RecordCard 补齐)

阶段六：课程页
  F1 (清理旧任务) → F2 (历史成绩)

阶段七：认证改造（v4.6 方案）
  A1 (LoginScreen 改造) → A2 (邮箱登录) → A3 (手机登录) → A4 (绑定联系方式)
  → A5 (恢复申请) → A6 (退出确认)

阶段八：课程加入全流程（依赖后端接口）
  B2 → B3 → B4 → B5

阶段九：成绩页重做（依赖后端接口）
  G1 → G2 → G3

阶段十：免测优化
  H1 → H2 → H3 → H4

阶段十一：新模块
  J1 (引导) → K1 (帮助中心) → L1 (服务反馈) → M2 (数据删除)

阶段十二：系统运维
  N1 (维护页面) → N2 (版本检查)

阶段十三：剩余双语迁移
  I1b → I1c → I1d → I1e → I1f → I1g → I1h → I1i → I1j
```

每个任务都设计为独立可执行，完成后可立即构建验证。标记为"受接口阻塞"的任务可以先搭建 UI 框架（使用 mock 数据），待后端接口就绪后再接入真实数据。
