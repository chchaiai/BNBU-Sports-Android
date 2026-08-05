package edu.bnbu.student.mvp.feature.courses

import edu.bnbu.student.mvp.core.network.ApiHttpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanJoinScreenTest {
    @Test
    fun extractsInviteCodeFromExpectedQrUrl() {
        assertEquals(
            "BNBU-7K3P9Q",
            inviteCodeFromQr("https://sports.example.com/join/BNBU-7K3P9Q")
        )
    }

    @Test
    fun rejectsUrlsOutsideTheCourseJoinFormat() {
        assertNull(inviteCodeFromQr("https://sports.example.com/course/BNBU-7K3P9Q"))
        assertNull(inviteCodeFromQr("http://sports.example.com/join/BNBU-7K3P9Q"))
        assertNull(inviteCodeFromQr("BNBU-7K3P9Q"))
    }

    @Test
    fun validatesManualInviteCodes() {
        assertTrue(isInviteCode("BNBU-7K3P9Q"))
        assertFalse(isInviteCode("bad code"))
    }

    @Test
    fun recognizesExpiredAndRevokedInvitations() {
        assertTrue(isInviteUnavailableError(ApiHttpException(410, "INVITE_EXPIRED")))
        assertTrue(isInviteUnavailableError(ApiHttpException(404, "invite revoked")))
        assertTrue(isInviteUnavailableError(ApiHttpException(404, "not found")))
        assertFalse(isInviteUnavailableError(ApiHttpException(500, "server error")))
    }

    @Test
    fun directJoinRequiresCourseIdNameAndCode() {
        assertFalse(CourseInviteLookupResponse().hasCourseDetails())
        assertFalse(CourseInviteLookupResponse(courseName = "Physical Education").hasCourseDetails())
        assertTrue(
            CourseInviteLookupResponse(
                data = CourseInviteCourseResponse(
                    id = "course-1",
                    name = "Physical Education",
                    code = "PE-101"
                )
            ).hasCourseDetails()
        )
    }

    @Test
    fun mapsDirectJoinCourseIdAndRejectsUnavailableInvites() {
        val available = CourseInviteLookupResponse(
            courseId = "course-1",
            courseName = "Physical Education",
            courseCode = "PE-101",
            joinEnabled = true
        )
        available.validateForDirectJoin()
        assertEquals("course-1", available.toCourseJoinInfo().id)

        assertThrows(InviteLookupException.Expired::class.java) {
            available.copy(inviteStatus = "expired").validateForDirectJoin()
        }
        assertThrows(InviteLookupException.Revoked::class.java) {
            available.copy(inviteStatus = "revoked").validateForDirectJoin()
        }
        assertThrows(InviteLookupException.Closed::class.java) {
            available.copy(joinEnabled = false).validateForDirectJoin()
        }
        assertThrows(InviteLookupException.Expired::class.java) {
            available.copy(expiresAt = "2000-01-01T00:00:00Z").validateForDirectJoin()
        }
    }

    @Test
    fun demoScanResultIsClearlyMarkedAndUsesTheWebCourseExample() {
        assertEquals("PE01-7K2Q", DemoStudentScanInviteCode)
        assertTrue(DemoStudentScanCourse.isDemoScanResult)
        assertEquals("demo-course-pe101-01", DemoStudentScanCourse.id)
        assertEquals("大学体育（一）", DemoStudentScanCourse.name)
        assertEquals("PE101", DemoStudentScanCourse.courseNumber)
        assertEquals("01班", DemoStudentScanCourse.section)
        assertEquals("陈若宁", DemoStudentScanCourse.teacher)
        assertEquals("2025–2026 第二学期", DemoStudentScanCourse.semester)
    }
}
