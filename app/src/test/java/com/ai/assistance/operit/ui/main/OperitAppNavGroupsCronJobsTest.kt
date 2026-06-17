package com.ai.assistance.operit.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-063 / TC-UI-063-c: `OperitApp.kt` must register `NavItem.CronJobs` in
 * the tools navigation group (alongside `Workflow`, `Toolbox`,
 * `ShizukuCommands`) so the sidebar entry is reachable, and it must NOT
 * appear in the AI features group or the system group.
 *
 * Source-scan defends the registration shape because driving the actual
 * NavGroup list would require a Compose runtime + Context.
 */
class OperitAppNavGroupsCronJobsTest {

    private val source: String by lazy { File(operitAppPath()).readText() }

    // -------- TC-UI-063-c --------
    @Test
    fun `TC-UI-063-c CronJobs registered in tools group only`() {
        val toolsGroup = extractGroup(R_NAV_GROUP_TOOLS)
            ?: error("TC-UI-063-c: cannot locate `NavGroup(R.string.nav_group_tools, ...)` block")
        val aiGroup = extractGroup(R_NAV_GROUP_AI_FEATURES)
            ?: error("TC-UI-063-c: cannot locate `NavGroup(R.string.nav_group_ai_features, ...)` block")
        val systemGroup = extractGroup(R_NAV_GROUP_SYSTEM)
            ?: error("TC-UI-063-c: cannot locate `NavGroup(R.string.nav_group_system, ...)` block")

        assertTrue(
            "TC-UI-063-c: NavItem.CronJobs must be registered in the tools group " +
                "(R.string.nav_group_tools). Tools block was:\n$toolsGroup",
            toolsGroup.contains("NavItem.CronJobs")
        )

        assertFalse(
            "TC-UI-063-c: NavItem.CronJobs must NOT be registered in the AI features " +
                "group (R.string.nav_group_ai_features).",
            aiGroup.contains("NavItem.CronJobs")
        )
        assertFalse(
            "TC-UI-063-c: NavItem.CronJobs must NOT be registered in the system " +
                "group (R.string.nav_group_system).",
            systemGroup.contains("NavItem.CronJobs")
        )
    }

    /** Extract the body of `NavGroup(<groupRes>, listOf(...))` for inspection. */
    private fun extractGroup(groupRes: String): String? {
        val needle = "NavGroup("
        var idx = 0
        while (true) {
            val start = source.indexOf(needle, idx)
            if (start == -1) return null
            // Walk balanced parens to find this NavGroup's full text.
            var depth = 0
            var i = start
            while (i < source.length) {
                val c = source[i]
                if (c == '(') depth++
                else if (c == ')') {
                    depth--
                    if (depth == 0) {
                        val block = source.substring(start, i + 1)
                        if (block.contains(groupRes)) return block
                        idx = i + 1
                        break
                    }
                }
                i++
            }
            if (i >= source.length) return null
        }
    }

    private fun operitAppPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/main/OperitApp.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/main/OperitApp.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate OperitApp.kt — cwd=${File(".").absolutePath}")
    }

    private companion object {
        const val R_NAV_GROUP_TOOLS = "R.string.nav_group_tools"
        const val R_NAV_GROUP_AI_FEATURES = "R.string.nav_group_ai_features"
        const val R_NAV_GROUP_SYSTEM = "R.string.nav_group_system"
    }
}
