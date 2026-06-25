package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.core.tools.StringResultData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * R-CRON-DIAG-001 unit tests for `CronDiagnosticTools` (`diagnose_cron_streaming`).
 *
 * Each `@Test` corresponds to a TC-CRON-DIAG-001-x row in `docs/hermes-test-cases.md`.
 * Pure JVM — uses `logFileOverride` to feed mocked cron.log content; production
 * `CronFileLogger` is not exercised here.
 */
class CronDiagnosticToolsTest {

    private lateinit var tmpLog: File

    @Before
    fun setUp() {
        tmpLog = File.createTempFile("cron_diag_test_", ".log")
    }

    @After
    fun tearDown() {
        tmpLog.delete()
    }

    private fun mkTool(jobId: String? = null, tailKb: Int? = null): AITool {
        val params = mutableListOf<ToolParameter>()
        if (jobId != null) params += ToolParameter("job_id", jobId)
        if (tailKb != null) params += ToolParameter("tail_kb", tailKb.toString())
        return AITool(name = "diagnose_cron_streaming", parameters = params)
    }

    private fun invokeWith(content: String, jobId: String? = null, tailKb: Int? = null): String {
        tmpLog.writeText(content)
        val tools = CronDiagnosticTools(logFileOverride = tmpLog)
        val result = tools.diagnose(mkTool(jobId, tailKb))
        assertTrue(
            "TC-CRON-DIAG-001: diagnose should succeed for existing log file. error=${result.error}",
            result.success
        )
        return (result.result as StringResultData).value
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-a: missing cron.log
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-a missing cron log returns success=false with descriptive error`() {
        val ghost = File(tmpLog.parentFile, "definitely-does-not-exist-${System.nanoTime()}.log")
        val tools = CronDiagnosticTools(logFileOverride = ghost)
        val result = tools.diagnose(mkTool())
        assertFalse(
            "TC-CRON-DIAG-001-a: missing log file must yield success=false",
            result.success
        )
        assertNotNull(
            "TC-CRON-DIAG-001-a: error message required",
            result.error
        )
        assertTrue(
            "TC-CRON-DIAG-001-a: error must mention cron.log so the user understands what's missing. Got: ${result.error}",
            (result.error ?: "").contains("cron.log")
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-b: no `agent run start` -> rule#1
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-b absence of agent run start hits rule#1`() {
        val content = "2026-06-25 12:00:00.000 I/Other: unrelated line\n"
        val md = invokeWith(content)
        assertTrue(
            "TC-CRON-DIAG-001-b: must hit rule#1 — markdown was:\n$md",
            md.contains("rule#1")
        )
        assertTrue(
            "TC-CRON-DIAG-001-b: rule#1 explanation must mention 运行痕迹",
            md.contains("运行痕迹")
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-c: `agent run start` but no sidecar summary -> rule#2
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-c missing sidecar summary hits rule#2`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:01.000 W/CronAgentRunner: something happened
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-c: must hit rule#2 — markdown was:\n$md", md.contains("rule#2"))
        assertTrue("TC-CRON-DIAG-001-c: rule#2 must mention sidecar", md.contains("sidecar"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-d: chatIdMatched=0 / dispatchCalls=0 -> rule#3
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-d zero dispatch hits rule#3`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=5 chatIdMatched=0 assistantDeltas=0 dispatchCalls=0 paragraphDispatches=0 dispatchSuccess=0 streamingDelivered=false
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-d: must hit rule#3 — markdown was:\n$md", md.contains("rule#3"))
        assertTrue(
            "TC-CRON-DIAG-001-d: rule#3 must mention originPlatform / originChatId / dispatch_target",
            md.contains("originPlatform") || md.contains("originChatId") || md.contains("dispatch_target")
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-e: single paragraph -> rule#4
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-e single paragraph hits rule#4`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=3 chatIdMatched=3 assistantDeltas=1 dispatchCalls=1 paragraphDispatches=1 dispatchSuccess=1 streamingDelivered=true
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-e: must hit rule#4 — markdown was:\n$md", md.contains("rule#4"))
        assertTrue("TC-CRON-DIAG-001-e: rule#4 must mention 整段", md.contains("整段"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-f: dispatchCalls > dispatchSuccess -> rule#5
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-f partial dispatch failure hits rule#5`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:01.000 W/CronAgentRunner: streaming dispatch failed jobId=x turn=2 reason=ok=false
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=8 chatIdMatched=8 assistantDeltas=3 dispatchCalls=3 paragraphDispatches=3 dispatchSuccess=1 streamingDelivered=true
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-f: must hit rule#5 — markdown was:\n$md", md.contains("rule#5"))
        assertTrue("TC-CRON-DIAG-001-f: rule#5 must mention 派发失败", md.contains("派发失败"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-g: streamingDelivered=false but dispatchSuccess>0 -> rule#6
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-g streamingDelivered mismatch hits rule#6`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=5 chatIdMatched=5 assistantDeltas=2 dispatchCalls=2 paragraphDispatches=2 dispatchSuccess=2 streamingDelivered=false
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-g: must hit rule#6 — markdown was:\n$md", md.contains("rule#6"))
        assertTrue("TC-CRON-DIAG-001-g: rule#6 must mention 状态机", md.contains("状态机"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-h: `(empty response)` -> rule#7
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-h empty response hits rule#7`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:01.000 W/CronAgentRunner: strip emptied output jobId=x preStripLen=200 — substituting placeholder
            2026-06-25 12:00:01.500 I/CronAgentRunner: deliver (empty response) jobId=x
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=3 chatIdMatched=3 assistantDeltas=1 dispatchCalls=1 paragraphDispatches=1 dispatchSuccess=1 streamingDelivered=true
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-h: must hit rule#7 — markdown was:\n$md", md.contains("rule#7"))
        assertTrue("TC-CRON-DIAG-001-h: rule#7 must mention strip", md.contains("strip"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-i: healthy run -> rule#8
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-i healthy run hits rule#8`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=10 chatIdMatched=4 assistantDeltas=3 dispatchCalls=3 paragraphDispatches=5 dispatchSuccess=5 streamingDelivered=true
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        assertTrue("TC-CRON-DIAG-001-i: must hit rule#8 — markdown was:\n$md", md.contains("rule#8"))
        assertTrue(
            "TC-CRON-DIAG-001-i: rule#8 must mention PASS or 无异常",
            md.contains("PASS") || md.contains("无异常")
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-j: `job_id` parameter selects the correct block
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-j job_id parameter selects the correct block`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=GOOD name='r' promptLen=10
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=GOOD totalEvents=10 chatIdMatched=4 assistantDeltas=3 dispatchCalls=3 paragraphDispatches=5 dispatchSuccess=5 streamingDelivered=true
            2026-06-25 12:01:00.000 I/CronAgentRunner: agent run start jobId=BAD name='r' promptLen=10
            2026-06-25 12:01:01.000 W/CronAgentRunner: streaming dispatch failed jobId=BAD turn=2 reason=ok=false
            2026-06-25 12:01:02.000 I/CronAgentRunner: streaming sidecar summary jobId=BAD totalEvents=8 chatIdMatched=8 assistantDeltas=3 dispatchCalls=3 paragraphDispatches=3 dispatchSuccess=1 streamingDelivered=true
        """.trimIndent() + "\n"
        val mdBad = invokeWith(content, jobId = "BAD")
        assertTrue("TC-CRON-DIAG-001-j: job_id=BAD must hit rule#5 — markdown:\n$mdBad", mdBad.contains("rule#5"))
        assertFalse("TC-CRON-DIAG-001-j: job_id=BAD must NOT hit rule#8", mdBad.contains("rule#8"))
        assertTrue("TC-CRON-DIAG-001-j: heading must echo the requested jobId BAD", mdBad.contains("jobId=BAD"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-k: `tail_kb` parameter limits the read window
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-k tail_kb parameter limits read window`() {
        // Build: front 4KB contains healthy run; back 8KB contains only unrelated lines.
        val frontBlock = StringBuilder()
        frontBlock.append("2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=OLD name='r' promptLen=10\n")
        frontBlock.append("2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=OLD totalEvents=10 chatIdMatched=4 assistantDeltas=3 dispatchCalls=3 paragraphDispatches=5 dispatchSuccess=5 streamingDelivered=true\n")
        // pad front to ~4KB with filler that does NOT match any rule pattern
        while (frontBlock.length < 4 * 1024) {
            frontBlock.append("2026-06-25 12:00:03.000 I/Other: padding line A\n")
        }
        val backBlock = StringBuilder()
        // ~8KB of unrelated lines (no agent run start, no sidecar summary)
        while (backBlock.length < 8 * 1024) {
            backBlock.append("2026-06-25 12:30:00.000 I/Other: unrelated tail filler line B\n")
        }
        val content = frontBlock.toString() + backBlock.toString()
        // tail_kb=4 — only last ~4KB should be visible, so OLD's run is invisible
        val md = invokeWith(content, tailKb = 4)
        assertTrue(
            "TC-CRON-DIAG-001-k: with tail_kb=4 the early healthy run must NOT be parsed (would otherwise hit rule#8). markdown:\n${md.take(400)}",
            md.contains("rule#1")
        )
        assertTrue("TC-CRON-DIAG-001-k: rule#1 must mention 运行痕迹", md.contains("运行痕迹"))
    }

    // ---------------------------------------------------------------------
    // TC-CRON-DIAG-001-l: output structure has fixed sections
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-DIAG-001-l output markdown has fixed section structure`() {
        val content = """
            2026-06-25 12:00:00.000 I/CronAgentRunner: agent run start jobId=x name='r' promptLen=10
            2026-06-25 12:00:02.000 I/CronAgentRunner: streaming sidecar summary jobId=x totalEvents=10 chatIdMatched=4 assistantDeltas=3 dispatchCalls=3 paragraphDispatches=5 dispatchSuccess=5 streamingDelivered=true
        """.trimIndent() + "\n"
        val md = invokeWith(content)
        val sections = listOf(
            "## 诊断 jobId=",
            "### 关键计数器",
            "### 概率最大根因",
            "### 建议下一步",
            "### 原始关键行"
        )
        // Each section must exist
        sections.forEach { s ->
            assertTrue(
                "TC-CRON-DIAG-001-l: missing section `$s` in output markdown:\n$md",
                md.contains(s)
            )
        }
        // Sections must appear in order
        val indices = sections.map { md.indexOf(it) }
        for (i in 1 until indices.size) {
            assertTrue(
                "TC-CRON-DIAG-001-l: sections out of order. expected `${sections[i - 1]}` < `${sections[i]}`, " +
                    "got idx ${indices[i - 1]} vs ${indices[i]}",
                indices[i - 1] < indices[i]
            )
        }
        // sanity: jobId echoed
        assertTrue("TC-CRON-DIAG-001-l: jobId=x must be echoed in heading", md.contains("jobId=x"))
    }
}
