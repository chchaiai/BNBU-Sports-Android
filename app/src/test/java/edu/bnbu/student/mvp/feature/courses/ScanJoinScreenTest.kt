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
        val opaqueToken = "019ff95a-84ad-cd03-f69b34d4.wKlhxS_lbM"
        assertEquals(
            opaqueToken,
            inviteCodeFromQr("https://sports.example.com/join/$opaqueToken")
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
        assertTrue(isInviteCode("019ff95a-84ad-cd03-f69b34d4.wKlhxS_lbM"))
        assertTrue(isInviteCode("  0123456789abcdef  "))
        assertFalse(isInviteCode("bad code"))
        assertFalse(isInviteCode("x".repeat(513)))
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
    fun previewMappingKeepsEveryServerOwnedDisplayField() {
        val course = CourseInvitePreview(
            classSectionId = "server-section",
            displayName = "Server Section",
            courseCode = "PE-SERVER",
            courseName = "Server Course",
            semesterDisplayName = "Server Semester",
            teacherDisplayName = "Server Teacher",
            enrollmentOpen = true,
            expiresAt = OffsetDateTime.parse("2026-12-01T00:00:00Z")
        ).toCourseJoinInfo()

        assertEquals("server-section", course.id)
        assertEquals("Server Course", course.name)
        assertEquals("PE-SERVER", course.courseNumber)
        assertEquals("Server Section", course.section)
        assertEquals("Server Teacher", course.teacher)
        assertEquals("Server Semester", course.semester)
    }
}
