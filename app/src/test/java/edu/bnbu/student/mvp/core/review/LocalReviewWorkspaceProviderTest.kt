package edu.bnbu.student.mvp.core.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReviewWorkspaceProviderTest {
    @Test
    fun debugFixture_isSyntheticAndContainsNoBackendRepository() {
        val factory = requireNotNull(LocalReviewWorkspaceProvider.workspaceFactory)
        val workspace = factory()

        assertEquals("LOCAL-REVIEW-STUDENT", workspace.student.id)
        assertEquals("本地测试学生", workspace.student.name)
        assertTrue(workspace.student.email.endsWith(".invalid"))
        assertTrue(workspace.progress.source.contains("不来自真实 Backend"))
        assertEquals(16.0, workspace.progress.authoritativeTotalHours ?: -1.0, 0.0)
        assertFalse(workspace.courses.isEmpty())
        assertFalse(workspace.records.isEmpty())
        assertNull(workspace.progress.organizationCredit)
    }
}
