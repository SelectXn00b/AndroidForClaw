package com.xiaomo.hermes.agent.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class GetViewTreeFormatTest {
    @Test
    fun formatNode_includesResourceId() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/tools/GetViewTreeSkill.kt").readText()
        assertTrue("formatNode should output resourceId", source.contains("id=\$resId"))
    }

    @Test
    fun formatNode_includesBounds() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/tools/GetViewTreeSkill.kt").readText()
        assertTrue("formatNode should output bounds", source.contains("bounds="))
    }

    @Test
    fun formatNode_includesCenter() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/tools/GetViewTreeSkill.kt").readText()
        assertTrue("formatNode should output center coords", source.contains("center="))
    }

    @Test
    fun formatNode_includesScrollable() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/tools/GetViewTreeSkill.kt").readText()
        assertTrue("formatNode should output scrollable state", source.contains("可滚动"))
    }
}
