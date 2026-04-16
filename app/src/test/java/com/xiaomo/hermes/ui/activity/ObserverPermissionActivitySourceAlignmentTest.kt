package com.xiaomo.hermes.ui.activity

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ObserverPermissionActivitySourceAlignmentTest {

    // Resolve project root from Gradle's working directory (app/)
    private val projectRoot = File(System.getProperty("user.dir")!!).let {
        if (it.name == "app") it.parentFile else it
    }
    private val sourceFile = File(
        projectRoot, "extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/PermissionActivity.kt"
    )

    @Test
    fun permissionScreen_refreshesStatus_whenReturningFromSystemSettings() {
        val src = sourceFile.readText()
        assertTrue(src.contains("override fun onResume()"))
        assertTrue(src.contains("mainHandler.postDelayed({ checkPermissionsAsync("))
        assertTrue(src.contains("override fun onRestart()"))
        assertTrue(src.contains("override fun onWindowFocusChanged(hasFocus: Boolean)"))
        assertTrue(src.contains("if (hasFocus)"))
    }

    @Test
    fun accessibilityCheck_requiresGlobalSwitchAndServiceEntry() {
        val src = sourceFile.readText()
        assertTrue(src.contains("Settings.Secure.ACCESSIBILITY_ENABLED"))
        assertTrue(src.contains("accessibilityEnabled && serviceEnabled"))
    }

    @Test
    fun accessibilityEnabled_waitsForServiceConnectionAfterSettingsToggle() {
        val src = sourceFile.readText()
        assertTrue(src.contains("resolveAccessibilityEnabled()"))
        assertTrue(src.contains("AccessibilityBinderService.serviceInstance != null"))
        assertTrue(src.contains("repeat(8)"))
        assertTrue(src.contains("delay(250)"))
    }
}
