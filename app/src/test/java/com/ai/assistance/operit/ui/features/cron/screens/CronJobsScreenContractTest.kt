package com.ai.assistance.operit.ui.features.cron.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-063: defend the `CronJobsScreen.kt` contract via source-scan because
 * driving the Composable would require Compose runtime + Context.
 *
 *  - TC-UI-063-d: 5 core calls — `listJobs`, `pauseJob`, `resumeJob`,
 *    `removeJob`, `CronAgentRunner.run` — must all be wired.
 *  - TC-UI-063-e: `nav_cron_jobs` string resource is declared in the app
 *    `strings.xml` so the navigation label has a translation entry.
 *  - TC-UI-063-f: this screen must NOT expose a "create" action — creation
 *    flows through agent chat (`cronjob(action="create", ...)`).
 */
class CronJobsScreenContractTest {

    private val screenSource: String by lazy { File(screenPath()).readText() }
    private val screenCode: String by lazy { stripComments(screenSource) }
    private val stringsXml: String by lazy { File(stringsPath()).readText() }

    // -------- TC-UI-063-d --------
    @Test
    fun `TC-UI-063-d screen wires the five core cron calls`() {
        // Imports + call-sites for each operation.
        val expected = listOf(
            "import com.xiaomo.hermes.hermes.cron.listJobs"            to "listJobs(",
            "import com.xiaomo.hermes.hermes.cron.pauseJob"            to "pauseJob(",
            "import com.xiaomo.hermes.hermes.cron.resumeJob"           to "resumeJob(",
            "import com.xiaomo.hermes.hermes.cron.removeJob"           to "removeJob(",
            "import com.ai.assistance.operit.core.cron.CronAgentRunner" to "CronAgentRunner.run(",
        )
        for ((importStmt, callSite) in expected) {
            assertTrue(
                "TC-UI-063-d: CronJobsScreen.kt missing `$importStmt`",
                screenSource.contains(importStmt)
            )
            assertTrue(
                "TC-UI-063-d: CronJobsScreen.kt missing call-site `$callSite`",
                screenSource.contains(callSite)
            )
        }
    }

    // -------- TC-UI-063-e --------
    @Test
    fun `TC-UI-063-e nav_cron_jobs string resource declared`() {
        assertTrue(
            "TC-UI-063-e: missing <string name=\"nav_cron_jobs\">…</string> in app strings.xml",
            Regex("""<string\s+name\s*=\s*"nav_cron_jobs"\s*>[^<]+</string>""")
                .containsMatchIn(stringsXml)
        )
    }

    // -------- TC-UI-063-f --------
    @Test
    fun `TC-UI-063-f screen does not expose a create action`() {
        // Strip comments first so the doc comment that mentions
        // `cronjob(action="create", ...)` as documentation does not
        // count as a UI-side create action.
        // No direct `addJob(` call in real code.
        assertFalse(
            "TC-UI-063-f: CronJobsScreen must NOT call `addJob(` (creation goes through agent).",
            screenCode.contains("addJob(")
        )
        // No agent-tool create dispatch in real code.
        assertFalse(
            "TC-UI-063-f: CronJobsScreen must NOT issue `cronjob(action = \"create\"` " +
                "(creation goes through natural-language agent chat, not a UI button).",
            Regex("""cronjob\s*\(\s*action\s*=\s*"create"""").containsMatchIn(screenCode)
        )
    }

    private fun screenPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/features/cron/screens/CronJobsScreen.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/features/cron/screens/CronJobsScreen.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate CronJobsScreen.kt — cwd=${File(".").absolutePath}")
    }

    private fun stringsPath(): String {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate strings.xml — cwd=${File(".").absolutePath}")
    }

    /** Remove `/* ... */` block comments and `// ...` line comments. */
    private fun stripComments(src: String): String {
        // Block comments first (non-greedy, multi-line).
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(src, "")
        // Then line comments.
        return noBlock.lineSequence()
            .map { line ->
                val idx = line.indexOf("//")
                if (idx >= 0) line.substring(0, idx) else line
            }
            .joinToString("\n")
    }
}
