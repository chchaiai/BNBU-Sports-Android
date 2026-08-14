package edu.bnbu.student.mvp.feature.courses

import edu.bnbu.student.mvp.core.network.ApiHttpException
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
    fun enforcesContractFieldBoundaries() {
        assertNull(validateDirectCourseJoin("S".repeat(64), "A".repeat(32), "male", "2026"))
        assertNotNull(validateDirectCourseJoin("S".repeat(65), "20260001", "male", "2026"))
        assertNotNull(validateDirectCourseJoin("Student", "A".repeat(33), "male", "2026"))
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
