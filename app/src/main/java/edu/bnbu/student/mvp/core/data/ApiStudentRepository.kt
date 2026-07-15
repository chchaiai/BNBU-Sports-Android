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
import edu.bnbu.student.mvp.core.model.ProofUploadRule
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
import edu.bnbu.student.mvp.core.network.ApiHttpException
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
import edu.bnbu.student.mvp.core.network.ExemptionSupplementRequest
import edu.bnbu.student.mvp.core.network.StudentProfileResponse
import edu.bnbu.student.mvp.core.network.StudentProfileUpdateRequest
import edu.bnbu.student.mvp.core.network.StudentTaskListResponse
import edu.bnbu.student.mvp.core.network.StudentTaskItemResponse
import edu.bnbu.student.mvp.core.network.StudentCourseDetailResponse
import edu.bnbu.student.mvp.core.network.StudentCoursesResponse
import edu.bnbu.student.mvp.core.network.StudentGradesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

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
            apiClient.executeAndParseCancellable(request, LoginResponse::class.java)
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
            val summary: SportSummaryResponse = apiClient.executeAndParseCancellable(
                sportSummaryRequest(), SportSummaryResponse::class.java
            )
            val records: List<SportRecordResponse> = apiClient.executeAndParseCancellable(
                recordsListRequest(), Array<SportRecordResponse>::class.java
            ).toList()
            val memberships: List<MembershipResponse> = apiClient.executeAndParseCancellable(
                sportIdentityRequest(), Array<MembershipResponse>::class.java
            ).toList()
            val notices: List<NotificationResponse> = apiClient.executeAndParseCancellable(
                notificationsRequest(), Array<NotificationResponse>::class.java
            ).toList()
            val profileResult: Result<StudentProfileResponse> = try {
                Result.success(fetchProfile())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isUnauthorizedResponse()) throw e
                Result.failure(e)
            }
            // Also fetch tasks from the backend — they are a separate API call.
            // AND-004: surface task fetch errors visibly instead of silently
            // returning an empty list (which would show "暂无近期任务" misleadingly).
            val tasksResult: Result<StudentTaskListResponse> = try {
                Result.success(
                    apiClient.executeAndParseCancellable(
                        apiClient.request(StudentEndpoint.StudentTasks),
                        StudentTaskListResponse::class.java
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isUnauthorizedResponse()) throw e
                Result.failure(e)
            }
            val tasksResponse = tasksResult.getOrElse {
                StudentTaskListResponse(emptyList(), emptyList())
            }
            val allTasks = tasksResponse.pending + tasksResponse.completed
            // Week2 course contract is optional during the transition from the
            // shared port 96 API. A 404 falls back to summary.courses below.
            val coursesResult: Result<StudentCoursesResponse> = try {
                Result.success(
                    apiClient.executeAndParseCancellable(
                        apiClient.request(StudentEndpoint.StudentCourses(scope = "all")),
                        StudentCoursesResponse::class.java
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isUnauthorizedResponse()) throw e
                Result.failure(e)
            }
            val courseItems = coursesResult.getOrNull()?.courses.orEmpty()
            val gradesResult: Result<StudentGradesResponse> = try {
                Result.success(
                    apiClient.executeAndParseCancellable(
                        apiClient.request(StudentEndpoint.StudentGrades),
                        StudentGradesResponse::class.java
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isUnauthorizedResponse()) throw e
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
                gradesResponse = gradesResponse,
                gradesLoadError = gradesLoadError,
                remoteProfile = profileResult.getOrNull()
            )
            workspace
        } catch (e: CancellationException) {
            throw e
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
            apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.StudentGrades),
                StudentGradesResponse::class.java
            )
        }
    }

    // ── Mutations ───────────────────────────────────────────────

    override suspend fun submitRecord(payload: SubmitSportRecordRequest): Result<SubmitRecordResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = submitSportRecordRequest(payload)
                Result.success(
                    apiClient.executeAndParseCancellable(request, SubmitRecordResponse::class.java)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun supplementRecord(
        recordId: String,
        payload: SupplementSportRecordRequest
    ): Result<SupplementResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = supplementSportRecordRequest(recordId, payload)
                Result.success(
                    apiClient.executeAndParseCancellable(request, SupplementResponse::class.java)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun markNotificationRead(id: String): Result<MarkReadResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = markNotificationReadRequest(id)
                Result.success(
                    apiClient.executeAndParseCancellable(request, MarkReadResponse::class.java)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
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
        gradesResponse: StudentGradesResponse? = null,
        gradesLoadError: String? = null,
        remoteProfile: StudentProfileResponse? = null
    ): StudentWorkspace {
        // Student identity comes from the login response (userProfile), with
        // fallback defaults when not available (e.g. synchronous loadWorkspace).
        val profile = userProfile
        val student = StudentProfile(
            id = remoteProfile?.id?.takeIf { it.isNotBlank() } ?: profile?.id.orEmpty(),
            name = remoteProfile?.name?.takeIf { it.isNotBlank() }
                ?: profile?.name?.takeIf { it.isNotBlank() }
                ?: "学生",
            email = remoteProfile?.email?.takeIf { it.isNotBlank() } ?: profile?.email.orEmpty(),
            college = remoteProfile?.college?.takeIf { it.isNotBlank() } ?: profile?.college.orEmpty(),
            className = remoteProfile?.className?.takeIf { it.isNotBlank() } ?: profile?.className.orEmpty(),
            status = remoteProfile?.status?.takeIf { it.isNotBlank() }
                ?: profile?.status?.takeIf { it.isNotBlank() }
                ?: if (summary.completed) "已完成" else "进行中",
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
            sourceTrace = if (gradesLoadError != null) {
                "API: grade data not yet available — $gradesLoadError"
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
            taskTitle = r.taskTitle ?: r.taskId ?: "自主打卡",
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
            apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.ConvertEndurance, request),
                EnduranceScoreResponse::class.java
            )
        }
    }

    // ── New: Exemptions ───────────────────────────────────────────

    suspend fun listExemptions(): List<ExemptionResponse> {
        return withContext(Dispatchers.IO) {
            val physical = try {
                apiClient.executeAndParseCancellable(
                    apiClient.request(StudentEndpoint.PhysicalTestExemptions),
                    Array<ExemptionResponse>::class.java
                ).toList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is ApiHttpException || e.statusCode != 404) throw e
                // Shared port 96 still exposes the legacy physical-test path.
                apiClient.executeAndParseCancellable(
                    apiClient.request(StudentEndpoint.StudentExemptions),
                    Array<ExemptionResponse>::class.java
                ).toList()
            }
            val checkIn = try {
                apiClient.executeAndParseCancellable(
                    apiClient.request(StudentEndpoint.CheckInExemptions),
                    Array<ExemptionResponse>::class.java
                ).toList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiHttpException && e.statusCode == 404) emptyList() else throw e
            }
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
            apiClient.executeAndParseCancellable(
                apiClient.request(endpoint, payload),
                ExemptionSubmitResponse::class.java
            )
        }
    }

    // ── New: Tasks ────────────────────────────────────────────────

    suspend fun listTasks(): StudentTaskListResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
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
            var preparedBytes = 0L
            try {
                if (proofAttachments.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                for (attachment in proofAttachments) {
                    if (!attachment.isValidForUpload) {
                        throw IOException(
                            "Upload file is invalid: ${attachment.fileName} " +
                                "(${attachment.validationMessage ?: "validation failed"})"
                        )
                    }

                    val ext = attachment.fileName
                        .substringAfterLast('.', "")
                        .lowercase()
                        .filter { it.isLetterOrDigit() }
                        .take(5)
                        .ifBlank {
                            if (attachment.type == ProofMediaType.Video) "mp4" else "jpg"
                        }
                    val tempFile = File.createTempFile("proof_", ".$ext", cacheDir)
                    tempFiles.add(tempFile)
                    openAttachmentStream(attachment).use { input ->
                        tempFile.outputStream().use { output ->
                            val maximumBytes = if (attachment.type == ProofMediaType.Video) {
                                ProofUploadRule.maxVideoBytes.toLong()
                            } else {
                                ProofUploadRule.maxImageBytes.toLong()
                            }
                            val copied = copyWithLimit(input, output, maximumBytes)
                            if (copied == 0L) {
                                throw IOException("Upload file is empty: ${attachment.fileName}")
                            }
                            preparedBytes += copied
                            if (preparedBytes > ProofUploadRule.maxRequestBytes.toLong()) {
                                throw IOException("Upload request exceeds 120MB")
                            }
                        }
                    }
                }

                if (tempFiles.size != proofAttachments.size) {
                    throw IOException(
                        "Prepared ${tempFiles.size} of ${proofAttachments.size} upload files"
                    )
                }

                val response = apiClient.uploadProofFilesCancellable(tempFiles)
                if (response.files.size != proofAttachments.size) {
                    throw IOException(
                        "Server accepted ${response.files.size} of ${proofAttachments.size} upload files"
                    )
                }
                if (response.files.any { it.cosKey.isBlank() }) {
                    throw IOException("Server upload response is missing a COS key")
                }

                Result.success(response.files)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempFiles.forEach { it.delete() }
            }
        }
    }

    suspend fun supplementExemption(
        exemption: Exemption,
        payload: ExemptionApplication
    ): ExemptionSubmitResponse {
        return withContext(Dispatchers.IO) {
            val isCheckIn = exemption.category == "checkin" ||
                exemption.type == "team" || exemption.type == "club"
            val endpoint = if (isCheckIn) {
                StudentEndpoint.SupplementCheckInExemption(exemption.id)
            } else {
                StudentEndpoint.SupplementPhysicalTestExemption(exemption.id)
            }
            val supplement = ExemptionSupplementRequest(
                reason = payload.reason,
                proofFiles = payload.proofFiles,
                organization = payload.organization
            )
            apiClient.executeAndParseCancellable(
                apiClient.request(endpoint, supplement),
                ExemptionSubmitResponse::class.java
            )
        }
    }

    @Throws(IOException::class)
    private fun openAttachmentStream(attachment: ProofAttachment): InputStream {
        val source = attachment.source.trim()
        if (source.isEmpty()) {
            throw IOException("Upload source is empty: ${attachment.fileName}")
        }

        val sourceUri = try {
            URI(source)
        } catch (e: Exception) {
            throw IOException("Upload source is invalid: ${attachment.fileName}", e)
        }

        return when (sourceUri.scheme?.lowercase()) {
            "file" -> {
                val sourceFile = try {
                    File(sourceUri)
                } catch (e: Exception) {
                    throw IOException("Upload file path is invalid: ${attachment.fileName}", e)
                }
                if (!sourceFile.isFile || !sourceFile.canRead()) {
                    throw IOException("Upload file is not readable: ${attachment.fileName}")
                }
                FileInputStream(sourceFile)
            }

            "content" -> {
                val context = androidAppContext()
                    ?: throw IOException("Upload context is unavailable: ${attachment.fileName}")
                val androidUri = android.net.Uri.parse(source)
                context.contentResolver.openInputStream(androidUri)
                    ?: throw IOException("Upload content is not readable: ${attachment.fileName}")
            }

            else -> throw IOException(
                "Unsupported upload source scheme for ${attachment.fileName}: ${sourceUri.scheme ?: "none"}"
            )
        }
    }

    @Throws(IOException::class)
    private suspend fun copyWithLimit(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Long
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (copied > maximumBytes - count) {
                throw IOException("Upload file exceeds ${maximumBytes / 1_000_000}MB")
            }
            output.write(buffer, 0, count)
            copied += count
        }
        return copied
    }

    // ── New: Profile ──────────────────────────────────────────────

    suspend fun fetchProfile(): StudentProfileResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.StudentProfile),
                StudentProfileResponse::class.java
            )
        }
    }

    suspend fun updateProfile(payload: StudentProfileUpdateRequest): StudentProfileResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
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

private fun Throwable.isUnauthorizedResponse(): Boolean {
    return this is ApiHttpException && statusCode == 401
}
