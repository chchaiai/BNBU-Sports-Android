package edu.bnbu.student.mvp.core.network

data class StudentLoginRequest(
    val account: String,
    val password: String,
    val role: String = "student",
    val clientType: String = "mobile"
)

/**
 * FCM tokens are opaque device addresses, never notification content. The
 * authenticated backend associates this token with the current student.
 */
data class PushDeviceRegistrationRequest(
    val token: String,
    val platform: String = "android",
    val appVersion: String
)

/** Language preference consumed by server-originated communication such as email. */
data class UpdateLanguagePreferenceRequest(
    val language: String
)

/** Payload used to verify an email login code. Codes are six digits and single-use. */
data class EmailLoginRequest(
    val email: String,
    val code: String
)

/** Payload used to request an email login code. */
data class SendEmailCodeRequest(
    val email: String
)

/** Payload used to verify a phone login code. Codes are six digits and single-use. */
data class PhoneLoginRequest(
    val phone: String,
    val code: String
)

/** Payload used to request a phone login code. */
data class SendPhoneCodeRequest(
    val phone: String
)

/** Request body for POST /api/v1/course-invites/{code}/join-request. */
data class CourseInviteJoinRequestBody(
    val name: String,
    val studentNumber: String,
    val email: String?
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
    val hours: Double,
    val description: String,
    val remark: String = "",
    val proofFiles: List<ProofFileReference>,
    val sportType: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val actualDurationSeconds: Long? = null
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

/** Request body for POST /api/v1/student/feedback. */
data class SubmitFeedbackRequest(
    val category: String,
    val description: String,
    val currentPage: String,
    val clientVersion: String,
    val screenshots: List<String> = emptyList(),
    /** Contact details are used only to follow up on this feedback ticket. */
    val email: String,
    val phone: String
)
