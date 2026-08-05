package edu.bnbu.student.mvp.core.network

import java.net.URLEncoder

enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE
}

sealed class StudentEndpoint(val method: HttpMethod) {
    /** Public app-configuration endpoint, available before a student logs in. */
    data object MinimumAppVersion : StudentEndpoint(HttpMethod.GET)
    /** Public availability policy, checked at startup before authentication. */
    data object Health : StudentEndpoint(HttpMethod.GET)
    data object Login : StudentEndpoint(HttpMethod.POST)
    data object EmailLogin : StudentEndpoint(HttpMethod.POST)
    data object PhoneLogin : StudentEndpoint(HttpMethod.POST)
    data object SportSummary : StudentEndpoint(HttpMethod.GET)
    data object SportRecords : StudentEndpoint(HttpMethod.POST)
    data object SportRecordsList : StudentEndpoint(HttpMethod.GET)   // GET list with filters
    data class SportRecordDetail(val id: String) : StudentEndpoint(HttpMethod.GET)
    data object SportIdentity : StudentEndpoint(HttpMethod.GET)
    data object Notifications : StudentEndpoint(HttpMethod.GET)
    /** Help content is published and maintained by administrators on the backend. */
    data object HelpArticles : StudentEndpoint(HttpMethod.GET)
    data class MarkNotificationRead(val id: String) : StudentEndpoint(HttpMethod.PUT)
    /** Registers or refreshes the current Android installation's opaque FCM token. */
    data object RegisterPushDevice : StudentEndpoint(HttpMethod.POST)
    /** Removes the current Android installation's token from this student's account. */
    data object UnregisterPushDevice : StudentEndpoint(HttpMethod.DELETE)
    /** Persists the language used for server-originated communication, such as email. */
    data object UpdateLanguagePreference : StudentEndpoint(HttpMethod.PUT)

    // ── New endpoints ──────────────────────────────────────────────
    data object ConvertEndurance : StudentEndpoint(HttpMethod.POST)
    data object StudentExemptions : StudentEndpoint(HttpMethod.GET)
    data object SubmitExemption : StudentEndpoint(HttpMethod.POST)
    data object PhysicalTestExemptions : StudentEndpoint(HttpMethod.GET)
    data object SubmitPhysicalTestExemption : StudentEndpoint(HttpMethod.POST)
    data class SupplementPhysicalTestExemption(val id: String) : StudentEndpoint(HttpMethod.POST)
    data object CheckInExemptions : StudentEndpoint(HttpMethod.GET)
    data object SubmitCheckInExemption : StudentEndpoint(HttpMethod.POST)
    data class SupplementCheckInExemption(val id: String) : StudentEndpoint(HttpMethod.POST)
    data class StudentCourses(
        val scope: String = "all",
        val semesterId: String? = null
    ) : StudentEndpoint(HttpMethod.GET)
    /** Server-authoritative date and daily time policy for starting a check-in. */
    data object CheckInTimeWindow : StudentEndpoint(HttpMethod.GET)
    data object StudentProfile : StudentEndpoint(HttpMethod.GET)
    data object UpdateStudentProfile : StudentEndpoint(HttpMethod.PUT)
    /** Used before authentication when a student has lost both login contacts. */
    data object RecoveryRequests : StudentEndpoint(HttpMethod.POST)
    data object UploadProof : StudentEndpoint(HttpMethod.POST)
    data object StudentGrades : StudentEndpoint(HttpMethod.GET)
    data object SendEmailContactCode : StudentEndpoint(HttpMethod.POST)
    data object VerifyEmailContactCode : StudentEndpoint(HttpMethod.POST)
    data object SendPhoneContactCode : StudentEndpoint(HttpMethod.POST)
    data object VerifyPhoneContactCode : StudentEndpoint(HttpMethod.POST)
    /** Service-feedback contract. The backend owns the final field schema. */
    data object SubmitFeedback : StudentEndpoint(HttpMethod.POST)
    data object FeedbackTickets : StudentEndpoint(HttpMethod.GET)
    /** Public lookup used before a student directly joins a course. */
    data class CourseInviteLookup(val code: String) : StudentEndpoint(HttpMethod.GET)
    /** Atomically validates the invite and creates or returns the active membership. */
    data class CourseJoin(val courseId: String) : StudentEndpoint(HttpMethod.POST)

    val path: String
        get() = when (this) {
            MinimumAppVersion -> "/v1/config/minimum-app-version"
            Health -> "/health"
            Login -> "/auth/login"
            EmailLogin -> "/auth/login/email"
            PhoneLogin -> "/v1/auth/login/phone"
            SportSummary -> "/sport/summary"
            SportRecords -> "/sport/records"
            SportRecordsList -> "/sport/records"
            is SportRecordDetail -> "/sport/records/${id.pathSegment()}"
            SportIdentity -> "/sport/identity"
            Notifications -> "/common/notifications"
            HelpArticles -> "/common/help-articles"
            is MarkNotificationRead -> "/common/notifications/${id.pathSegment()}/read"
            RegisterPushDevice, UnregisterPushDevice -> "/v1/student/push-devices"
            UpdateLanguagePreference -> "/v1/student/preferences/language"
            ConvertEndurance -> "/scoring/convert-endurance"
            StudentExemptions -> "/student/exemptions"
            SubmitExemption -> "/student/exemptions"
            PhysicalTestExemptions -> "/student/physical-test-exemptions"
            SubmitPhysicalTestExemption -> "/student/physical-test-exemptions"
            is SupplementPhysicalTestExemption ->
                "/student/physical-test-exemptions/${id.pathSegment()}/supplements"
            CheckInExemptions -> "/student/checkin-exemptions"
            SubmitCheckInExemption -> "/student/checkin-exemptions"
            is SupplementCheckInExemption ->
                "/student/checkin-exemptions/${id.pathSegment()}/supplements"
            is StudentCourses -> buildString {
                append("/student/courses?scope=")
                append(scope.queryValue())
                semesterId?.takeIf { it.isNotBlank() }?.let {
                    append("&semesterId=")
                    append(it.queryValue())
                }
            }
            CheckInTimeWindow -> "/student/checkin-time-window"
            StudentProfile -> "/student/profile"
            UpdateStudentProfile -> "/student/profile"
            RecoveryRequests -> "/v1/student/recovery-requests"
            UploadProof -> "/upload/proof"
            StudentGrades -> "/student/grades"
            SendEmailContactCode -> "/v1/student/contacts/email/send-code"
            VerifyEmailContactCode -> "/v1/student/contacts/email/verify"
            SendPhoneContactCode -> "/v1/student/contacts/phone/send-code"
            VerifyPhoneContactCode -> "/v1/student/contacts/phone/verify"
            SubmitFeedback, FeedbackTickets -> "/v1/student/feedback"
            is CourseInviteLookup -> "/v1/course-invites/${code.pathSegment()}"
            is CourseJoin -> "/courses/${courseId.pathSegment()}/join"
        }
}

private fun String.queryValue(): String {
    return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

private fun String.pathSegment(): String {
    return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
