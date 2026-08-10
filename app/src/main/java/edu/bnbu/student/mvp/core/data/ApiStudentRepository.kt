package edu.bnbu.student.mvp.core.data

import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.CheckInTimeWindow
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.EnduranceConversionRequest
import edu.bnbu.student.mvp.core.model.EnduranceScoreResult
import edu.bnbu.student.mvp.core.model.EnduranceRunStatus
import edu.bnbu.student.mvp.core.model.Exemption
import edu.bnbu.student.mvp.core.model.ExemptionApplication
import edu.bnbu.student.mvp.core.model.GradeBlock
import edu.bnbu.student.mvp.core.model.GradeRow
import edu.bnbu.student.mvp.core.model.GradeSubItem
import edu.bnbu.student.mvp.core.model.Membership
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ProofUploadRule
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.model.StudentProgress
import edu.bnbu.student.mvp.core.model.StudentProfile
import edu.bnbu.student.mvp.core.model.SportHourRule
import edu.bnbu.student.mvp.core.model.TeacherInfo
import edu.bnbu.student.mvp.core.model.StudentWorkspace
import edu.bnbu.student.mvp.core.model.AppLanguage
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
import edu.bnbu.student.mvp.core.network.UploadProofResponse
import edu.bnbu.student.mvp.core.network.UploadedProofFile
import edu.bnbu.student.mvp.core.network.UploadProgress
import edu.bnbu.student.mvp.core.network.UserDto
import edu.bnbu.student.mvp.core.network.EnduranceScoreResponse
import edu.bnbu.student.mvp.core.network.ExemptionResponse
import edu.bnbu.student.mvp.core.network.ExemptionSubmitResponse
import edu.bnbu.student.mvp.core.network.ExemptionSupplementRequest
import edu.bnbu.student.mvp.core.network.StudentProfileResponse
import edu.bnbu.student.mvp.core.network.StudentProfileUpdateRequest
import edu.bnbu.student.mvp.core.network.StudentCourseDetailResponse
import edu.bnbu.student.mvp.core.network.StudentCoursesResponse
import edu.bnbu.student.mvp.core.network.CheckInTimeWindowResponse
import edu.bnbu.student.mvp.core.network.StudentGradesResponse
import edu.bnbu.student.mvp.core.network.SendEmailContactCodeRequest
import edu.bnbu.student.mvp.core.network.VerifyEmailContactCodeRequest
import edu.bnbu.student.mvp.core.network.SendPhoneContactCodeRequest
import edu.bnbu.student.mvp.core.network.VerifyPhoneContactCodeRequest
import edu.bnbu.student.mvp.core.network.FeedbackTicketListResponse
import edu.bnbu.student.mvp.core.network.FeedbackTicketResponse
import edu.bnbu.student.mvp.core.network.HelpArticleResponse
import edu.bnbu.student.mvp.core.network.SubmitFeedbackRequest
import edu.bnbu.student.mvp.core.network.LanguagePreferenceResponse
import edu.bnbu.student.mvp.core.network.UpdateLanguagePreferenceRequest
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
        return StudentWorkspace.empty()
    }

    /** Fetches only the current server-authoritative check-in admission policy. */
    suspend fun fetchCheckInTimeWindow(): CheckInTimeWindow = withContext(Dispatchers.IO) {
        apiClient.executeAndParseCancellable(
            apiClient.request(StudentEndpoint.CheckInTimeWindow),
            CheckInTimeWindowResponse::class.java
        ).toDomain()
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
            val checkInTimeWindow = fetchCheckInTimeWindow()
            val profileResult: Result<StudentProfileResponse> = try {
                Result.success(fetchProfile())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isUnauthorizedResponse()) throw e
                Result.failure(e)
            }
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
                gradesResponse = gradesResponse,
                gradesLoadError = gradesLoadError,
                remoteProfile = profileResult.getOrNull(),
                checkInTimeWindow = checkInTimeWindow
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
        gradesResponse: StudentGradesResponse? = null,
        gradesLoadError: String? = null,
        remoteProfile: StudentProfileResponse? = null,
        checkInTimeWindow: CheckInTimeWindow = CheckInTimeWindow.unavailable()
    ): StudentWorkspace {
        // Student identity comes from the login response (userProfile), with
        // fallback defaults when not available (e.g. synchronous loadWorkspace).
        val profile = userProfile
        val student = StudentProfile(
            id = remoteProfile?.id?.takeIf { it.isNotBlank() } ?: profile?.id.orEmpty(),
            name = remoteProfile?.name?.takeIf { it.isNotBlank() }
                ?: profile?.name?.takeIf { it.isNotBlank() }
                ?: "学生",
            studentNumber = remoteProfile?.studentNumber?.takeIf { it.isNotBlank() }
                ?: profile?.studentNumber.orEmpty(),
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
            gradeCalculatedAt = remoteProfile?.gradeCalculatedAt.orEmpty(),
            accountStatus = remoteProfile?.accountStatus?.takeIf { it.isNotBlank() }
                ?: profile?.accountStatus?.takeIf { it.isNotBlank() }
                ?: "ACTIVE"
        )

        val orgCredit = memberships.firstOrNull { it.status == "认证有效" && it.offset == "可抵扣" }

        val progress = StudentProgress(
            id = student.id,
            name = student.name,
            college = student.college,
            className = student.className,
            course = summary.courseHours,
            general = summary.generalHours,
            // The current summary endpoint exposes only totals. This fallback keeps the
            // breakdown honest until its raw course/general fields are available.
            rawCourse = summary.courseHours,
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
                    completion = 0,
                    missing = 0,
                    deadline = c.semester.endDate.orEmpty(),
                    teacher = c.teacherName,
                    teacherId = c.teacherId,
                    semesterId = c.semester.id,
                    academicYear = c.semester.academicYear,
                    term = c.semester.term,
                    semesterStatus = c.semester.status,
                    status = c.status,
                    enrollmentStatus = c.enrollmentStatus,
                    isCurrent = c.isCurrent,
                    finalGrade = c.finalGrade,
                    gradeStatus = c.gradeStatus
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
                    completion = 0,
                    missing = 0,
                    deadline = "",
                    teacher = c.teacherName,
                    teacherId = c.teacherId,
                    // Summary responses do not carry course lifecycle state, so they
                    // cannot authorize check-in when /student/courses is unavailable.
                    status = "unavailable",
                    isCurrent = true
                )
            }
        }

        // Teachers are returned directly from the summary API
        val teachers: List<TeacherInfo> = summary.teachers.map { t ->
            TeacherInfo(teacherId = t.teacherId, teacherName = t.teacherName)
        }

        // Grade scores are managed by teacher/admin endpoints. Prefer configured
        // blocks, while retaining the legacy flat check-in/physical fields used
        // by the current student API.
        val studentGrade = gradesResponse?.grades
            ?.firstOrNull { it.studentId == student.id }
            ?: gradesResponse?.grades?.firstOrNull()

        val grades = if (studentGrade != null) {
            val configuredBlocks = studentGrade.visibleBlocks.map { block ->
                GradeBlock(
                    id = block.id,
                    name = block.name,
                    weight = block.weight,
                    score = block.score,
                    scoreDisplay = block.scoreDisplay,
                    isVisible = block.isVisible,
                    displayOrder = block.displayOrder,
                    blockType = block.blockType,
                    description = block.description,
                    subItems = block.subItems?.map { subItem ->
                        GradeSubItem(
                            name = subItem.name,
                            score = subItem.score,
                            scoreDisplay = subItem.scoreDisplay
                        )
                    }
                )
            }
            val configuredIdentity = configuredBlocks.filter(GradeBlock::isVisible).joinToString(" ") {
                "${it.id} ${it.name} ${it.blockType}"
            }.lowercase()
            val hasConfiguredCheckIn = listOf("checkin", "check_in", "打卡", "学时")
                .any(configuredIdentity::contains)
            val hasConfiguredEndurance = listOf(
                "physical",
                "endurance",
                "800m",
                "800米",
                "1000m",
                "1000米",
                "耐力跑",
                "体测"
            ).any(configuredIdentity::contains)
            val legacyFocusedBlocks = buildList {
                if (!hasConfiguredEndurance) {
                    val distance = when (student.gender) {
                        "male" -> "1000 米"
                        "female" -> "800 米"
                        else -> "800 / 1000 米"
                    }
                    add(
                        GradeBlock(
                            id = "physical",
                            name = "$distance 跑步",
                            weight = 0.0,
                            score = studentGrade.physical,
                            scoreDisplay = studentGrade.physical.toString(),
                            isVisible = true,
                            displayOrder = 10,
                            blockType = "physical_test",
                            description = "耐力跑测试成绩",
                            subItems = null
                        )
                    )
                }
                if (!hasConfiguredCheckIn) {
                    add(
                        GradeBlock(
                            id = "checkin",
                            name = "打卡成绩",
                            weight = 0.0,
                            score = studentGrade.resolvedCheckinScore,
                            scoreDisplay = studentGrade.resolvedCheckinScore.toString(),
                            isVisible = true,
                            displayOrder = 20,
                            blockType = "checkin",
                            description = "根据有效运动打卡换算",
                            subItems = null
                        )
                    )
                }
            }
            GradeRow(
                studentId = studentGrade.studentId,
                studentName = studentGrade.studentName,
                visibleBlocks = configuredBlocks + legacyFocusedBlocks,
                totalScore = studentGrade.totalScore,
                totalDisplay = studentGrade.totalDisplay,
                isPassed = studentGrade.isPassed,
                courseGradeStatus = studentGrade.courseGradeStatus,
                displayConfigVersion = studentGrade.displayConfigVersion,
                sourceTrace = studentGrade.sourceTrace.orEmpty().ifBlank { "API: /student/grades" },
                enduranceRunTimeSeconds = studentGrade.enduranceRunTimeSeconds,
                enduranceRunStatus = EnduranceRunStatus.fromApi(
                    studentGrade.enduranceRunStatus,
                    studentGrade.enduranceRunTimeSeconds
                ),
                enduranceRunScore = studentGrade.enduranceRunScore
            )
        } else GradeRow(
            studentId = student.id,
            studentName = student.name,
            visibleBlocks = emptyList(),
            totalScore = null,
            totalDisplay = "未开放",
            isPassed = null,
            courseGradeStatus = "rules_not_published",
            displayConfigVersion = 0,
            sourceTrace = if (gradesLoadError != null) {
                "API: grade data not yet available — $gradesLoadError"
            } else {
                "API: grade data not yet available from summary endpoint"
            }
        )

        return StudentWorkspace(
            student = student,
            courses = courses,
            progress = progress,
            hourRule = summary.toSportHourRule(),
            records = records.map { recordResponseToRecord(it) },
            grades = grades,
            memberships = memberships.map { membershipToMembership(it) },
            notices = notices.map { noticeResponseToNotice(it) },
            teachers = teachers,
            checkInTimeWindow = checkInTimeWindow
        )
    }

    /**
     * Hour targets are teacher-configured and must come from the summary API.
     * The standard rule is only a compatibility fallback for older servers that
     * do not return the optional `rule` object.
     */
    private fun SportSummaryResponse.toSportHourRule(): SportHourRule {
        val serverRule = rule ?: return SportHourRule.Standard
        return SportHourRule(
            total = serverRule.total,
            courseRequired = serverRule.courseRequired,
            generalRequired = serverRule.generalRequired,
            dailyLimit = serverRule.dailyLimit
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
        val creditType = when (r.creditType) {
            "课程相关" -> CreditType.CourseRelated
            "其他运动" -> CreditType.General
            "系统抵扣" -> CreditType.OrganizationOffset
            else -> CreditType.General
        }
        return CheckInRecord(
            id = r.id,
            courseId = r.courseId,
            taskTitle = r.taskTitle ?: "运动打卡",
            creditType = creditType,
            hours = r.hours,
            submittedAt = r.submittedAt ?: "",
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
            teacherPublicFeedback = r.teacherPublicFeedback,
            teacherInternalNote = r.teacherInternalNote,
            note = r.description ?: "",
            remark = r.remark ?: "",
            sportType = r.sportType,
            startTime = r.startTime,
            endTime = r.endTime,
            actualDurationSeconds = r.actualDurationSeconds
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
        cacheDir: File,
        onProgress: (UploadProgress) -> Unit = {}
    ): Result<List<UploadedProofFile>> {
        return withContext(Dispatchers.IO) {
            val tempFiles = mutableListOf<File>()
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
                            // This legacy multipart helper remains only for image-only
                            // feedback and exemption attachments. Exercise video uses
                            // the private /api/v1 media lifecycle instead.
                            require(attachment.type == ProofMediaType.Image) {
                                "Exercise video must use the private media upload flow"
                            }
                            val maximumBytes = ProofUploadRule.maxImageBytes.toLong()
                            val copied = copyWithLimit(input, output, maximumBytes)
                            if (copied == 0L) {
                                throw IOException("Upload file is empty: ${attachment.fileName}")
                            }
                        }
                    }
                }

                if (tempFiles.size != proofAttachments.size) {
                    throw IOException(
                        "Prepared ${tempFiles.size} of ${proofAttachments.size} upload files"
                    )
                }

                val response = apiClient.uploadProofFilesCancellable(tempFiles, onProgress)
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

    /**
     * Stores the student's UI language on the backend so email and other
     * server-originated communication can use the same language.
     */
    suspend fun updateLanguagePreference(language: AppLanguage): LanguagePreferenceResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(
                    StudentEndpoint.UpdateLanguagePreference,
                    UpdateLanguagePreferenceRequest(language.languageTag)
                ),
                LanguagePreferenceResponse::class.java
            )
        }
    }

    /** Loads only the articles currently published by an administrator. */
    suspend fun fetchHelpArticles(): List<HelpArticleResponse> {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.HelpArticles),
                Array<HelpArticleResponse>::class.java
            ).toList()
        }
    }

    // Feedback API contract is isolated here while the backend endpoint is being finalized.
    suspend fun submitFeedback(payload: SubmitFeedbackRequest): FeedbackTicketResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.SubmitFeedback, payload),
                FeedbackTicketResponse::class.java
            )
        }
    }

    suspend fun listFeedbackTickets(): List<FeedbackTicketResponse> {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.FeedbackTickets),
                FeedbackTicketListResponse::class.java
            ).tickets
        }
    }

    suspend fun sendEmailContactCode(email: String) {
        withContext(Dispatchers.IO) {
            apiClient.executeCancellable(
                apiClient.request(StudentEndpoint.SendEmailContactCode, SendEmailContactCodeRequest(email))
            )
        }
    }

    suspend fun verifyEmailContactCode(email: String, code: String): StudentProfileResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(
                    StudentEndpoint.VerifyEmailContactCode,
                    VerifyEmailContactCodeRequest(email, code)
                ),
                StudentProfileResponse::class.java
            )
        }
    }

    suspend fun sendPhoneContactCode(phone: String) {
        withContext(Dispatchers.IO) {
            apiClient.executeCancellable(
                apiClient.request(StudentEndpoint.SendPhoneContactCode, SendPhoneContactCodeRequest(phone))
            )
        }
    }

    suspend fun verifyPhoneContactCode(phone: String, code: String): StudentProfileResponse {
        return withContext(Dispatchers.IO) {
            apiClient.executeAndParseCancellable(
                apiClient.request(
                    StudentEndpoint.VerifyPhoneContactCode,
                    VerifyPhoneContactCodeRequest(phone, code)
                ),
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

private fun CheckInTimeWindowResponse.toDomain(): CheckInTimeWindow = CheckInTimeWindow(
    windowMode = windowMode,
    dateRangeStart = dateRangeStart,
    dateRangeEnd = dateRangeEnd,
    dailyStartTime = dailyStartTime,
    dailyEndTime = dailyEndTime,
    excludedDates = excludedDates,
    semesterDeadline = semesterDeadline
)
