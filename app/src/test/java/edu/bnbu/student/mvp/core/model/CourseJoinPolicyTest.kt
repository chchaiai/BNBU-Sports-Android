package edu.bnbu.student.mvp.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseJoinPolicyTest {
    @Test
    fun allowsANewJoinWhenThereIsNoCurrentEnrollmentOrRequest() {
        assertTrue(StudentWorkspace.empty().canStartNewCourseJoin())
    }

    @Test
    fun blocksANewJoinWhenTheCurrentSemesterAlreadyHasAnEnrollment() {
        val workspace = StudentWorkspace.empty().copy(courses = listOf(currentCourse()))

        assertFalse(workspace.canStartNewCourseJoin())
    }

    @Test
    fun historicalEnrollmentDoesNotBlockANewSemester() {
        val workspace = StudentWorkspace.empty().copy(
            courses = listOf(currentCourse().copy(isCurrent = false, enrollmentStatus = "completed"))
        )

        assertTrue(workspace.canStartNewCourseJoin())
    }

    @Test
    fun pendingOrApprovedRequestBlocksANewJoinUntilCourseDataRefreshes() {
        val pending = StudentWorkspace.empty().copy(
            courseJoinRequest = joinRequest(JoinRequestStatus.PENDING)
        )
        val approved = StudentWorkspace.empty().copy(
            courseJoinRequest = joinRequest(JoinRequestStatus.ACTIVE)
        )

        assertFalse(pending.canStartNewCourseJoin())
        assertFalse(approved.canStartNewCourseJoin())
    }

    private fun currentCourse() = Course(
        id = "course-1",
        code = "PE101",
        section = "01",
        name = "Physical Education",
        semester = "2026 Spring",
        students = 0,
        pending = 0,
        completion = 0,
        missing = 0,
        deadline = "",
        teacher = "Teacher"
    )

    private fun joinRequest(status: JoinRequestStatus) = CourseJoinRequest(
        id = "request-1",
        inviteCode = "INVITE-1",
        courseName = "Physical Education",
        courseCode = "PE101",
        section = "01",
        teacherName = "Teacher",
        semester = "2026 Spring",
        studentName = "Student",
        studentNumber = "20260001",
        email = "student@example.invalid",
        status = status,
        reviewComment = "",
        submittedAt = "2026-01-01T00:00:00Z",
        reviewedAt = null
    )
}
