package edu.bnbu.student.mvp.core.network

// ── Response DTOs mirroring backend JSON shapes ──────────────────

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val college: String = "",
    val scope: String = "",
    val status: String = "正常",
    val gender: String? = null,
    val gradeLevel: String? = null,
    val className: String = ""
)

data class LoginResponse(
    val token: String,
    val user: UserDto,
    val defaultRoute: String
)

data class SportSummaryResponse(
    val courseHours: Double = 0.0,
    val generalHours: Double = 0.0,
    val totalCompleted: Double = 0.0,
    val totalRequired: Double = 20.0,
    val totalRemaining: Double = 20.0,
    val courseRemaining: Double = 10.0,
    val generalRemaining: Double = 10.0,
    val completed: Boolean = false,
    val pendingCount: Int = 0,
    val rule: SportRuleDto? = null,
    val teachers: List<TeacherDto> = emptyList(),
    val courses: List<StudentCourseDto> = emptyList()
)

data class TeacherDto(
    val teacherId: String = "",
    val teacherName: String = ""
)

data class StudentCourseDto(
    val courseId: String = "",
    val courseCode: String = "",
    val courseSection: String = "",
    val courseName: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val courseHours: Double = 0.0,
    val generalHours: Double = 0.0
)

data class SportRuleDto(
    val total: Double = 20.0,
    val courseRequired: Double = 10.0,
    val generalRequired: Double = 10.0,
    val dailyLimit: Double = 2.0
)

data class SportRecordResponse(
    val id: String,
    val courseId: String? = null,
    val taskId: String? = null,
    val creditType: String = "",
    val hours: Double = 0.0,
    val approvedHours: Double = 0.0,
    val description: String? = null,
    val proofFiles: List<String> = emptyList(),
    val status: String = "待审核",
    val reviewComment: String? = null,
    val submittedAt: String? = null,
    val reviewedAt: String? = null
)

data class SubmitRecordResponse(
    val id: String,
    val status: String,
    val submittedAt: String
)

data class SupplementResponse(
    val id: String,
    val status: String,
    val message: String = ""
)

data class MembershipResponse(
    val id: String,
    val type: String,
    val organization: String,
    val studentId: String,
    val studentName: String = "",
    val status: String = "待确认",
    val validUntil: String? = null,
    val offset: String = "待确认",
    val comment: String? = null,
    val updatedBy: String? = null,
    val updatedAt: String? = null
)

data class NotificationResponse(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val category: String = "系统通知",
    val isUnread: Boolean = true
)

data class MarkReadResponse(
    val id: String,
    val read: Boolean
)

// ── Endurance Scoring ──────────────────────────────────────────────

data class EnduranceScoreResponse(
    val score: Int,
    val tier: String,
    val timeSeconds: Int,
    val gender: String,
    val gradeLevel: String,
    val gradeGroup: String,
    val range: TimeRange? = null,
    val note: String? = null
)

data class TimeRange(
    val min: Int,
    val max: Int
)

// ── Exemptions ─────────────────────────────────────────────────────

data class ExemptionResponse(
    val id: String,
    val studentId: String,
    val studentName: String? = null,
    val type: String,
    val reason: String? = null,
    val status: String,
    val proofFiles: List<String> = emptyList(),
    val reviewComment: String? = null,
    val reviewerId: String? = null,
    val reviewerName: String? = null,
    val createdAt: String,
    val updatedAt: String? = null
)

data class ExemptionSubmitResponse(
    val id: String,
    val status: String,
    val createdAt: String
)

// ── Student Profile ────────────────────────────────────────────────

data class StudentProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val college: String = "",
    val gender: String? = null,
    val gradeLevel: String? = null,
    val status: String = "正常",
    val enrolledCourses: Int = 0
)

data class StudentProfileUpdateRequest(
    val gender: String? = null,
    val gradeLevel: String? = null
)

// ── Student Tasks ──────────────────────────────────────────────────

data class StudentTaskListResponse(
    val pending: List<StudentTaskItemResponse> = emptyList(),
    val completed: List<StudentTaskItemResponse> = emptyList()
)

// ── Student Grades ─────────────────────────────────────────────────

data class StudentGradeResponse(
    val studentId: String,
    val studentName: String,
    val courseId: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val courseHours: Double = 0.0,
    val generalHours: Double = 0.0,
    val checkin: Int = 0,
    val checkinScore: Int = 0,
    val exam: Int = 0,
    val attendance: Int = 0,
    val physical: Int = 0,
    val overallTotal: Int = 0,
    val total: Int = 0,
    val sourceTrace: String? = null
) {
    val resolvedCheckinScore: Int
        get() = if (checkinScore != 0) checkinScore else checkin

    val resolvedTotal: Int
        get() = if (total != 0) total else overallTotal
}

data class StudentGradesResponse(
    val grades: List<StudentGradeResponse> = emptyList(),
    val summary: StudentGradesSummary = StudentGradesSummary()
)

data class StudentGradesSummary(
    val overallCheckinScore: Int = 0,
    val overallExam: Int = 0,
    val overallAttendance: Int = 0,
    val overallPhysical: Int = 0,
    val overallTotal: Int = 0,
    val totalPossible: Int = 100
)

data class StudentTaskItemResponse(
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
)
