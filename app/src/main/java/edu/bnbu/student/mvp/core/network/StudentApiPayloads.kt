package edu.bnbu.student.mvp.core.network

data class StudentLoginRequest(
    val account: String,
    val password: String,
    val role: String = "student",
    val clientType: String = "mobile"
)

data class SubmitSportRecordRequest(
    val creditType: String,
    val courseId: String?,
    val taskId: String,
    val hours: Double,
    val description: String,
    val proofFiles: List<String>
)

data class SupplementSportRecordRequest(
    val hours: Double,
    val description: String,
    val proofFiles: List<String>
)

data class EnduranceConversionRequest(
    val timeSeconds: Int,
    val gender: String,
    val gradeLevel: String
)

data class ExemptionApplication(
    val type: String,
    val reason: String,
    val proofFiles: List<String>
)
