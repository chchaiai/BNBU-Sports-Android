package edu.bnbu.student.mvp.feature.courses

import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.network.v1.generated.CourseInvitePreview
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertFalse(isInviteUnavailableError(ApiHttpException(404, "not found")))
        assertFalse(isInviteUnavailableError(ApiHttpException(500, "server error")))
    }

    @Test
    fun mapsTheGeneratedV1PreviewWithoutLegacyCourseDtos() {
        val course = CourseInvitePreview(
            classSectionId = "section-1",
            displayName = "Section One",
            courseCode = "PE-101",
            courseName = "Physical Education",
            semesterDisplayName = "2026 Fall",
            teacherDisplayName = "Teacher",
            enrollmentOpen = true,
            expiresAt = OffsetDateTime.parse("2026-12-01T00:00:00Z")
        ).toCourseJoinInfo()

        assertEquals("section-1", course.id)
        assertEquals("Physical Education", course.name)
        assertEquals("PE-101", course.courseNumber)
        assertEquals("Section One", course.section)
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
