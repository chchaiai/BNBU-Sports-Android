package edu.bnbu.student.mvp.feature.checkin

import edu.bnbu.student.mvp.core.model.CheckInTimeWindow
import edu.bnbu.student.mvp.core.mock.MockStudentWorkspace
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CheckInTimeWindowTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun unavailablePolicyBlocksInsteadOfFallingBackToHardCodedHours() {
        assertNotNull(
            CheckInTimeWindow.unavailable().canStartExercise(
                ZonedDateTime.of(2026, 7, 27, 12, 0, 0, 0, shanghai)
            )
        )
    }

    @Test
    fun mockUserUsesLocalCheckInPolicyWithoutServerLoading() {
        assertNull(
            MockStudentWorkspace.create().checkInTimeWindow.canStartExercise(
                ZonedDateTime.of(2026, 7, 27, 12, 0, 0, 0, shanghai)
            )
        )
    }

    @Test
    fun beijingWindowAllows2150ButRejectsStartingAfter2200() {
        val window = MockStudentWorkspace.create().checkInTimeWindow

        assertNotNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 5, 59, 59, 0, shanghai)))
        assertNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 6, 0, 0, 0, shanghai)))
        assertNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 21, 50, 0, 0, shanghai)))
        assertNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 22, 0, 0, 0, shanghai)))
        assertNotNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 22, 0, 1, 0, shanghai)))
    }

    @Test
    fun serverWindowMayNarrowButCannotBroadenTheBeijingBoundary() {
        val broadWindow = CheckInTimeWindow(
            windowMode = "semester_wide",
            dateRangeStart = null,
            dateRangeEnd = null,
            dailyStartTime = "00:00",
            dailyEndTime = "23:59",
            excludedDates = emptyList(),
            semesterDeadline = null
        )

        assertNotNull(broadWindow.canStartExercise(ZonedDateTime.of(2026, 7, 27, 5, 59, 59, 0, shanghai)))
        assertNull(broadWindow.canStartExercise(ZonedDateTime.of(2026, 7, 27, 21, 50, 0, 0, shanghai)))
        assertNotNull(broadWindow.canStartExercise(ZonedDateTime.of(2026, 7, 27, 22, 0, 1, 0, shanghai)))
    }

    @Test
    fun serverDateAndDailyWindowAreBothApplied() {
        val window = CheckInTimeWindow(
            windowMode = "specified_range",
            dateRangeStart = "2026-07-20",
            dateRangeEnd = "2026-07-31",
            dailyStartTime = "08:00",
            dailyEndTime = "20:00",
            excludedDates = emptyList(),
            semesterDeadline = "2026-08-01"
        )

        assertNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 12, 0, 0, 0, shanghai)))
        assertNotNull(window.canStartExercise(ZonedDateTime.of(2026, 7, 27, 21, 0, 0, 0, shanghai)))
        assertNotNull(window.canStartExercise(ZonedDateTime.of(2026, 8, 1, 12, 0, 0, 0, shanghai)))
    }

    @Test
    fun semesterWidePolicyStillEnforcesTheServerSuppliedSemesterDates() {
        val window = CheckInTimeWindow(
            windowMode = "semester_wide",
            dateRangeStart = "2026-02-23",
            dateRangeEnd = "2026-06-28",
            dailyStartTime = "06:00",
            dailyEndTime = "22:00",
            excludedDates = emptyList(),
            semesterDeadline = null
        )

        assertNotNull(window.canStartExercise(ZonedDateTime.of(2026, 2, 22, 12, 0, 0, 0, shanghai)))
        assertNull(window.canStartExercise(ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, shanghai)))
        assertNotNull(window.canStartExercise(ZonedDateTime.of(2026, 6, 29, 12, 0, 0, 0, shanghai)))
    }
}
