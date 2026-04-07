package com.xiaomo.androidforclaw.agent.context

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTimeSectionTest {
    @Test
    fun contextBuilderSource_containsSessionStatusHintInTimeSection() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/context/SystemPrompt.kt").readText()
        assertTrue(source.contains("## Current Date & Time"))
        assertTrue(source.contains("If you need the current date, time, or day of week, run session_status (📊 session_status)."))
    }
}
