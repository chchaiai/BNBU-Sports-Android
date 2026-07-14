package edu.bnbu.student.mvp.core.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.model.CheckInDraft
import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.AiReviewStatus
import edu.bnbu.student.mvp.core.model.AppThemeMode
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.Membership
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ProofUploadRule
import edu.bnbu.student.mvp.core.model.ReviewStatus
import edu.bnbu.student.mvp.core.model.SportHourRule
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.model.StudentWorkspace
import edu.bnbu.student.mvp.core.model.SyncOperation
import edu.bnbu.student.mvp.core.model.SyncOperationStatus
import edu.bnbu.student.mvp.core.model.SyncOperationType
import edu.bnbu.student.mvp.core.model.TaskStatus
import edu.bnbu.student.mvp.core.model.hourText
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentLoginRequest
import edu.bnbu.student.mvp.core.network.ProofFileReference
import edu.bnbu.student.mvp.core.network.SubmitSportRecordRequest
import edu.bnbu.student.mvp.core.network.SupplementSportRecordRequest
import edu.bnbu.student.mvp.core.network.UserDto
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

class StudentAppState(
    private val localStore: AndroidAppLocalStore? = null,
    var cacheDir: File? = null
) {
    private data class InitialLocalState(
        val workspace: StudentWorkspace? = null,
        val draft: CheckInDraft? = null,
        val lastSyncTimestamp: String? = null,
        val authToken: String? = null,
        val userProfileJson: String? = null
    )

    private val gson = GsonBuilder().serializeNulls().create()
    private val job = kotlinx.coroutines.Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val persistenceMutex = Mutex()
    private var initialLocalStateApplied = false
    private val initialLocalState = scope.async(Dispatchers.IO) {
        persistenceMutex.withLock {
            val store = localStore ?: return@withLock InitialLocalState()
            try {
                InitialLocalState(
                    workspace = store.readWorkspace().value,
                    draft = store.readDraft().value,
                    lastSyncTimestamp = store.loadLastSyncTime(),
                    authToken = store.loadAuthToken(),
                    userProfileJson = store.loadUserProfileJson()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("StudentAppState", "load initial local state failed", error)
                InitialLocalState()
            }
        }
    }

    // ── State ─────────────────────────────────────────────────────

    var isAuthenticated by mutableStateOf(false)
        private set

    var workspace by mutableStateOf(
        StudentWorkspace.empty()
    )
        private set

    var isShowingCachedData by mutableStateOf(false)
        private set

    var lastSyncTimestamp: String? by mutableStateOf(null)
        private set

    var draft by mutableStateOf<CheckInDraft?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var themeMode by mutableStateOf(
        localStore?.loadThemeMode() ?: AppThemeMode.Light
    )
        private set

    val hourRule: SportHourRule = SportHourRule.Standard

    fun updateThemeMode(mode: AppThemeMode) {
        themeMode = mode
        persist(event = "save theme mode") {
            saveThemeMode(mode)
        }
    }

    // ── API repository (set after successful login) ───────────────

    var apiRepository: ApiStudentRepository? = null
        private set

    init {
        scope.launch {
            ensureInitialLocalState()
        }
    }

    private suspend fun ensureInitialLocalState(): InitialLocalState {
        val loaded = initialLocalState.await()
        if (initialLocalStateApplied) return loaded

        initialLocalStateApplied = true
        workspace = (loaded.workspace ?: StudentWorkspace.empty()).let { cachedWorkspace ->
            if (cachedWorkspace.syncOperations.isEmpty()) {
                cachedWorkspace.copy(syncOperations = listOf(localWorkspaceLoadedOperation()))
            } else {
                cachedWorkspace
            }
        }
        lastSyncTimestamp = loaded.lastSyncTimestamp

        val savedDraft = loaded.draft
        if (
            savedDraft != null &&
            (savedDraft.taskId == "self-general" || workspace.tasks.any { it.id == savedDraft.taskId && it.status == TaskStatus.Active })
        ) {
            draft = savedDraft
        } else if (savedDraft != null) {
            persistUnit(event = "clear invalid saved draft") {
                this.clearDraft()
            }
        }
        return loaded
    }

    // ── Computed properties ───────────────────────────────────────

    val courseRemaining: Double
        get() = (hourRule.courseRequired - workspace.progress.course).coerceAtLeast(0.0)

    val generalRemaining: Double
        get() = (hourRule.generalRequired - workspace.progress.general).coerceAtLeast(0.0)

    val totalCompleted: Double
        get() {
            // Cap each category at its required max to avoid double-counting overflow.
            // e.g., if a student has 15h course (over 10h cap), only 10h counts toward total.
            val cappedCourse = workspace.progress.course.coerceAtMost(hourRule.courseRequired)
            val cappedGeneral = workspace.progress.general.coerceAtMost(hourRule.generalRequired)
            return (cappedCourse + cappedGeneral).coerceAtMost(hourRule.total)
        }

    val totalRemaining: Double
        get() = (hourRule.total - totalCompleted).coerceAtLeast(0.0)

    val completionRatio: Double
        get() = if (hourRule.total <= 0.0) 0.0 else (totalCompleted / hourRule.total).coerceIn(0.0, 1.0)

    val unreadNoticeCount by derivedStateOf {
        visibleNotices.count { it.isUnread }
    }

    val visibleNotices by derivedStateOf {
        workspace.notices.filter { it.isStudentVisible }
    }

    val activeTasks by derivedStateOf {
        workspace.tasks.filter { it.status == TaskStatus.Active }
    }

    val selfCheckInTask = CourseTask(
        id = "self-general",
        courseId = "self-general",
        creditType = CreditType.General,
        title = "自主运动打卡",
        hours = hourRule.dailyLimit,
        deadline = "",
        proof = ProofUploadRule.summaryText,
        status = TaskStatus.Active,
        updatedAt = ""
    )

    // ── Auth (real API login only — no demo path) ─────────────────

    /**
     * Log in via the backend API. Returns true on success.
     * On success, sets up the [apiRepository] with the returned bearer token
     * and refreshes the workspace from the server.
     *
     * On failure, stays on the login screen and surfaces the error via [lastError].
     */
    fun login(account: String, password: String, onResult: (Boolean) -> Unit = {}) {
        if (isLoading) return
        if (account.isBlank() || password.isBlank()) {
            lastError = "请输入账号和密码"
            return
        }
        isLoading = true
        lastError = null

        scope.launch {
            try {
                ensureInitialLocalState()
                val repo = ApiStudentRepository()
                val response = repo.login(StudentLoginRequest(account = account, password = password))
                val client = StudentApiClient().withToken(response.token)
                val apiRepo = ApiStudentRepository(apiClient = client, userProfile = response.user)
                apiRepository = apiRepo

                // Persist token and user profile for session restore (AND-004)
                val authSaved = withLocalStoreOnIo(event = "save authenticated session") {
                    val tokenSaved = saveAuthToken(response.token)
                    val profileSaved = saveUserProfile(gson.toJson(response.user))
                    tokenSaved && profileSaved
                }
                if (authSaved == false) {
                    android.util.Log.w("StudentAppState", "save authenticated session failed")
                }

                // Fetch remote workspace
                val remoteWorkspace = apiRepo.loadWorkspaceAsync()
                workspace = remoteWorkspace
                isAuthenticated = true
                isShowingCachedData = false
                saveWorkspaceNow(event = "登录成功，工作台已同步")
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("StudentAppState", "Login failed", e)
                lastError = errorMessage(e)
                onResult(false)
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Try to restore a previous session using a saved bearer token.
     * Called on app start before showing login screen.
     *
     * On network error: keeps the cached workspace and sets [isShowingCachedData]
     * so the UI can show a stale-data banner. Does NOT clear auth.
     * On auth error (401/403): clears stale auth data, shows login.
     *
     * @return true if the session was restored successfully (fresh data from API).
     */
    fun tryRestoreSession(onResult: (Boolean) -> Unit = {}) {
        if (localStore == null) {
            onResult(false)
            return
        }
        if (isLoading) return
        isLoading = true
        lastError = null
        scope.launch {
            try {
                val loaded = ensureInitialLocalState()
                val savedToken = loaded.authToken
                val savedUserJson = loaded.userProfileJson
                if (savedToken == null || savedUserJson == null) {
                    onResult(false)
                    return@launch
                }
                val user = withContext(Dispatchers.IO) {
                    gson.fromJson(savedUserJson, UserDto::class.java)
                }
                val client = StudentApiClient().withToken(savedToken)
                val apiRepo = ApiStudentRepository(apiClient = client, userProfile = user)
                apiRepository = apiRepo
                val remoteWorkspace = apiRepo.loadWorkspaceAsync()
                workspace = remoteWorkspace
                isAuthenticated = true
                isShowingCachedData = false
                saveWorkspaceNow(event = "会话已恢复")
                onResult(true)
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("401") || msg.contains("403")) {
                    // Token expired or revoked — clear auth, show login
                    withLocalStoreOnIo(event = "clear expired session") {
                        this.clearAuth()
                    }
                    lastError = null
                    onResult(false)
                } else {
                    // Network error — enter offline mode only when usable cache exists.
                    val hasCachedWorkspace = workspace != StudentWorkspace.empty()
                    isShowingCachedData = hasCachedWorkspace
                    isAuthenticated = hasCachedWorkspace
                    lastError = if (hasCachedWorkspace) {
                        "无法连接服务器，显示缓存数据"
                    } else {
                        "无法连接服务器，请检查网络后重试"
                    }
                    onResult(hasCachedWorkspace)
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Retry loading the workspace after an error. Uses the existing
     * [apiRepository] (must be set via prior login).
     */
    fun retryLoadWorkspace() {
        val apiRepo = apiRepository ?: return
        if (isLoading) return
        isLoading = true
        lastError = null
        scope.launch {
            try {
                workspace = apiRepo.loadWorkspaceAsync()
                isShowingCachedData = false
                saveWorkspaceNow(event = "工作台已刷新")
            } catch (e: Exception) {
                isShowingCachedData = workspace != StudentWorkspace.empty()
                lastError = errorMessage(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshWorkspace() {
        retryLoadWorkspace()
    }

    fun clearError() {
        lastError = null
    }

    fun logout() {
        isAuthenticated = false
        apiRepository = null
        lastError = null
        isLoading = false
        workspace = StudentWorkspace.empty()
        draft = null
        persistUnit(event = "clear local data on logout") {
            this.clearAll()
        }
    }

    /**
     * Cancel the coroutine scope and release resources.
     * Call from Activity.onDestroy().
     */
    fun destroy() {
        job.cancel()
    }

    // ── Query ─────────────────────────────────────────────────────

    fun tasksFor(course: Course): List<CourseTask> {
        return workspace.tasks.filter { it.courseId == course.id }
    }

    fun recordsFor(course: Course): List<CheckInRecord> {
        return workspace.records.filter {
            it.courseId == course.id && it.creditType != CreditType.OrganizationOffset
        }
    }

    // ── Notifications ─────────────────────────────────────────────

    fun markNoticeRead(id: String) {
        val notice = workspace.notices.firstOrNull { it.id == id } ?: return
        if (!notice.isUnread) return

        workspace = workspace.copy(
            notices = workspace.notices.map {
                if (it.id == id) it.copy(isUnread = false) else it
            }
        )
        enqueueSyncOperation(
            type = SyncOperationType.MarkNoticeRead,
            title = "标记通知已读",
            detail = notice.title
        )
        saveWorkspace(event = "通知已读状态已保存")

        // Fire-and-forget to backend
        apiRepository?.let { repo ->
            scope.launch { repo.markNotificationRead(id) }
        }
    }

    fun markAllNoticesRead() {
        val count = unreadNoticeCount
        if (count == 0) return

        // Capture which notices were unread BEFORE mutating
        val previouslyUnreadIds = visibleNotices.filter { it.isUnread }.map { it.id }

        workspace = workspace.copy(
            notices = workspace.notices.map {
                if (it.id in previouslyUnreadIds) it.copy(isUnread = false) else it
            }
        )
        enqueueSyncOperation(
            type = SyncOperationType.MarkNoticeRead,
            title = "批量标记通知已读",
            detail = "$count 条通知已切换为已读"
        )
        saveWorkspace(event = "批量通知已读已保存")

        // Sync only previously-unread notices to backend (not every notice)
        apiRepository?.let { repo ->
            scope.launch {
                val idsToSync = previouslyUnreadIds
                idsToSync.forEach { id ->
                    runCatching { repo.markNotificationRead(id) }
                }
            }
        }
    }

    // ── Check-in submission ───────────────────────────────────────

    fun submitCheckIn(
        task: CourseTask,
        hours: Double,
        note: String,
        sportType: String?,
        proofAttachments: List<ProofAttachment>,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (hasSubmittedCheckInToday()) {
            failSubmission("submitCheckIn", "今日已打卡，每天只能提交一次", onResult)
            return
        }
        if (task.status != TaskStatus.Active) {
            failSubmission("submitCheckIn", "当前任务不可提交", onResult)
            return
        }
        if (proofAttachments.isEmpty()) {
            failSubmission("submitCheckIn", "至少需要添加 1 个凭证", onResult)
            return
        }
        if (proofAttachments.any { !it.isValidForUpload }) {
            failSubmission("submitCheckIn", "凭证包含无效文件", onResult)
            return
        }
        ProofUploadRule.limitMessage(proofAttachments)?.let { message ->
            failSubmission("submitCheckIn", message, onResult)
            return
        }

        val repo = apiRepository ?: run {
            failSubmission("submitCheckIn", "尚未连接服务器，请重新登录", onResult)
            return
        }
        val submittedHours = normalizedHours(hours, task)
        isLoading = true
        lastError = null
        scope.launch {
            try {
                val cDir = cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                val uploadedFiles = repo.uploadProofFiles(
                    proofAttachments = proofAttachments,
                    cacheDir = cDir
                ).getOrThrow()
                val proofFiles = uploadedFiles.map { uploaded ->
                    ProofFileReference(
                        cosKey = uploaded.cosKey,
                        mediaType = uploaded.mediaType,
                        mimeType = uploaded.mimeType,
                        size = uploaded.size
                    )
                }
                val payload = SubmitSportRecordRequest(
                    creditType = task.creditType.label,
                    courseId = if (task.courseId == "self-general") null else task.courseId,
                    taskId = task.id.takeUnless { it == "self-general" },
                    hours = submittedHours,
                    description = note,
                    proofFiles = proofFiles,
                    sportType = sportType
                )
                val response = repo.submitRecord(payload).getOrThrow()
                val serverProofs = uploadedFiles.map { uploaded ->
                    ProofAttachment(
                        id = uploaded.cosKey,
                        type = if (uploaded.mediaType == "video") ProofMediaType.Video else ProofMediaType.Image,
                        fileName = uploaded.cosKey.substringAfterLast('/'),
                        byteCount = uploaded.size,
                        source = uploaded.url
                    )
                }
                val record = CheckInRecord(
                    id = response.id,
                    courseId = if (task.courseId == "self-general") null else task.courseId,
                    taskTitle = task.title,
                    creditType = task.creditType,
                    hours = submittedHours,
                    submittedAt = response.submittedAt,
                    status = ReviewStatus.Pending,
                    proofSummary = proofSummary(serverProofs),
                    proofPhotoCount = serverProofs.count { it.type == ProofMediaType.Image },
                    proofVideoCount = serverProofs.count { it.type == ProofMediaType.Video },
                    proofFiles = serverProofs,
                    teacherFeedback = "已提交，等待老师审核。",
                    note = note.ifBlank { "学生未填写补充说明。" },
                    sportType = sportType,
                    aiReviewStatus = AiReviewStatus.Pending,
                    aiReviewMessage = "凭证已进入 AI 初审队列。"
                )
                workspace = workspace.copy(
                    records = listOf(record) + workspace.records
                )
                enqueueSyncOperation(
                    type = SyncOperationType.SubmitRecord,
                    title = "提交打卡记录",
                    detail = "${task.title} · ${submittedHours.hourText()} · ${serverProofs.size} 个凭证",
                    status = SyncOperationStatus.Synced
                )
                clearDraft()
                saveWorkspace(event = "打卡提交已同步")
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                android.util.Log.e("StudentAppState", "submitCheckIn API failed", e)
                val message = errorMessage(e)
                lastError = message
                onResult(Result.failure(IllegalStateException(message, e)))
            } finally {
                isLoading = false
            }
        }
    }

    fun submitSupplement(
        record: CheckInRecord,
        hours: Double,
        note: String,
        proofAttachments: List<ProofAttachment>,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (record.status != ReviewStatus.Supplement && record.status != ReviewStatus.Rejected) {
            failSubmission("submitSupplement", "记录状态不允许补交", onResult)
            return
        }
        if (proofAttachments.isEmpty()) {
            failSubmission("submitSupplement", "至少需要添加 1 个凭证", onResult)
            return
        }
        if (proofAttachments.any { !it.isValidForUpload }) {
            failSubmission("submitSupplement", "凭证包含无效文件", onResult)
            return
        }

        val index = workspace.records.indexOfFirst { it.id == record.id }
        if (index < 0) return
        val mergedProofs = workspace.records[index].proofFiles + proofAttachments
        ProofUploadRule.limitMessage(mergedProofs)?.let { message ->
            failSubmission("submitSupplement", message, onResult)
            return
        }

        val repo = apiRepository ?: run {
            failSubmission("submitSupplement", "尚未连接服务器，请重新登录", onResult)
            return
        }
        val supplementMaxHours = minOf(record.hours, hourRule.dailyLimit)
        val submittedHours = if (hours >= 2.0 && supplementMaxHours >= 2.0) 2.0 else 1.0
        isLoading = true
        lastError = null
        scope.launch {
            try {
                val cDir = cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                val uploadedFiles = repo.uploadProofFiles(
                    proofAttachments = proofAttachments,
                    cacheDir = cDir
                ).getOrThrow()
                val proofFiles = uploadedFiles.map { uploaded ->
                    ProofFileReference(
                        cosKey = uploaded.cosKey,
                        mediaType = uploaded.mediaType,
                        mimeType = uploaded.mimeType,
                        size = uploaded.size
                    )
                }
                val payload = SupplementSportRecordRequest(
                    hours = submittedHours,
                    description = note,
                    proofFiles = proofFiles
                )
                repo.supplementRecord(record.id, payload).getOrThrow()
                val serverProofs = uploadedFiles.map { uploaded ->
                    ProofAttachment(
                        id = uploaded.cosKey,
                        type = if (uploaded.mediaType == "video") ProofMediaType.Video else ProofMediaType.Image,
                        fileName = uploaded.cosKey.substringAfterLast('/'),
                        byteCount = uploaded.size,
                        source = uploaded.url
                    )
                }
                val allProofs = workspace.records[index].proofFiles + serverProofs
                val updatedRecord = workspace.records[index].copy(
                    hours = submittedHours,
                    submittedAt = Instant.now().toString(),
                    status = ReviewStatus.Pending,
                    proofSummary = proofSummary(allProofs),
                    proofPhotoCount = allProofs.count { it.type == ProofMediaType.Image },
                    proofVideoCount = allProofs.count { it.type == ProofMediaType.Video },
                    proofFiles = allProofs,
                    teacherFeedback = "补充材料已提交，等待老师复审。",
                    note = note.ifBlank { "学生已按反馈补交材料。" },
                    aiReviewStatus = AiReviewStatus.Pending,
                    aiRiskLevel = null,
                    aiRiskCodes = emptyList(),
                    aiReviewMessage = "补充材料已进入 AI 复审队列。",
                    aiConfidence = null,
                    aiReviewedAt = null
                )
                val updatedRecords = workspace.records.toMutableList().also { it[index] = updatedRecord }
                val notice = StudentNotice(
                    id = UUID.randomUUID().toString(),
                    title = "补充材料已提交",
                    message = "${record.taskTitle} 的补充材料已进入复审队列。",
                    time = "刚刚",
                    category = NoticeCategory.Review,
                    isUnread = true
                )
                workspace = workspace.copy(
                    records = updatedRecords,
                    notices = listOf(notice) + workspace.notices
                )
                enqueueSyncOperation(
                    type = SyncOperationType.SupplementRecord,
                    title = "提交补充材料",
                    detail = "${record.taskTitle} · 新增 ${serverProofs.size} 个凭证",
                    status = SyncOperationStatus.Synced
                )
                saveWorkspace(event = "补充材料已同步")
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                android.util.Log.e("StudentAppState", "submitSupplement API failed", e)
                val message = errorMessage(e)
                lastError = message
                onResult(Result.failure(IllegalStateException(message, e)))
            } finally {
                isLoading = false
            }
        }
    }

    fun saveDraft(
        taskId: String,
        hours: Double,
        note: String,
        sportType: String?,
        customSportType: String,
        proofAttachments: List<ProofAttachment>
    ) {
        val task = workspace.tasks.firstOrNull { it.id == taskId && it.status == TaskStatus.Active }
            ?: run {
                clearDraft()
                return
            }

        draft = CheckInDraft(
            id = draft?.id ?: UUID.randomUUID().toString(),
            taskId = taskId,
            hours = normalizedHours(hours, task),
            note = note,
            proofAttachments = proofAttachments,
            updatedAt = "刚刚",
            sportType = sportType,
            customSportType = customSportType.takeIf { it.isNotBlank() }
        )
        saveDraft(event = "打卡草稿已保存")
    }

    fun hourLimitFor(task: CourseTask): Double {
        return minOf(task.hours, hourRule.dailyLimit)
    }

    fun hasSubmittedCheckInToday(today: LocalDate = LocalDate.now()): Boolean {
        return workspace.records.any { record ->
            record.creditType != CreditType.OrganizationOffset &&
                record.submittedAt.toLocalSubmissionDate() == today
        }
    }

    fun normalizedHours(hours: Double, task: CourseTask): Double {
        val maxHours = hourLimitFor(task)
        return if (hours >= 2.0 && maxHours >= 2.0) 2.0 else 1.0
    }

    fun clearDraft() {
        draft = null
        persistUnit(event = "clear check-in draft") {
            this.clearDraft()
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private suspend fun errorMessage(e: Exception): String {
        val msg = e.message.orEmpty()
        val serverMessage = withContext(Dispatchers.IO) {
            msg.indexOf('{').takeIf { it >= 0 }?.let { start ->
                runCatching {
                    JsonParser.parseString(msg.substring(start)).asJsonObject
                        .get("message")
                        ?.asString
                }.getOrNull()
            }
        }
        if (!serverMessage.isNullOrBlank()) return serverMessage
        return when {
            msg.contains("401") -> "账号或密码错误"
            msg.contains("400") -> "请输入账号和密码"
            msg.contains("403") -> "没有访问权限，请联系管理员"
            msg.contains("500") || msg.contains("DB_ERROR") -> "服务器内部错误，请联系管理员"
            msg.contains("Unable to resolve host") || msg.contains("UnknownHost") ->
                "无法连接服务器，请检查网络"
            msg.contains("timeout") || msg.contains("Timeout") ->
                "连接超时，请稍后再试"
            msg.contains("502") || msg.contains("503") ->
                "服务器维护中，请稍后再试"
            msg.contains("ConnectException") || msg.contains("Connection refused") ->
                "无法连接到服务器，请检查网络连接"
            msg.contains("SocketTimeoutException") ->
                "请求超时，请检查网络后重试"
            msg.contains("Gson returned null") ->
                "服务器返回数据异常，请联系管理员"
            msg.contains("CLEARTEXT") || msg.contains("cleartext") ->
                "网络安全策略错误，请联系开发人员"
            else -> "请求失败，请稍后重试"
        }
    }

    private fun logValidationFailure(method: String, reason: String) {
        android.util.Log.w("StudentAppState", "$method blocked: $reason")
    }

    private fun failSubmission(
        method: String,
        reason: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        logValidationFailure(method, reason)
        onResult(Result.failure(IllegalArgumentException(reason)))
    }

    private fun proofSummary(proofAttachments: List<ProofAttachment>): String {
        val photoCount = proofAttachments.count { it.type == ProofMediaType.Image }
        val videoCount = proofAttachments.count { it.type == ProofMediaType.Video }
        val parts = buildList {
            if (photoCount > 0) add("$photoCount 张图片")
            if (videoCount > 0) add("$videoCount 个短视频")
        }
        return parts.ifEmpty { listOf("未添加凭证") }.joinToString("，")
    }

    private val maxSyncOperations = 12

    private fun enqueueSyncOperation(
        type: SyncOperationType,
        title: String,
        detail: String,
        status: SyncOperationStatus = SyncOperationStatus.Queued
    ) {
        val operation = SyncOperation(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            detail = detail,
            createdAt = "刚刚",
            status = status
        )
        // Prepend new operation and cap the list at maxSyncOperations.
        // .take() keeps the first N entries, so the oldest entries drop off naturally.
        val updated = (listOf(operation) + workspace.syncOperations).take(maxSyncOperations)
        workspace = workspace.copy(syncOperations = updated)
    }

    private fun saveWorkspace(event: String) {
        val workspaceSnapshot = workspace
        val now = currentSyncTimestamp()
        lastSyncTimestamp = now
        persist(event = event) {
            val workspaceSaved = saveWorkspace(workspaceSnapshot)
            val syncTimeSaved = saveLastSyncTime(now)
            workspaceSaved && syncTimeSaved
        }
    }

    private suspend fun saveWorkspaceNow(event: String) {
        val workspaceSnapshot = workspace
        val now = currentSyncTimestamp()
        lastSyncTimestamp = now
        val saved = withLocalStoreOnIo(event = event) {
            val workspaceSaved = saveWorkspace(workspaceSnapshot)
            val syncTimeSaved = saveLastSyncTime(now)
            workspaceSaved && syncTimeSaved
        }
        if (saved == false) {
            android.util.Log.w("StudentAppState", "$event failed")
        }
    }

    private fun currentSyncTimestamp(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    private fun saveDraft(event: String) {
        val currentDraft = draft ?: return
        persist(event = event) {
            saveDraft(currentDraft)
        }
    }

    private fun persist(
        event: String,
        block: AndroidAppLocalStore.() -> Boolean
    ) {
        val store = localStore ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            persistenceMutex.withLock {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        store.block()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("StudentAppState", "$event failed", error)
                        false
                    }
                }
                if (!saved) {
                    android.util.Log.w("StudentAppState", "$event failed")
                }
            }
        }
    }

    private fun persistUnit(
        event: String,
        block: AndroidAppLocalStore.() -> Unit
    ) {
        val store = localStore ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            persistenceMutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        store.block()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("StudentAppState", "$event failed", error)
                    }
                }
            }
        }
    }

    private suspend fun <T> withLocalStoreOnIo(
        event: String,
        block: AndroidAppLocalStore.() -> T
    ): T? {
        val store = localStore ?: return null
        return persistenceMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    store.block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    android.util.Log.w("StudentAppState", "$event failed", error)
                    null
                }
            }
        }
    }

    private fun localWorkspaceLoadedOperation(): SyncOperation {
        return SyncOperation(
            id = "sync-local-load",
            type = SyncOperationType.ResetLocalData,
            title = "读取本地工作台",
            detail = "从 Android SharedPreferences 加载已缓存的工作台数据。",
            createdAt = "启动时",
            status = SyncOperationStatus.LocalOnly
        )
    }
}

private fun String.toLocalSubmissionDate(): LocalDate? {
    val value = trim()
    return runCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull() ?: value.take(10)
        .takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
