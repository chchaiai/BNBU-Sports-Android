package edu.bnbu.student.mvp.core.model

enum class AppThemeMode(val label: String, val storageValue: String) {
    Light("浅色", "light"),
    Dark("深色", "dark"),
    System("跟随系统", "system");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: Light
        }
    }
}

data class StudentProfile(
    val id: String,
    val name: String,
    /** Stable campus identifier used when a student directly joins a course. */
    val studentNumber: String = "",
    val email: String,
    val college: String,
    val className: String,
    val status: String,
    val gender: String = "",
    val gradeLevel: String = "",
    val admissionYear: Int? = null,
    val currentAcademicYear: String = "",
    val gradeCalculatedAt: String = "",
    /** Server-side account lifecycle state, e.g. PENDING_CONTACT_BINDING or ACTIVE. */
    val accountStatus: String = AccountStatus.ACTIVE.name
) {
    val genderLabel: String
        get() = when (gender) {
            "male" -> "男"
            "female" -> "女"
            else -> gender
        }

    val gradeLabel: String
        get() = when (gradeLevel) {
            "freshman" -> "大一"
            "sophomore" -> "大二"
            "junior" -> "大三"
            "senior" -> "大四"
            else -> gradeLevel
        }

    val gradeGroup: String
        get() = when (gradeLevel) {
            "freshman", "sophomore" -> "freshman_sophomore"
            "junior", "senior" -> "junior_senior"
            else -> ""
        }
}

enum class AccountStatus {
    PENDING_CONTACT_BINDING,
    ACTIVE;

    companion object {
        fun from(value: String?): AccountStatus = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        } ?: PENDING_CONTACT_BINDING

        fun requireKnown(value: String?): AccountStatus = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("ACCOUNT_STATUS_UNSUPPORTED")
    }
}

data class TeacherInfo(
    val teacherId: String,
    val teacherName: String
)

data class StudentWorkspace(
    val student: StudentProfile,
    val courses: List<Course>,
    val progress: StudentProgress,
    /** Server-owned hour targets for the student's current course and term. */
    val hourRule: SportHourRule = SportHourRule.Standard,
    val records: List<CheckInRecord>,
    val grades: GradeRow,
    val memberships: List<Membership>,
    val notices: List<StudentNotice>,
    val teachers: List<TeacherInfo> = emptyList(),
    val syncOperations: List<SyncOperation> = emptyList(),
    val exemptions: List<Exemption> = emptyList(),
    /** Server-owned policy used to decide whether a new exercise session may start. */
    val checkInTimeWindow: CheckInTimeWindow = CheckInTimeWindow.unavailable()
) {
    companion object {
        fun empty(): StudentWorkspace = StudentWorkspace(
            student = StudentProfile(id = "", name = "", email = "", college = "", className = "", status = "未登录"),
            courses = emptyList(),
            progress = StudentProgress(id = "", name = "", college = "", className = "", course = 0.0, general = 0.0, rawCourse = 0.0, rawGeneral = 0.0, exam = 0, attendance = 0, physical = 0, status = "请先登录", source = "empty", organizationCredit = null),
            records = emptyList(),
            grades = GradeRow(
                studentId = "",
                studentName = "",
                visibleBlocks = emptyList(),
                totalScore = null,
                totalDisplay = "未开放",
                isPassed = null,
                courseGradeStatus = "rules_not_published",
                displayConfigVersion = 0,
                sourceTrace = ""
            ),
            memberships = emptyList(),
            notices = emptyList(),
            teachers = emptyList(),
            syncOperations = emptyList(),
            exemptions = emptyList(),
            checkInTimeWindow = CheckInTimeWindow.unavailable()
        )
    }
}

enum class SyncOperationType(val label: String) {
    SubmitRecord("提交打卡"),
    MarkNoticeRead("通知已读"),
    ResetLocalData("重置数据")
}

enum class SyncOperationStatus(val label: String) {
    Queued("待同步"),
    LocalOnly("本地完成"),
    Synced("已同步")
}

data class SyncOperation(
    val id: String,
    val type: SyncOperationType,
    val title: String,
    val detail: String,
    val createdAt: String,
    val status: SyncOperationStatus
)

data class Course(
    val id: String,
    val code: String,
    val section: String,
    val name: String,
    val semester: String,
    val students: Int,
    val completion: Int,
    val missing: Int,
    val deadline: String,
    val teacher: String,
    val teacherId: String = "",
    val semesterId: String = "",
    val academicYear: String = "",
    val term: String = "",
    val semesterStatus: String = "current",
    /** Course lifecycle state supplied by the server, for example "active" or "closed". */
    val status: String = "active",
    val enrollmentStatus: String = "enrolled",
    val isCurrent: Boolean = true,
    /** Final result supplied for an archived course; absent until the backend publishes it. */
    val finalGrade: Int? = null,
    /** Backend result status for an archived course: "pass", "fail", or absent. */
    val gradeStatus: String? = null
) {
    val displayTitle: String
        get() = "$code / Section $section"

    val isOpenForCheckIn: Boolean
        get() = status.trim().lowercase() in setOf("active", "open", "enabled")

    /** The backend may use either the legacy `enrolled` value or the new `active` value. */
    val hasActiveMembership: Boolean
        get() = enrollmentStatus.trim().lowercase() in setOf("active", "enrolled")
}

/**
 * A student may hold only one active course membership in the current semester.
 * The server remains authoritative and repeats this check atomically at join time.
 */
fun StudentWorkspace.canStartNewCourseJoin(): Boolean =
    courses.none { it.isCurrent && it.hasActiveMembership }

data class StudentProgress(
    val id: String,
    val name: String,
    val college: String,
    val className: String,
    val course: Double,
    val general: Double,
    /** Course-related check-in hours before organization offsets are applied. */
    val rawCourse: Double,
    val rawGeneral: Double,
    val exam: Int,
    val attendance: Int,
    val physical: Int,
    val status: String,
    val source: String,
    val organizationCredit: Membership?
)

/**
 * Applies a newly accepted exercise check-in to the local progress snapshot.
 * Organization offsets are managed separately and must not be changed by a
 * student check-in submission.
 */
fun StudentProgress.withRecordedCheckIn(
    creditType: CreditType,
    hours: Double
): StudentProgress {
    val recordedHours = hours.coerceAtLeast(0.0)
    return when (creditType) {
        CreditType.CourseRelated -> copy(
            course = course + recordedHours,
            rawCourse = rawCourse + recordedHours
        )
        CreditType.General -> copy(
            general = general + recordedHours,
            rawGeneral = rawGeneral + recordedHours
        )
        CreditType.OrganizationOffset -> this
    }
}

enum class CreditType(val label: String) {
    CourseRelated("课程相关"),
    General("其他运动"),
    OrganizationOffset("系统抵扣")
}

data class CheckInRecord(
    val id: String,
    val courseId: String?,
    val taskTitle: String,
    val creditType: CreditType,
    val hours: Double,
    val submittedAt: String,
    val proofSummary: String,
    val proofPhotoCount: Int,
    val proofVideoCount: Int,
    val proofFiles: List<ProofAttachment>,
    val teacherPublicFeedback: String?,
    val teacherInternalNote: String?,
    val note: String,
    val sportType: String? = null,
    /** ISO-8601 timestamps and active duration captured by the exercise session. */
    val startTime: String? = null,
    val endTime: String? = null,
    val actualDurationSeconds: Long? = null
)

/** The period in which a student may start an exercise check-in session. */
data class CheckInTimeWindow(
    val windowMode: String,
    val dateRangeStart: String?,
    val dateRangeEnd: String?,
    val dailyStartTime: String,
    val dailyEndTime: String,
    val excludedDates: List<String>,
    val semesterDeadline: String?
) {
    companion object {
        /** Blocks new sessions until the server has supplied an authoritative policy. */
        fun unavailable() = CheckInTimeWindow(
            windowMode = "unavailable",
            dateRangeStart = null,
            dateRangeEnd = null,
            dailyStartTime = "",
            dailyEndTime = "",
            excludedDates = emptyList(),
            semesterDeadline = null
        )

        /** @deprecated The app must obtain its policy from the server. */
        @Deprecated("Use unavailable() until the server policy is loaded")
        fun default() = unavailable()
    }
}

enum class ProofMediaType(val label: String) {
    Image("图片"),
    Video("视频")
}

object ProofUploadRule {
    const val maxImageCount = 6
    const val maxVideoCount = 1
    const val maxAttachmentCount = maxImageCount + maxVideoCount
    const val maxImageBytes = 10 * 1_024 * 1_024
    const val maxVideoDurationSeconds = 15.0

    val summaryText: String
        get() = "最多 $maxImageCount 张照片（每张不超过 10 MiB），最多 $maxVideoCount 个现场视频（累计录制不超过 15 秒）。"

    fun limitMessage(proofs: List<ProofAttachment>): String? {
        val imageCount = proofs.count { it.type == ProofMediaType.Image }
        val videoCount = proofs.count { it.type == ProofMediaType.Video }
        return when {
            imageCount > maxImageCount -> "同一条记录最多上传 $maxImageCount 张照片。"
            videoCount > maxVideoCount -> "同一条记录最多上传 $maxVideoCount 个视频。"
            else -> null
        }
    }
}

data class ProofAttachment(
    val id: String,
    val type: ProofMediaType,
    val fileName: String,
    val byteCount: Long?,
    val durationSeconds: Double? = null,
    val thumbnailBytes: ByteArray? = null,
    val source: String
) {
    val displaySize: String
        get() {
            val bytes = byteCount ?: return "本地文件"
            return if (bytes >= 1_000_000) {
                "%.1f MB".format(bytes / 1_000_000.0)
            } else {
                "${maxOf(bytes / 1_000, 1)} KB"
            }
        }

    val displayDuration: String?
        get() {
            val seconds = durationSeconds?.let { maxOf(it.toInt(), 0) } ?: return null
            return if (seconds >= 60) {
                "${seconds / 60}分${seconds % 60}秒"
            } else {
                "${seconds}秒"
            }
        }

    val validationMessage: String?
        get() {
            val bytes = byteCount
            if (bytes != null) {
                if (type == ProofMediaType.Image && bytes > ProofUploadRule.maxImageBytes) {
                    return "图片超过 10 MiB"
                }
            }
            if (
                type == ProofMediaType.Video &&
                (durationSeconds == null || durationSeconds <= 0.0 ||
                    durationSeconds > ProofUploadRule.maxVideoDurationSeconds)
            ) return "视频实际录制时长必须在 15 秒内"
            return null
        }

    val isValidForUpload: Boolean
        get() = validationMessage == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProofAttachment
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class Membership(
    val id: String,
    val type: String,
    val organization: String,
    val studentId: String,
    val studentName: String,
    val status: String,
    val validUntil: String,
    val offset: String,
    val comment: String,
    val updatedBy: String,
    val updatedAt: String
) {
    val typeTitle: String
        get() = if (type == "team") "校队" else "社团"
}

data class GradeBlock(
    val id: String,
    val name: String,
    val weight: Double,
    val score: Int?,
    val scoreDisplay: String,
    val isVisible: Boolean,
    val displayOrder: Int,
    val blockType: String,
    val description: String?,
    val subItems: List<GradeSubItem>?
)

data class GradeSubItem(
    val name: String,
    val score: Int?,
    val scoreDisplay: String
)

/**
 * Server-authoritative outcome for the endurance-run grade item.  A duration
 * alone cannot tell an unrecorded result from an approved exemption or an
 * absence, so the grade API supplies this value independently.
 */
enum class EnduranceRunStatus {
    Recorded,
    Exempt,
    Absent,
    NotRecorded;

    companion object {
        fun fromApi(value: String?, timeSeconds: Int?): EnduranceRunStatus {
            return when (value?.trim()?.lowercase()) {
                "recorded", "completed", "measured" -> Recorded
                "exempt", "exempted", "免测" -> Exempt
                "absent", "missing", "缺考" -> Absent
                "not_recorded", "unrecorded", "pending", "" -> NotRecorded
                null -> if ((timeSeconds ?: 0) > 0) Recorded else NotRecorded
                else -> if ((timeSeconds ?: 0) > 0) Recorded else NotRecorded
            }
        }
    }
}

data class GradeRow(
    val studentId: String,
    val studentName: String,
    val visibleBlocks: List<GradeBlock>,
    val totalScore: Int?,
    val totalDisplay: String,
    val isPassed: Boolean?,
    val courseGradeStatus: String,
    val displayConfigVersion: Int,
    val sourceTrace: String,
    /** Measured 800m/1000m endurance-run duration supplied by the teaching system. */
    val enduranceRunTimeSeconds: Int? = null,
    /** Distinguishes a measured result from an exemption, absence, or no entry. */
    val enduranceRunStatus: EnduranceRunStatus = EnduranceRunStatus.NotRecorded,
    /** Teacher-assigned score for this item; an absence is always displayed as zero. */
    val enduranceRunScore: Int? = null
)

enum class NoticeCategory(val label: String) {
    Deadline("截止提醒"),
    Review("申请与材料"),
    Organization("组织认证"),
    System("系统通知")
}

data class StudentNotice(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val category: NoticeCategory = NoticeCategory.System,
    val isUnread: Boolean,
    val targetType: String? = null,
    val targetId: String? = null
) {
    val targetsExemption: Boolean
        get() = targetType?.lowercase() in setOf(
            "exemption",
            "physical_test_exemption",
            "checkin_exemption",
            "application"
        )

    val isStudentVisible: Boolean
        get() {
            if (category != NoticeCategory.Review) return true
            if (targetsExemption) return true
            val applicationKeywords = listOf("免测", "免打卡", "校队", "社团", "证明材料", "申请")
            return applicationKeywords.any { keyword -> title.contains(keyword) || message.contains(keyword) }
        }
}

data class SportHourRule(
    val total: Double,
    val courseRequired: Double,
    val generalRequired: Double,
    val dailyLimit: Double
) {
    companion object {
        val Standard = SportHourRule(total = 20.0, courseRequired = 10.0, generalRequired = 10.0, dailyLimit = 2.0)
    }
}

fun Double.hourText(): String {
    return if (this % 1.0 == 0.0) {
        "${toInt()}h"
    } else {
        "%.1fh".format(this)
    }
}

// ── Endurance Scoring ──────────────────────────────────────────────

data class EnduranceScoreResult(
    val score: Int,
    val tier: String,
    val timeSeconds: Int,
    val gender: String,
    val gradeLevel: String,
    val gradeGroup: String
) {
    val tierLabel: String
        get() = when (tier) {
            "excellent" -> "优秀"
            "good" -> "良好"
            "pass" -> "及格"
            "fail" -> "不及格"
            else -> tier
        }

    val tierColor: String
        get() = when (tier) {
            "excellent" -> "#3A9DF6"
            "good" -> "#4CAF50"
            "pass" -> "#FF9800"
            "fail" -> "#F44336"
            else -> "#757575"
        }
}

data class EnduranceConversionRequest(
    val timeSeconds: Int,
    val gender: String,
    val gradeLevel: String
)

// ── Exemptions ─────────────────────────────────────────────────────

enum class ExemptionType(val apiValue: String, val label: String) {
    Run800("800m", "800m 免测"),
    Run1000("1000m", "1000m 免测"),
    Team("team", "校队免打卡"),
    Club("club", "社团免打卡");

    val isCheckInExemption: Boolean
        get() = this == Team || this == Club
}

enum class ExemptionStatus(val label: String) {
    Pending("待审核"),
    Approved("已通过"),
    Rejected("已驳回")
}

data class Exemption(
    val id: String,
    val studentId: String,
    val studentName: String = "",
    val type: String,
    val category: String = "physical_test",
    val organization: String = "",
    val reason: String,
    val status: String,
    val proofFiles: List<String> = emptyList(),
    val reviewComment: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val createdAt: String,
    val updatedAt: String = ""
) {
    val typeLabel: String
        get() = when (type) {
            "800m" -> ExemptionType.Run800.label
            "1000m" -> ExemptionType.Run1000.label
            "team" -> ExemptionType.Team.label
            "club" -> ExemptionType.Club.label
            else -> type
        }
}

data class ExemptionApplication(
    val type: String,
    val reason: String,
    val proofFiles: List<String>,
    val organization: String? = null
)
// ── Student Tasks ──────────────────────────────────────────────────
