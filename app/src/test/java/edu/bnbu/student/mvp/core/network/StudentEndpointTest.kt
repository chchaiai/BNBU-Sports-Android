package edu.bnbu.student.mvp.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentEndpointTest {
    @Test
    fun endpointContractsMatchBackendRoutes() {
        val contracts = listOf(
            Triple(StudentEndpoint.Login, HttpMethod.POST, "/auth/login"),
            Triple(StudentEndpoint.SportSummary, HttpMethod.GET, "/sport/summary"),
            Triple(StudentEndpoint.SportRecords, HttpMethod.POST, "/sport/records"),
            Triple(StudentEndpoint.SportRecordsList, HttpMethod.GET, "/sport/records"),
            Triple(StudentEndpoint.SportRecordDetail("record-1"), HttpMethod.GET, "/sport/records/record-1"),
            Triple(
                StudentEndpoint.SupplementSportRecord("record-1"),
                HttpMethod.POST,
                "/sport/records/record-1/supplements"
            ),
            Triple(StudentEndpoint.SportIdentity, HttpMethod.GET, "/sport/identity"),
            Triple(StudentEndpoint.Notifications, HttpMethod.GET, "/common/notifications"),
            Triple(
                StudentEndpoint.MarkNotificationRead("notice-1"),
                HttpMethod.PUT,
                "/common/notifications/notice-1/read"
            ),
            Triple(StudentEndpoint.ConvertEndurance, HttpMethod.POST, "/scoring/convert-endurance"),
            Triple(StudentEndpoint.StudentExemptions, HttpMethod.GET, "/student/exemptions"),
            Triple(StudentEndpoint.SubmitExemption, HttpMethod.POST, "/student/exemptions"),
            Triple(
                StudentEndpoint.PhysicalTestExemptions,
                HttpMethod.GET,
                "/student/physical-test-exemptions"
            ),
            Triple(
                StudentEndpoint.SubmitPhysicalTestExemption,
                HttpMethod.POST,
                "/student/physical-test-exemptions"
            ),
            Triple(
                StudentEndpoint.SupplementPhysicalTestExemption("physical-1"),
                HttpMethod.POST,
                "/student/physical-test-exemptions/physical-1/supplements"
            ),
            Triple(StudentEndpoint.CheckInExemptions, HttpMethod.GET, "/student/checkin-exemptions"),
            Triple(
                StudentEndpoint.SubmitCheckInExemption,
                HttpMethod.POST,
                "/student/checkin-exemptions"
            ),
            Triple(
                StudentEndpoint.SupplementCheckInExemption("checkin-1"),
                HttpMethod.POST,
                "/student/checkin-exemptions/checkin-1/supplements"
            ),
            Triple(StudentEndpoint.StudentTasks, HttpMethod.GET, "/student/tasks"),
            Triple(StudentEndpoint.StudentProfile, HttpMethod.GET, "/student/profile"),
            Triple(StudentEndpoint.UpdateStudentProfile, HttpMethod.PUT, "/student/profile"),
            Triple(StudentEndpoint.UploadProof, HttpMethod.POST, "/upload/proof"),
            Triple(StudentEndpoint.StudentGrades, HttpMethod.GET, "/student/grades")
        )

        contracts.forEach { (endpoint, method, path) ->
            assertEquals(method, endpoint.method)
            assertEquals(path, endpoint.path)
        }
    }

    @Test
    fun dynamicPathAndQueryValuesAreEncoded() {
        assertEquals(
            "/sport/records/a%2Fb%20c",
            StudentEndpoint.SportRecordDetail("a/b c").path
        )
        assertEquals(
            "/student/courses?scope=current%20term&semesterId=2026%2Ffall",
            StudentEndpoint.StudentCourses(
                scope = "current term",
                semesterId = "2026/fall"
            ).path
        )
    }
}
