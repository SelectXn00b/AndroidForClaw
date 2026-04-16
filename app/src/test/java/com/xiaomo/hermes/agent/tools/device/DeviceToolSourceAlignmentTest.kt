package com.xiaomo.hermes.agent.tools.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceToolSourceAlignmentTest {

    // Resolve project root from Gradle's working directory (app/)
    private val projectRoot = File(System.getProperty("user.dir")!!).let {
        if (it.name == "app") it.parentFile else it
    }
    private val sourceFile = File(
        projectRoot, "app/src/main/java/com/xiaomo/androidforclaw/agent/tools/device/DeviceTool.kt"
    )

    @Test
    fun tapAction_usesAccessibilityProxy_notShellInputTap() {
        val src = sourceFile.readText()
        val tapSection = src.substringAfter("private suspend fun executeTap").substringBefore("private suspend fun executeType")
        assertTrue(tapSection.contains("AccessibilityProxy.tap(x, y)"))
        assertFalse(tapSection.contains("input tap"))
    }

    @Test
    fun longPressAction_usesAccessibilityProxy_notShellSwipeHold() {
        val src = sourceFile.readText()
        val section = src.substringAfter("private suspend fun executeLongPress").substringBefore("private suspend fun executeScroll")
        assertTrue(section.contains("AccessibilityProxy.longPress(x, y)"))
        assertFalse(section.contains("input swipe"))
    }

    @Test
    fun scrollAndSwipe_useAccessibilityProxy() {
        val src = sourceFile.readText()
        val scrollSection = src.substringAfter("private suspend fun executeScroll").substringBefore("private suspend fun executeSwipe")
        val swipeSection = src.substringAfter("private suspend fun executeSwipe").substringBefore("private suspend fun executeWait")
        assertTrue(scrollSection.contains("AccessibilityProxy.swipe("))
        assertTrue(swipeSection.contains("AccessibilityProxy.swipe("))
    }
}
