package edu.bnbu.student.mvp.core.network

data class StudentLoginRequest(
    val account: String,
    val password: String,
    val role: String = "student",
    val clientType: String = "mobile"
)

data class ProofFileReference(
    val cosKey: String,
    val mediaType: String,
    val mimeType: String,
    val size: Long
)

data class SubmitSportRecordRequest(
    val creditType: String,
    val courseId: String?,
    val taskId: String?,
    val hours: Double,
    val description: String,
    val proofFiles: List<ProofFileReference>,
    val sportType: String? = null
)

data class SupplementSportRecordRequest(
    val hours: Double,
    val description: String,
    val proofFiles: List<ProofFileReference>
)

data class EnduranceConversionRequest(
    val timeSeconds: Int,
    val gender: String,
    val gradeLevel: String
)

data class ExemptionSupplementRequest(
    val reason: String,
    val proofFiles: List<String>,
    val organization: String? = null
)
