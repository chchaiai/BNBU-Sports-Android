package edu.bnbu.student.mvp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemModeTest {
    @Test
    fun parsesServerModesAndFallsBackToNormal() {
        assertEquals(SystemMode.READ_ONLY, SystemMode.from("read_only"))
        assertEquals(SystemMode.MAINTENANCE, SystemMode.from("MAINTENANCE"))
        assertEquals(SystemMode.NORMAL, SystemMode.from("unknown"))
        assertEquals(SystemMode.NORMAL, SystemMode.from(null))
    }

    @Test
    fun onlyNormalAllowsWrites() {
        assertFalse(SystemMode.NORMAL.blocksWrites)
        assertTrue(SystemMode.READ_ONLY.blocksWrites)
        assertTrue(SystemMode.MAINTENANCE.blocksWrites)
    }
}
