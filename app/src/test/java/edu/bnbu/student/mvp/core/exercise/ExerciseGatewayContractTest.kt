package edu.bnbu.student.mvp.core.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExerciseGatewayContractTest {
    @Test
    fun completedSessionUsesTheBackendCompletedPhase() {
        val session = ExerciseSessionRecord(
            sessionId = "session-1",
            phase = ExerciseSessionPhase.COMPLETED,
            startedAtEpochMillis = 1_000L,
            activeDurationSeconds = MaximumExerciseDurationSeconds
        )

        assertEquals(ExerciseSessionPhase.COMPLETED, session.phase)
    }

    @Test
    fun sessionContractRejectsDurationAboveTwoHours() {
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseSessionRecord(
                sessionId = "session-1",
                phase = ExerciseSessionPhase.ACTIVE,
                startedAtEpochMillis = 1_000L,
                activeDurationSeconds = MaximumExerciseDurationSeconds + 1L
            )
        }
    }
}
