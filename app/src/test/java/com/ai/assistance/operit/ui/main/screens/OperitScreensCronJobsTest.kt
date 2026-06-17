package com.ai.assistance.operit.ui.main.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-063: `OperitScreens.kt` must (1) declare a `Screen.CronJobs` data
 * object that mounts `CronJobsScreen()` and (2) wire `NavItem.CronJobs ->
 * Screen.CronJobs` in `OperitRouter.getScreenForNavItem` so tapping the
 * sidebar entry navigates into the new screen.
 *
 * Source-scan: instantiating `Screen.CronJobs.Content(...)` would require
 * a real `NavController` + Compose runtime; the structural shape captured
 * here defends the navigation contract.
 */
class OperitScreensCronJobsTest {

    private val source: String by lazy { File(screensPath()).readText() }

    // -------- TC-UI-063-b --------
    @Test
    fun `TC-UI-063-b screen and router mapping declared`() {
        // (1) The Screen.CronJobs data object must reference NavItem.CronJobs
        //     and the new title resource so the top bar shows the right label.
        assertTrue(
            "TC-UI-063-b: missing `data object CronJobs : Screen(navItem = NavItem.CronJobs, ...)`",
            Regex(
                """data\s+object\s+CronJobs\s*:\s*Screen\s*\(\s*navItem\s*=\s*NavItem\.CronJobs"""
            ).containsMatchIn(source)
        )

        // (1.b) Title resource hooked up.
        assertTrue(
            "TC-UI-063-b: Screen.CronJobs must reference R.string.nav_cron_jobs as titleRes",
            Regex(
                """data\s+object\s+CronJobs\s*:\s*Screen\s*\([^)]*titleRes\s*=\s*R\.string\.nav_cron_jobs"""
            ).containsMatchIn(source)
        )

        // (1.c) The Content() body must mount the new Composable.
        assertTrue(
            "TC-UI-063-b: Screen.CronJobs.Content() must call CronJobsScreen()",
            source.contains("CronJobsScreen()")
        )

        // (1.d) The CronJobsScreen import wires the screen Composable.
        assertTrue(
            "TC-UI-063-b: must import com.ai.assistance.operit.ui.features.cron.screens.CronJobsScreen",
            source.contains("import com.ai.assistance.operit.ui.features.cron.screens.CronJobsScreen")
        )

        // (2) Router maps NavItem.CronJobs to Screen.CronJobs (otherwise the
        //     sidebar tap would fall through to the default `Screen.AiChat`).
        assertTrue(
            "TC-UI-063-b: missing `NavItem.CronJobs -> Screen.CronJobs` in OperitRouter",
            Regex(
                """NavItem\.CronJobs\s*->\s*Screen\.CronJobs"""
            ).containsMatchIn(source)
        )
    }

    private fun screensPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate OperitScreens.kt — cwd=${File(".").absolutePath}")
    }
}
