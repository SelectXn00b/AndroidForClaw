package com.ai.assistance.operit.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-063: `NavItem` sealed class must declare a `CronJobs` entry that
 * names the new sidebar slot for the cron jobs management screen.
 *
 * Source-scan because instantiating the NavItem at test time wouldn't
 * verify icon / route / titleResId triple match in the source — we want
 * to defend the literal declaration shape.
 */
class NavItemCronJobsTest {

    private val source: String by lazy { File(navItemPath()).readText() }

    // -------- TC-UI-063-a --------
    @Test
    fun `TC-UI-063-a CronJobs nav item declared`() {
        // The declaration must:
        //   - Be inside the NavItem sealed class (`object CronJobs : NavItem(...)`)
        //   - Use the route literal "cronjobs"
        //   - Reference R.string.nav_cron_jobs (so it picks up the locale string
        //     added in strings.xml — see TC-UI-063-e)
        //   - Use the Material `Icons.Default.Schedule` icon (semantic match
        //     for "scheduled / cron" concept)
        assertTrue(
            "TC-UI-063-a: missing `object CronJobs : NavItem(...)` declaration",
            Regex(
                """object\s+CronJobs\s*:\s*NavItem\s*\(\s*"cronjobs"\s*,\s*R\.string\.nav_cron_jobs\s*,\s*Icons\.Default\.Schedule\s*\)"""
            ).containsMatchIn(source)
        )

        // Defense: the Schedule icon must be imported (otherwise the file
        // would not compile, but make the import requirement explicit).
        assertTrue(
            "TC-UI-063-a: must import androidx.compose.material.icons.filled.Schedule",
            source.contains("import androidx.compose.material.icons.filled.Schedule")
        )
    }

    private fun navItemPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/common/NavItem.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/common/NavItem.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate NavItem.kt — cwd=${File(".").absolutePath}")
    }
}
