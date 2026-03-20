package com.xiaomo.androidforclaw.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxStatusFormatterTest {
    @Test
    fun fallbackMessage_mapsKnownSteps() {
        val status = TermuxStatus(true, false, true, true, false, false, true, true, TermuxSetupStep.TERMUX_API_NOT_INSTALLED, "Termux:API 未安装")
        assertEquals("Termux:API is not installed yet.", TermuxStatusFormatter.fallbackMessage(status))
    }

    @Test
    fun userFacingMessage_includesStatusAndFallback() {
        val status = TermuxStatus(true, true, true, true, false, false, true, true, TermuxSetupStep.SSHD_NOT_REACHABLE, "SSH 端口 8022 不可达")
        val text = TermuxStatusFormatter.userFacingMessage(status)
        assertTrue(text.contains("Termux is not ready:"))
        assertTrue(text.contains("SSH 端口 8022 不可达"))
        assertTrue(text.contains("sshd is not reachable on 127.0.0.1:8022."))
    }
}
