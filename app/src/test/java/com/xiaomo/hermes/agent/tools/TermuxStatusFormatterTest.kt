package com.xiaomo.hermes.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxStatusFormatterTest {
    @Test
    fun fallbackMessage_mapsKnownSteps() {
        val status = TermuxStatus(true, false, false, true, TermuxSetupStep.SSHD_NOT_REACHABLE, "sshd not running")
        assertEquals("sshd is not running. Open Termux and run: sshd", TermuxStatusFormatter.fallbackMessage(status))
    }

    @Test
    fun userFacingMessage_includesStatusAndFallback() {
        val status = TermuxStatus(true, false, false, true, TermuxSetupStep.SSHD_NOT_REACHABLE, "SSH \u7aef\u53e3 8022 \u4e0d\u53ef\u8fbe")
        val text = TermuxStatusFormatter.userFacingMessage(status)
        assertTrue(text.contains("Termux is not ready:"))
        assertTrue(text.contains("sshd is not running"))
    }
}
