package edu.bnbu.student.mvp.feature.courses

import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.network.CourseJoinRequestBody
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectCourseJoinTest {
    @Test
    fun validatesRequiredIdentityFields() {
        assertNotNull(validateDirectCourseJoin("", "20260001", "male", "2026"))
        assertNotNull(validateDirectCourseJoin("Student", "bad", "male", "2026"))
        assertNotNull(validateDirectCourseJoin("Student", "20260001", "", "2026"))
        assertNotNull(validateDirectCourseJoin("Student", "20260001", "female", ""))
        assertNotNull(validateDirectCourseJoin("Student", "20260001", "female", "freshman"))
        assertNotNull(validateDirectCourseJoin("Student", "20260001", "female", "999"))
        assertNotNull(validateDirectCourseJoin("Student", "20260001", "female", "10000"))
        assertNotNull(validateDirectCourseJoin("Student", "20260001", "other", "2026"))
        assertNull(validateDirectCourseJoin("Student", "20260001", "male", "2021"))
        assertNull(validateDirectCourseJoin("Student", "20260001", "female", "2028"))
        assertNull(validateDirectCourseJoin("Student", "20260001", "female", "9999"))
    }

    @Test
    fun demoResponseCreatesAnActiveQrMembership() {
        val course = DemoStudentScanCourse
        val response = buildDemoCourseJoinResponse(
            course,
            CourseJoinRequestBody(
                studentName = "Student",
                studentNumber = "20260001",
                gender = "female",
                grade = "2026",
                inviteCode = DemoStudentScanInviteCode
            )
        )

        assertEquals(course.id, response.resolvedCourse()?.id)
        assertEquals("active", response.resolvedMembership()?.status)
        assertEquals("qr", response.resolvedMembership()?.joinMethod)
        assertEquals("20260001", response.resolvedStudent()?.studentNumber)
        assertEquals("PENDING_CONTACT_BINDING", response.resolvedStudent()?.accountStatus)
    }

    @Test
    fun reportsPreciseDuplicateAndNetworkFailures() {
        assertTrue(
            directJoinErrorMessage(ApiHttpException(409, "ALREADY_JOINED"))
                .contains("已经加入")
        )
        assertTrue(directJoinErrorMessage(IOException("offline")).contains("网络"))
        assertTrue(
            directJoinErrorMessage(ApiHttpException(409, "ACTIVE_COURSE_EXISTS"))
                .contains("其他体育课程")
        )
        assertTrue(
            directJoinErrorMessage(IllegalArgumentException("JOIN_RESPONSE_COURSE_MISMATCH"))
                .contains("不一致")
        )
    }
}
