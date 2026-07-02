package edu.bnbu.student.mvp.core.model

data class StudentProfile(
    val id: String,
    val name: String,
    val email: String,
    val college: String,
    val className: String,
    val status: String,
    val gender: String = "",
    val gradeLevel: String = ""
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

data class TeacherInfo(
    val teacherId: String,
    val teacherName: String
)

data class StudentWorkspace(
    val student: StudentProfile,
    val courses: List<Course>,
    val progress: StudentProgress,
    val tasks: List<CourseTask>,
    val records: List<CheckInRecord>,
    val grades: GradeRow,
    val memberships: List<Membership>,
    val notices: List<StudentNotice>,
    val teachers: List<TeacherInfo> = emptyList(),
    val syncOperations: List<SyncOperation> = emptyList(),
    val exemptions: List<Exemption> = emptyList(),
    val studentTasks: StudentTaskList = StudentTaskList(emptyList(), emptyList())
) {
    companion object {
        fun empty(): StudentWorkspace = StudentWorkspace(
            student = StudentProfile(id = "", name = "", email = "", college = "", className = "", status = "未登录"),
            courses = emptyList(),
            progress = StudentProgress(id = "", name = "", college = "", className = "", course = 0.0, general = 0.0, rawGeneral = 0.0, exam = 0, attendance = 0, physical = 0, status = "请先登录", source = "empty", organizationCredit = null),
            tasks = emptyList(),
            records = emptyList(),
            grades = GradeRow(studentId = "", studentName = "", checkinScore = 0, exam = 0, attendance = 0, physical = 0, total = 0, sourceTrace = "", missingItems = emptyList()),
            memberships = emptyList(),
            notices = emptyList(),
            teachers = emptyList(),
            syncOperations = emptyList(),
            exemptions = emptyList(),
            studentTasks = StudentTaskList(emptyList(), emptyList())
        )
    }
}

enum class SyncOperationType(val label: String) {
    SubmitRecord("提交打卡"),
    SupplementRecord("补交材料"),
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
    val pending: Int,
    val completion: Int,
    val missing: Int,
    val deadline: String,
    val teacher: String,
    val teacherId: String = ""
) {
    val displayTitle: String
        get() = "$code / Section $section"
}

data class StudentProgress(
    val id: String,
    val name: String,
    val college: String,
    val className: String,
    val course: Double,
    val general: Double,
    val rawGeneral: Double,
    val exam: Int,
    val attendance: Int,
    val physical: Int,
    val status: String,
    val source: String,
    val organizationCredit: Membership?
)

enum class CreditType(val label: String) {
    CourseRelated("课程相关"),
    General("其他运动"),
    OrganizationOffset("系统抵扣")
}

enum class TaskStatus(val label: String) {
    Draft("草稿"),
    Active("进行中"),
    Closed("已关闭")
}

data class CourseTask(
    val id: String,
    val courseId: String,
    val creditType: CreditType,
    val title: String,
    val hours: Double,
    val deadline: String,
    val proof: String,
    val status: TaskStatus,
    val updatedAt: String
)

enum class ReviewStatus(val label: String) {
    Pending("待审核"),
    Approved("已通过"),
    Rejected("被驳回"),
    Supplement("需补材料"),
    Offset("系统抵扣")
}

data class CheckInRecord(
    val id: String,
    val courseId: String?,
    val taskTitle: String,
    val creditType: CreditType,
    val hours: Double,
    val submittedAt: String,
    val status: ReviewStatus,
    val proofSummary: String,
    val proofPhotoCount: Int,
    val proofVideoCount: Int,
    val proofFiles: List<ProofAttachment>,
    val teacherFeedback: String,
    val note: String
)

enum class ProofMediaType(val label: String) {
    Image("图片"),
    Video("视频")
}

object ProofUploadRule {
    const val maxAttachmentCount = 8
    const val maxImageBytes = 10_000_000
    const val maxVideoBytes = 80_000_000
    const val maxVideoDurationSeconds = 30

    val summaryText: String
        get() = "最多 $maxAttachmentCount 个；图片不超过 10MB；视频不超过 80MB，视频不超过 $maxVideoDurationSeconds 秒。"
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
            val bytes = byteCount ?: return "本地占位"
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
                    return "图片超过 10MB"
                }
                if (type == ProofMediaType.Video && bytes > ProofUploadRule.maxVideoBytes) {
                    return "视频超过 80MB"
                }
            }
            if (
                type == ProofMediaType.Video &&
                durationSeconds != null &&
                durationSeconds > ProofUploadRule.maxVideoDurationSeconds
            ) {
                return "视频超过 ${ProofUploadRule.maxVideoDurationSeconds} 秒"
            }
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

data class CheckInDraft(
    val id: String,
    val taskId: String,
    val hours: Double,
    val note: String,
    val proofAttachments: List<ProofAttachment>,
    val updatedAt: String
)

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

data class GradeRow(
    val studentId: String,
    val studentName: String,
    val checkinScore: Int,
    val exam: Int,
    val attendance: Int,
    val physical: Int,
    val total: Int,
    val sourceTrace: String,
    val missingItems: List<String>
)

enum class NoticeCategory(val label: String) {
    Deadline("截止提醒"),
    Review("审核反馈"),
    Organization("组织认证"),
    System("系统通知")
}

data class StudentNotice(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val category: NoticeCategory = NoticeCategory.System,
    val isUnread: Boolean
)

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

enum class ExemptionType(val label: String) {
    Run800("800m"),
    Run1000("1000m")
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
        get() = if (type == "800m") ExemptionType.Run800.label else ExemptionType.Run1000.label
}

data class ExemptionApplication(
    val type: String,
    val reason: String,
    val proofFiles: List<String>
)

// ── Student Tasks ──────────────────────────────────────────────────

data class StudentTaskList(
    val pending: List<StudentTaskItem>,
    val completed: List<StudentTaskItem>
)

data class StudentTaskItem(
    val id: String,
    val courseId: String,
    val courseCode: String = "",
    val courseSection: String = "",
    val courseName: String = "",
    val title: String,
    val description: String = "",
    val creditType: String,
    val requiredHours: Double = 0.0,
    val deadline: String,
    val status: String,
    val completedAt: String? = null
) {
    val isActive: Boolean get() = status == "进行中"

    val creditTypeLabel: String
        get() = when (creditType) {
            "课程相关" -> "课程"
            "其他运动" -> "其他"
            else -> creditType
        }
}
