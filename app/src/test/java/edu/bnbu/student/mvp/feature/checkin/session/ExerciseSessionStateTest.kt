package edu.bnbu.student.mvp.feature.checkin.session

import edu.bnbu.student.mvp.core.model.CreditType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSessionStateTest {
    private val clock = FakeExerciseClock(1_000L)
    private val machine = ExerciseSessionMachine(clock)
    private val details = ExerciseSessionDetails(CreditType.General, "running")

    @Test
    fun startCreatesActiveSessionAtCurrentTime() {
        val result = machine.start(ExerciseSessionState.Idle, "session-1", details)

        val active = result.changedState<ExerciseSessionState.Active>()
        assertEquals("session-1", active.sessionId)
        assertEquals(1_000L, active.startedAtEpochMillis)
        assertEquals(1_000L, active.activeSegmentStartedAtEpochMillis)
        assertEquals(0L, active.accumulatedActiveMillis)
    }

    @Test
    fun activeDurationUsesTimestampsInsteadOfUiTicks() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()

        clock.advance(25.minutes)

        assertEquals(25.minutes, active.effectiveDurationMillis(clock.nowEpochMillis()))
    }

    @Test
    fun pausedTimeIsExcludedAndResumeContinuesAccumulation() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(20.minutes)
        val paused = machine.pause(active).changedState<ExerciseSessionState.Paused>()

        clock.advance(30.minutes)
        assertEquals(20.minutes, paused.effectiveDurationMillis(clock.nowEpochMillis()))

        val resumed = machine.resume(paused).changedState<ExerciseSessionState.Active>()
        clock.advance(40.minutes)
        val finished = machine.requestFinish(resumed).changedState<ExerciseSessionState.Finished>()

        assertEquals(60.minutes, finished.activeDurationMillis)
        assertEquals(1, finished.creditedHours)
    }

    @Test
    fun finishBeforeOneHourPausesAndKeepsSessionAvailableToResume() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(59.minutes + 59.seconds)

        val result = machine.requestFinish(active)

        assertTrue(result is ExerciseSessionTransition.TooShort)
        result as ExerciseSessionTransition.TooShort
        assertEquals(ExerciseTooShortMessage, result.message)
        assertEquals(59.minutes + 59.seconds, result.state.accumulatedActiveMillis)

        clock.advance(10.minutes)
        val resumed = machine.resume(result.state).changedState<ExerciseSessionState.Active>()
        clock.advance(1.seconds)
        assertEquals(60.minutes, resumed.effectiveDurationMillis(clock.nowEpochMillis()))
    }

    @Test
    fun exactOneHourFinishesWithOneCreditHour() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(60.minutes)

        val finished = machine.requestFinish(active).changedState<ExerciseSessionState.Finished>()

        assertEquals(MinimumValidExerciseMillis, finished.activeDurationMillis)
        assertEquals(1, finished.creditedHours)
    }

    @Test
    fun durationBelowTwoHoursStillCreditsOneHour() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(119.minutes + 59.seconds)

        val finished = machine.requestFinish(active).changedState<ExerciseSessionState.Finished>()

        assertEquals(1, finished.creditedHours)
    }

    @Test
    fun twoHourLimitStopsAtExactThresholdAndWaitsForConfirmation() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(125.minutes)

        val waiting = machine.autoFinishIfNeeded(active)
            .changedState<ExerciseSessionState.Paused>()

        assertEquals(MaximumExerciseMillis, waiting.accumulatedActiveMillis)
        assertEquals(1_000L + MaximumExerciseMillis, waiting.pausedAtEpochMillis)

        clock.advance(10.minutes)
        val finished = machine.requestFinish(waiting)
            .changedState<ExerciseSessionState.Finished>()
        assertEquals(MaximumExerciseMillis, finished.activeDurationMillis)
        assertEquals(2, finished.creditedHours)
        assertEquals(clock.nowEpochMillis(), finished.endedAtEpochMillis)
    }

    @Test
    fun twoHourLimitCannotResumeWhileWaitingForConfirmation() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(120.minutes)
        val waiting = machine.autoFinishIfNeeded(active)
            .changedState<ExerciseSessionState.Paused>()

        val resumeResult = machine.resume(waiting)

        assertTrue(resumeResult is ExerciseSessionTransition.Rejected)
        assertSame(waiting, resumeResult.state)
    }

    @Test
    fun invalidTransitionsAreRejectedWithoutChangingState() {
        val idle = ExerciseSessionState.Idle
        val pauseResult = machine.pause(idle)
        val resumeResult = machine.resume(idle)
        val finishResult = machine.requestFinish(idle)

        assertTrue(pauseResult is ExerciseSessionTransition.Rejected)
        assertTrue(resumeResult is ExerciseSessionTransition.Rejected)
        assertTrue(finishResult is ExerciseSessionTransition.Rejected)
        assertSame(idle, pauseResult.state)
        assertSame(idle, resumeResult.state)
        assertSame(idle, finishResult.state)
    }

    @Test
    fun clockRollbackDoesNotSubtractAccumulatedTime() {
        val active = machine.start(ExerciseSessionState.Idle, "session-1", details)
            .changedState<ExerciseSessionState.Active>()
        clock.advance(15.minutes)
        val paused = machine.pause(active).changedState<ExerciseSessionState.Paused>()
        val resumed = machine.resume(paused).changedState<ExerciseSessionState.Active>()

        clock.now -= 5.minutes

        assertEquals(15.minutes, resumed.effectiveDurationMillis(clock.nowEpochMillis()))
    }

    private inline fun <reified T : ExerciseSessionState> ExerciseSessionTransition.changedState(): T {
        assertTrue(this is ExerciseSessionTransition.Changed)
        return (this as ExerciseSessionTransition.Changed).state as T
    }

    private class FakeExerciseClock(var now: Long) : ExerciseClock {
        override fun nowEpochMillis(): Long = now

        fun advance(durationMillis: Long) {
            now += durationMillis
        }
    }

    private val Int.seconds: Long
        get() = this * 1_000L

    private val Int.minutes: Long
        get() = this * 60L * 1_000L
}
