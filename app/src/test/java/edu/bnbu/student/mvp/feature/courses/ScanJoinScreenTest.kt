package edu.bnbu.student.mvp.feature.courses

import edu.bnbu.student.mvp.core.network.ApiHttpException
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
        assertFalse(isInviteUnavailableError(ApiHttpException(500, "server error")))
    }
}
