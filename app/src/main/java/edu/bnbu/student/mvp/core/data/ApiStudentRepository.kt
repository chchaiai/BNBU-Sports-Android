package edu.bnbu.student.mvp.core.data

import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.AiReviewStatus
import edu.bnbu.student.mvp.core.model.AiRiskLevel
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.EnduranceConversionRequest
import edu.bnbu.student.mvp.core.model.EnduranceScoreResult
import edu.bnbu.student.mvp.core.model.Exemption
import edu.bnbu.student.mvp.core.model.ExemptionApplication
import edu.bnbu.student.mvp.core.model.GradeRow
import edu.bnbu.student.mvp.core.model.Membership
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ReviewStatus
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.model.StudentProgress
import edu.bnbu.student.mvp.core.model.StudentProfile
import edu.bnbu.student.mvp.core.model.StudentTaskItem
import edu.bnbu.student.mvp.core.model.TaskStatus
import edu.bnbu.student.mvp.core.model.StudentTaskList
import edu.bnbu.student.mvp.core.model.TeacherInfo
import edu.bnbu.student.mvp.core.model.StudentWorkspace
import edu.bnbu.student.mvp.core.network.LoginResponse
import edu.bnbu.student.mvp.core.network.MembershipResponse
import edu.bnbu.student.mvp.core.network.MarkReadResponse
import edu.bnbu.student.mvp.core.network.NotificationResponse
import edu.bnbu.student.mvp.core.network.SportRecordResponse
import edu.bnbu.student.mvp.core.network.SportSummaryResponse
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentApiRequest
import edu.bnbu.student.mvp.core.network.StudentEndpoint
import edu.bnbu.student.mvp.core.network.StudentLoginRequest
import edu.bnbu.student.mvp.core.network.SubmitRecordResponse
import edu.bnbu.student.mvp.core.network.SubmitSportRecordRequest
import edu.bnbu.student.mvp.core.network.SupplementResponse
import edu.bnbu.student.mvp.core.network.SupplementSportRecordRequest
import edu.bnbu.student.mvp.core.network.UploadProofResponse
import edu.bnbu.student.mvp.core.network.UploadedProofFile
import edu.bnbu.student.mvp.core.network.UserDto
import edu.bnbu.student.mvp.core.network.EnduranceScoreResponse
import edu.bnbu.student.mvp.core.network.ExemptionResponse
import edu.bnbu.student.mvp.core.network.ExemptionSubmitResponse
import edu.bnbu.student.mvp.core.network.StudentProfileResponse
import edu.bnbu.student.mvp.core.network.StudentProfileUpdateRequest
import edu.bnbu.student.mvp.core.network.StudentTaskListResponse
import edu.bnbu.student.mvp.core.network.StudentTaskItemResponse
import edu.bnbu.student.mvp.core.network.StudentCourseDetailResponse
import edu.bnbu.student.mvp.core.network.StudentCoursesResponse
import edu.bnbu.student.mvp.core.network.StudentGradesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ApiStudentRepository(
    private var apiClient: StudentApiClient = StudentApiClient(),
    private val userProfile: UserDto? = null
) : StudentRepository {

    /**
     * The current bearer token, mirrored from [apiClient].
     *
     * Setting this replaces the underlying client with one that carries the
     * new token. Prefer calling [ApiStudentRepository.withToken] for a
     * fresh copy when both the client and profile must change.
     */
    var bearerToken: String?
        get() = apiClient.bearerToken
        set(value) {
            apiClient = apiClient.withToken(value)
        }

    // ── Auth ──────────────────────────────────────────────────────

    override suspend fun login(payload: StudentLoginRequest): LoginResponse {
        val request = loginRequest(payload)
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParse(request, LoginResponse::class.java)
        }
    }

    // ── Core loading ────────────────────────────────────────────

    override fun loadWorkspace(): StudentWorkspace {
        // Synchronous path returns an empty workspace — real callers use loadWorkspaceAsync.
        return StudentWorkspace.empty()
    }

    /**
     * Fetch the full student workspace from the backend (summary + records +
     * identity + notifications), then map DTOs → domain model.
     *
     * Throws on any network or mapping error so the caller can surface it to the
     * user. Persistent cache fallback is owned by StudentAppState so stale data is
     * never returned silently from the network layer.
     */
    override suspend fun loadWorkspaceAsync(): StudentWorkspace = withContext(Dispatchers.IO) {
        try {
            val summary: SportSummaryResponse = apiClient.executeAndParse(
                sportSummaryRequest(), SportSummaryResponse::class.java
            )
            val records: List<SportRecordResponse> = apiClient.executeAndParse(
                recordsListRequest(), Array<SportRecordResponse>::class.java
            ).toList()
            val memberships: List<MembershipResponse> = apiClient.executeAndParse(
                sportIdentityRequest(), Array<MembershipResponse>::class.java
            ).toList()
            val notices: List<NotificationResponse> = apiClient.executeAndParse(
                notificationsRequest(), Array<NotificationResponse>::class.java
            ).toList()
            val profileResult: Result<StudentProfileResponse> = try {
                Result.success(fetchProfile())
            } catch (e: Exception) {
                Result.failure(e)
            }
            // Also fetch tasks from the backend — they are a separate API call.
            // AND-004: surface task fetch errors visibly instead of silently
            // returning an empty list (which would show "暂无近期任务" misleadingly).
            val tasksResult: Result<StudentTaskListResponse> = try {
                Result.success(
                    apiClient.executeAndParse(
                        apiClient.request(StudentEndpoint.StudentTasks),
                        StudentTaskListResponse::class.java
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
            val tasksResponse = tasksResult.getOrElse {
                StudentTaskListResponse(emptyList(), emptyList())
            }
            val allTasks = tasksResponse.pending + tasksResponse.completed
            val tasksLoadError = if (tasksResult.isFailure) tasksResult.exceptionOrNull()?.message else null
            // Week2 course contract is optional during the transition from the
            // shared port 96 API. A 404 falls back to summary.courses below.
            val coursesResult: Result<StudentCoursesResponse> = try {
                Result.success(
                    apiClient.executeAndParse(
                        apiClient.request(StudentEndpoint.StudentCourses(scope = "all")),
                        StudentCoursesResponse::class.java
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
            val courseItems = coursesResult.getOrNull()?.courses.orEmpty()
            val gradesResult: Result<StudentGradesResponse> = try {
                Result.success(
                    apiClient.executeAndParse(
                        apiClient.request(StudentEndpoint.StudentGrades),
                        StudentGradesResponse::class.java
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
            val gradesResponse = gradesResult.getOrNull()
            val gradesLoadError = if (gradesResult.isFailure) gradesResult.exceptionOrNull()?.message else null

            val workspace = buildWorkspace(
                summary = summary,
                records = records,
                memberships = memberships,
                notices = notices,
                courseItems = courseItems,
                taskItems = allTasks,
                tasksLoadError = tasksLoadError,
                gradesResponse = gradesResponse,
                gradesLoadError = gradesLoadError,
                remoteProfile = profileResult.getOrNull()
            )
            workspace
        } catch (e: Exception) {
            android.util.Log.w("ApiStudentRepository", "Workspace refresh failed: ${e.message}")
            throw e
        }
    }

    // ── Grades ────────────────────────────────────────────────────

    /**
     * Fetch the student's own grade data from the backend.
     *
     * This is a separate call because grade data (exam, attendance, physical)
     * is managed by teacher/admin endpoints and is not included in the summary.
     */
    suspend fun fetchStudentGrades(): StudentGradesResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParse(
                apiClient.request(StudentEndpoint.StudentGrades),
                StudentGradesResponse::class.java
            )
        }
    }

    // ── Mutations ───────────────────────────────────────────────

    override suspend fun submitRecord(payload: SubmitSportRecordRequest): Result<SubmitRecordResponse> {
        return try {
            val request = submitSportRecordRequest(payload)
            Result.success(apiClient.executeAndParse(request, SubmitRecordResponse::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supplementRecord(
        recordId: String,
        payload: SupplementSportRecordRequest
    ): Result<SupplementResponse> {
        return try {
            val request = supplementSportRecordRequest(recordId, payload)
            Result.success(apiClient.executeAndParse(request, SupplementResponse::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markNotificationRead(id: String): Result<MarkReadResponse> {
        return try {
            val request = markNotificationReadRequest(id)
            Result.success(apiClient.executeAndParse(request, MarkReadResponse::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── DTO → domain mapping ─────────────────────────────────────

    private fun buildWorkspace(
        summary: SportSummaryResponse,
        records: List<SportRecordResponse>,
        memberships: List<MembershipResponse>,
        notices: List<NotificationResponse>,
        courseItems: List<StudentCourseDetailResponse> = emptyList(),
        taskItems: List<StudentTaskItemResponse> = emptyList(),
        tasksLoadError: String? = null,
        gradesResponse: StudentGradesResponse? = null,
        gradesLoadError: String? = null,
        remoteProfile: StudentProfileResponse? = null
    ): StudentWorkspace {
        // Student identity comes from the login response (userProfile), with
        // fallback defaults when not available (e.g. synchronous loadWorkspace).
        val profile = userProfile
        val student = StudentProfile(
            id = profile?.id ?: "",
            name = profile?.name ?: "学生",
            email = profile?.email ?: "",
            college = profile?.college ?: "",
            className = profile?.className ?: "",
            status = if (summary.completed) "已完成" else "进行中",
            gender = remoteProfile?.gender ?: profile?.gender ?: "",
            gradeLevel = remoteProfile?.currentGradeLevel
                ?: remoteProfile?.gradeLevel
                ?: profile?.gradeLevel
                ?: "",
            admissionYear = remoteProfile?.admissionYear,
            currentAcademicYear = remoteProfile?.currentAcademicYear.orEmpty(),
            gradeCalculatedAt = remoteProfile?.gradeCalculatedAt.orEmpty()
        )

        val orgCredit = memberships.firstOrNull { it.status == "认证有效" && it.offset == "可抵扣" }

        val progress = StudentProgress(
            id = student.id,
            name = student.name,
            college = student.college,
            className = student.className,
            course = summary.courseHours,
            general = summary.generalHours,
            rawGeneral = summary.generalHours,
            exam = 0,
            attendance = 0,
            physical = 0,
            status = statusText(summary),
            source = "api",
            organizationCredit = if (orgCredit != null) membershipToMembership(orgCredit) else null
        )

        // Courses and tasks are now returned by the backend summary API —
        // map them from the new `courses` field.
        val courses: List<Course> = if (courseItems.isNotEmpty()) {
            courseItems.map { c ->
                Course(
                    id = c.id,
                    code = c.code,
                    section = c.section,
                    name = c.name,
                    semester = c.semester.name.ifBlank { c.semester.academicYear },
                    students = 0,
                    pending = 0,
                    completion = 0,
                    missing = 0,
                    deadline = c.semester.endDate.orEmpty(),
                    teacher = c.teacherName,
                    teacherId = c.teacherId,
                    semesterId = c.semester.id,
                    academicYear = c.semester.academicYear,
                    term = c.semester.term,
                    semesterStatus = c.semester.status,
                    enrollmentStatus = c.enrollmentStatus,
                    isCurrent = c.isCurrent
                )
            }
        } else {
            summary.courses.map { c ->
                Course(
                    id = c.courseId,
                    code = c.courseCode,
                    section = c.courseSection,
                    name = c.courseName,
                    semester = "当前学期",
                    students = 0,
                    pending = 0,
                    completion = 0,
                    missing = 0,
                    deadline = "",
                    teacher = c.teacherName,
                    teacherId = c.teacherId,
                    isCurrent = true
                )
            }
        }

        // Map task items from the task API to domain CourseTask objects
        val tasks: List<CourseTask> = taskItems.map { t ->
            CourseTask(
                id = t.id,
                courseId = t.courseId,
                creditType = when (t.creditType) {
                    "课程相关" -> CreditType.CourseRelated
                    "其他运动" -> CreditType.General
                    else -> CreditType.General
                },
                title = t.title,
                hours = t.requiredHours,
                deadline = t.deadline,
                proof = "",
                status = when (t.status) {
                    "进行中" -> TaskStatus.Active
                    "草稿" -> TaskStatus.Draft
                    "已关闭" -> TaskStatus.Closed
                    else -> TaskStatus.Active
                },
                updatedAt = t.deadline
            )
        }

        val studentTaskList = StudentTaskList(
            pending = taskItems.filter { it.completedAt == null }.map { t ->
                StudentTaskItem(
                    id = t.id, courseId = t.courseId,
                    courseCode = t.courseCode, courseSection = t.courseSection,
                    courseName = t.courseName, title = t.title,
                    description = t.description, creditType = t.creditType,
                    requiredHours = t.requiredHours, deadline = t.deadline,
                    status = t.status, completedAt = t.completedAt
                )
            },
            completed = taskItems.filter { it.completedAt != null }.map { t ->
                StudentTaskItem(
                    id = t.id, courseId = t.courseId,
                    courseCode = t.courseCode, courseSection = t.courseSection,
                    courseName = t.courseName, title = t.title,
                    description = t.description, creditType = t.creditType,
                    requiredHours = t.requiredHours, deadline = t.deadline,
                    status = t.status, completedAt = t.completedAt
                )
            }
        )

        // Teachers are returned directly from the summary API
        val teachers: List<TeacherInfo> = summary.teachers.map { t ->
            TeacherInfo(teacherId = t.teacherId, teacherName = t.teacherName)
        }

        // Grade scores (exam, attendance, physical) are managed by teacher/admin
        // endpoints. Try to fetch from the student-facing grades endpoint;
        // fall back to zeros if not yet available.
        val studentGrade = gradesResponse?.grades
            ?.firstOrNull { it.studentId == student.id }
            ?: gradesResponse?.grades?.firstOrNull()

        val grades = if (studentGrade != null) {
            GradeRow(
                studentId = studentGrade.studentId,
                studentName = studentGrade.studentName,
                checkinScore = studentGrade.resolvedCheckinScore,
                exam = studentGrade.exam,
                attendance = studentGrade.attendance,
                physical = studentGrade.physical,
                total = studentGrade.resolvedTotal,
                sourceTrace = studentGrade.sourceTrace.orEmpty().ifBlank { "API: /student/grades" },
                missingItems = buildMissingItems(summary)
            )
        } else GradeRow(
            studentId = student.id,
            studentName = student.name,
            checkinScore = 0,
            exam = 0,
            attendance = 0,
            physical = 0,
            total = 0,
            sourceTrace = if (tasksLoadError != null) {
                "API: grade data not yet available — $tasksLoadError"
            } else {
                "API: grade data not yet available from summary endpoint"
            },
            missingItems = buildMissingItems(summary)
        )

        return StudentWorkspace(
            student = student,
            courses = courses,
            progress = progress,
            tasks = tasks,
            records = records.map { recordResponseToRecord(it) },
            grades = grades,
            memberships = memberships.map { membershipToMembership(it) },
            notices = notices.map { noticeResponseToNotice(it) },
            teachers = teachers,
            studentTasks = studentTaskList
        )
    }

    private fun buildMissingItems(summary: SportSummaryResponse): List<String> {
        val items = mutableListOf<String>()
        if (summary.courseRemaining > 0) items.add("打卡未满：课程相关还差 ${summary.courseRemaining}h")
        if (summary.generalRemaining > 0) items.add("打卡未满：其他运动还差 ${summary.generalRemaining}h")
        return items
    }

    private fun statusText(summary: SportSummaryResponse): String {
        if (summary.completed) return "已完成"
        val parts = mutableListOf<String>()
        if (summary.courseRemaining > 0) parts.add("差课程 ${summary.courseRemaining}h")
        if (summary.generalRemaining > 0) parts.add("差其他 ${summary.generalRemaining}h")
        return parts.ifEmpty { listOf("进行中") }.joinToString("，")
    }

    private fun recordResponseToRecord(r: SportRecordResponse): CheckInRecord {
        val status = when (r.status) {
            "待审核" -> ReviewStatus.Pending
            "已通过" -> ReviewStatus.Approved
            "已驳回" -> ReviewStatus.Rejected
            "补材料" -> ReviewStatus.Supplement
            "系统抵扣" -> ReviewStatus.Offset
            else -> ReviewStatus.Pending
        }
        val creditType = when (r.creditType) {
            "课程相关" -> CreditType.CourseRelated
            "其他运动" -> CreditType.General
            "系统抵扣" -> CreditType.OrganizationOffset
            else -> CreditType.General
        }
        return CheckInRecord(
            id = r.id,
            courseId = r.courseId,
            taskTitle = r.taskId ?: "自主打卡",
            creditType = creditType,
            hours = r.hours,
            submittedAt = r.submittedAt ?: "",
            status = status,
            proofSummary = "${r.proofFiles.size} 个凭证",
            proofPhotoCount = r.proofFiles.count { it.mediaType == "image" },
            proofVideoCount = r.proofFiles.count { it.mediaType == "video" },
            proofFiles = r.proofFiles.map { proof ->
                ProofAttachment(
                    id = proof.cosKey.ifBlank { proof.url },
                    type = if (proof.mediaType == "video") ProofMediaType.Video else ProofMediaType.Image,
                    fileName = proof.cosKey.substringAfterLast('/').ifBlank { "proof" },
                    byteCount = proof.size.takeIf { it > 0 },
                    source = proof.url.ifBlank { "api" }
                )
            },
            teacherFeedback = r.reviewComment ?: "",
            note = r.description ?: "",
            sportType = r.sportType,
            aiReviewStatus = when (r.aiReviewStatus) {
                "normal" -> AiReviewStatus.Normal
                "abnormal" -> AiReviewStatus.Abnormal
                "manual_review" -> AiReviewStatus.ManualReview
                "pending" -> AiReviewStatus.Pending
                else -> null
            },
            aiRiskLevel = when (r.aiRiskLevel) {
                "low" -> AiRiskLevel.Low
                "medium" -> AiRiskLevel.Medium
                "high" -> AiRiskLevel.High
                else -> null
            },
            aiRiskCodes = r.aiRiskCodes,
            aiReviewMessage = r.aiReviewMessage,
            aiConfidence = r.aiConfidence,
            aiReviewedAt = r.aiReviewedAt
        )
    }

    private fun membershipToMembership(m: MembershipResponse): Membership {
        return Membership(
            id = m.id,
            type = m.type,
            organization = m.organization,
            studentId = m.studentId,
            studentName = m.studentName,
            status = m.status,
            validUntil = m.validUntil ?: "",
            offset = m.offset,
            comment = m.comment ?: "",
            updatedBy = m.updatedBy ?: "",
            updatedAt = m.updatedAt ?: ""
        )
    }

    private fun noticeResponseToNotice(n: NotificationResponse): StudentNotice {
        val category = when (n.category) {
            "截止提醒" -> NoticeCategory.Deadline
            "审核反馈", "申请与材料" -> NoticeCategory.Review
            "组织认证" -> NoticeCategory.Organization
            else -> NoticeCategory.System
        }
        return StudentNotice(
            id = n.id,
            title = n.title,
            message = n.message,
            time = n.time,
            category = category,
            isUnread = n.isUnread,
            targetType = n.targetType,
            targetId = n.targetId
        )
    }

    // ── Request factories ────────────────────────────────────────

    fun loginRequest(payload: StudentLoginRequest): StudentApiRequest {
        return apiClient.request(StudentEndpoint.Login, payload)
    }

    fun sportSummaryRequest(): StudentApiRequest {
        return apiClient.request(StudentEndpoint.SportSummary)
    }

    fun submitSportRecordRequest(payload: SubmitSportRecordRequest): StudentApiRequest {
        return apiClient.request(StudentEndpoint.SportRecords, payload)
    }

    fun recordsListRequest(): StudentApiRequest {
        return apiClient.request(StudentEndpoint.SportRecordsList)
    }

    fun supplementSportRecordRequest(
        recordId: String,
        payload: SupplementSportRecordRequest
    ): StudentApiRequest {
        return apiClient.request(StudentEndpoint.SupplementSportRecord(recordId), payload)
    }

    fun sportIdentityRequest(): StudentApiRequest {
        return apiClient.request(StudentEndpoint.SportIdentity)
    }

    fun notificationsRequest(): StudentApiRequest {
        return apiClient.request(StudentEndpoint.Notifications)
    }

    fun markNotificationReadRequest(id: String): StudentApiRequest {
        return apiClient.request(StudentEndpoint.MarkNotificationRead(id))
    }

    // ── New: Endurance scoring ────────────────────────────────────

    suspend fun convertEndurance(request: EnduranceConversionRequest): EnduranceScoreResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParse(
                apiClient.request(StudentEndpoint.ConvertEndurance, request),
                EnduranceScoreResponse::class.java
            )
        }
    }

    // ── New: Exemptions ───────────────────────────────────────────

    suspend fun listExemptions(): List<ExemptionResponse> {
        return withContext(Dispatchers.IO) {
            val physical = runCatching {
                apiClient.executeAndParse(
                    apiClient.request(StudentEndpoint.PhysicalTestExemptions),
                    Array<ExemptionResponse>::class.java
                ).toList()
            }.getOrElse {
                // Shared port 96 still exposes the legacy physical-test path.
                apiClient.executeAndParse(
                    apiClient.request(StudentEndpoint.StudentExemptions),
                    Array<ExemptionResponse>::class.java
                ).toList()
            }
            val checkIn = runCatching {
                apiClient.executeAndParse(
                    apiClient.request(StudentEndpoint.CheckInExemptions),
                    Array<ExemptionResponse>::class.java
                ).toList()
            }.getOrDefault(emptyList())
            (physical + checkIn).sortedByDescending { it.createdAt }
        }
    }

    suspend fun submitExemption(payload: ExemptionApplication): ExemptionSubmitResponse {
        return withContext(Dispatchers.IO) {
            val endpoint = if (payload.type == "team" || payload.type == "club") {
                StudentEndpoint.SubmitCheckInExemption
            } else {
                StudentEndpoint.SubmitPhysicalTestExemption
            }
            apiClient.executeAndParse(
                apiClient.request(endpoint, payload),
                ExemptionSubmitResponse::class.java
            )
        }
    }

    // ── New: Tasks ────────────────────────────────────────────────

    suspend fun listTasks(): StudentTaskListResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParse(
                apiClient.request(StudentEndpoint.StudentTasks),
                StudentTaskListResponse::class.java
            )
        }
    }

    // ── File upload ────────────────────────────────────────────────

    /**
     * Upload proof media to the backend and return COS-backed file metadata.
     *
     * Copies files from [proofAttachments] that have valid local [ProofAttachment.source]
     * URIs to temporary files, then uploads them via multipart POST.
     * Returns signed display URLs together with stable COS keys and media metadata.
     *
     * @param proofAttachments the attachments selected by the user. Only those whose
     *   [ProofAttachment.source] is a readable content:// or file:// URI are used.
     * @param cacheDir the app's cache directory — used for staging temp copies.
     * @return uploaded file metadata on success; empty list if no valid files to upload.
     */
    suspend fun uploadProofFiles(
        proofAttachments: List<ProofAttachment>,
        cacheDir: File
    ): Result<List<UploadedProofFile>> {
        return withContext(Dispatchers.IO) {
            val tempFiles = mutableListOf<File>()
            try {
                for (attachment in proofAttachments) {
                    if (!attachment.isValidForUpload) continue
                    val uri = try {
                        android.net.Uri.parse(attachment.source)
                    } catch (_: Exception) {
                        continue
                    }
                    if (uri.scheme == null) continue

                    // Copy from content:// or file:// URI to a temp file
                    val ext = attachment.fileName.substringAfterLast('.', "jpg").take(5)
                    val tempFile = File.createTempFile("proof_", ".$ext", cacheDir)
                    try {
                        val inputStream = when {
                            uri.scheme == "file" -> java.io.FileInputStream(java.io.File(uri.path ?: continue))
                            else -> androidAppContext()?.contentResolver?.openInputStream(uri)
                                ?: continue
                        }
                        inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFiles.add(tempFile)
                    } catch (_: Exception) {
                        tempFile.delete()
                        continue
                    }
                }

                if (tempFiles.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                val response = apiClient.uploadProofFiles(tempFiles)

                Result.success(response.files)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempFiles.forEach { it.delete() }
            }
        }
    }

    // ── New: Profile ──────────────────────────────────────────────

    suspend fun fetchProfile(): StudentProfileResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParse(
                apiClient.request(StudentEndpoint.StudentProfile),
                StudentProfileResponse::class.java
            )
        }
    }

    suspend fun updateProfile(payload: StudentProfileUpdateRequest): StudentProfileResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParse(
                apiClient.request(StudentEndpoint.UpdateStudentProfile, payload),
                StudentProfileResponse::class.java
            )
        }
    }

    // ── Context access for content:// URIs ─────────────────────────

    companion object {
        @Volatile
        private var _appContext: android.content.Context? = null

        /** Initialize with the Application context. Call once from Application.onCreate(). */
        fun initContext(context: android.content.Context) {
            _appContext = context.applicationContext
        }

        @JvmStatic
        fun androidAppContext(): android.content.Context? = _appContext
    }
}
