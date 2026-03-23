package com.xiaomo.androidforclaw.agent.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxStatusModelTest {
    @Test
    fun ready_onlyWhenAllCriticalFlagsPass() {
        val ready = TermuxStatus(true, true, true, true, TermuxSetupStep.READY, "ok")
        val blocked = TermuxStatus(true, false, false, true, TermuxSetupStep.SSHD_NOT_REACHABLE, "blocked")
        assertTrue(ready.ready)
        assertFalse(blocked.ready)
    }
}
