package edu.bnbu.student.mvp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupReadinessTest {
    @Test
    fun keepsSystemSplashUntilEveryRealStartupConditionIsReady() {
        assertTrue(
            shouldKeepSystemSplash(
                sessionRestoreComplete = false,
                privacyConsentChecked = true,
                initialTargetReady = true
            )
        )
        assertTrue(
            shouldKeepSystemSplash(
                sessionRestoreComplete = true,
                privacyConsentChecked = false,
                initialTargetReady = true
            )
        )
        assertTrue(
            shouldKeepSystemSplash(
                sessionRestoreComplete = true,
                privacyConsentChecked = true,
                initialTargetReady = false
            )
        )
    }

    @Test
    fun releasesSystemSplashImmediatelyWhenTheRealTargetIsReady() {
        assertFalse(
            shouldKeepSystemSplash(
                sessionRestoreComplete = true,
                privacyConsentChecked = true,
                initialTargetReady = true
            )
        )
    }
}
