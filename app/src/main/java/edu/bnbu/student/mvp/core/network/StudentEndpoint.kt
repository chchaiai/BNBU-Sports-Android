package edu.bnbu.student.mvp.core.network

import java.net.URLEncoder

enum class HttpMethod {
    GET,
    POST,
    PUT
}

sealed class StudentEndpoint(val method: HttpMethod) {
    data object Login : StudentEndpoint(HttpMethod.POST)
    data object SportSummary : StudentEndpoint(HttpMethod.GET)
    data object SportRecords : StudentEndpoint(HttpMethod.POST)
    data object SportRecordsList : StudentEndpoint(HttpMethod.GET)   // GET list with filters
    data class SportRecordDetail(val id: String) : StudentEndpoint(HttpMethod.GET)
    data class SupplementSportRecord(val id: String) : StudentEndpoint(HttpMethod.POST)
    data object SportIdentity : StudentEndpoint(HttpMethod.GET)
    data object Notifications : StudentEndpoint(HttpMethod.GET)
    data class MarkNotificationRead(val id: String) : StudentEndpoint(HttpMethod.PUT)

    // ── New endpoints ──────────────────────────────────────────────
    data object ConvertEndurance : StudentEndpoint(HttpMethod.POST)
    data object StudentExemptions : StudentEndpoint(HttpMethod.GET)
    data object SubmitExemption : StudentEndpoint(HttpMethod.POST)
    data object PhysicalTestExemptions : StudentEndpoint(HttpMethod.GET)
    data object SubmitPhysicalTestExemption : StudentEndpoint(HttpMethod.POST)
    data object CheckInExemptions : StudentEndpoint(HttpMethod.GET)
    data object SubmitCheckInExemption : StudentEndpoint(HttpMethod.POST)
    data object StudentTasks : StudentEndpoint(HttpMethod.GET)
    data class StudentCourses(
        val scope: String = "all",
        val semesterId: String? = null
    ) : StudentEndpoint(HttpMethod.GET)
    data object StudentProfile : StudentEndpoint(HttpMethod.GET)
    data object UpdateStudentProfile : StudentEndpoint(HttpMethod.PUT)
    data object UploadProof : StudentEndpoint(HttpMethod.POST)
    data object StudentGrades : StudentEndpoint(HttpMethod.GET)

    val path: String
        get() = when (this) {
            Login -> "/auth/login"
            SportSummary -> "/sport/summary"
            SportRecords -> "/sport/records"
            SportRecordsList -> "/sport/records"
            is SportRecordDetail -> "/sport/records/${id.pathSegment()}"
            is SupplementSportRecord -> "/sport/records/${id.pathSegment()}/supplements"
            SportIdentity -> "/sport/identity"
            Notifications -> "/common/notifications"
            is MarkNotificationRead -> "/common/notifications/${id.pathSegment()}/read"
            ConvertEndurance -> "/scoring/convert-endurance"
            StudentExemptions -> "/student/exemptions"
            SubmitExemption -> "/student/exemptions"
            PhysicalTestExemptions -> "/student/physical-test-exemptions"
            SubmitPhysicalTestExemption -> "/student/physical-test-exemptions"
            CheckInExemptions -> "/student/checkin-exemptions"
            SubmitCheckInExemption -> "/student/checkin-exemptions"
            StudentTasks -> "/student/tasks"
            is StudentCourses -> buildString {
                append("/student/courses?scope=")
                append(scope.queryValue())
                semesterId?.takeIf { it.isNotBlank() }?.let {
                    append("&semesterId=")
                    append(it.queryValue())
                }
            }
            StudentProfile -> "/student/profile"
            UpdateStudentProfile -> "/student/profile"
            UploadProof -> "/upload/proof"
            StudentGrades -> "/student/grades"
        }
}

private fun String.queryValue(): String {
    return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

private fun String.pathSegment(): String {
    return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
