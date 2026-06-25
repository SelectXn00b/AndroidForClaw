package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.hermes.gateway.CronFileLogger
import java.io.File
import java.io.RandomAccessFile

/**
 * R-CRON-DIAG-001: agent-self-service diagnostic tool for the cron streaming chain
 * (R-CRON-STREAMING-001/002).  Reads the tail of `/sdcard/Download/Hermes/cron_logs/cron.log`
 * and renders a fixed-structure markdown report so the user can ask the agent
 * "诊断刚才那个 cron" in one sentence and get a root-cause hint without having to
 * remember the field names (`paragraphCount=`, `dispatchSuccess=`, etc.).
 *
 * **Does not** write to cron.log itself (keeps it read-only — the diagnostic tool
 * must not pollute the artifact it's diagnosing).
 *
 * Test ladder (each rule has a TC-CRON-DIAG-001-x in `hermes-test-cases.md`):
 *  - rule#1 missing `agent run start` → no trace
 *  - rule#2 no `streaming sidecar summary` → sidecar didn't start
 *  - rule#3 `dispatchCalls=0` or `chatIdMatched=0` → origin not injected
 *  - rule#4 `paragraphCount=1` / `paragraphDispatches==assistantDeltas` + success → model didn't follow hint
 *  - rule#5 `dispatchCalls > dispatchSuccess` → gateway adapter failed
 *  - rule#6 `streamingDelivered=false` but `dispatchSuccess>0` → state-machine bug
 *  - rule#7 `(empty response)` → strip emptied output (LLM behavior)
 *  - rule#8 healthy
 */
open class CronDiagnosticTools(
    /** Override for unit tests — defaults to the production cron.log path. */
    private val logFileOverride: File? = null
) {

    companion object {
        /** Default tail window — only read the last N KB of cron.log to keep response small. */
        const val DEFAULT_TAIL_KB: Int = 256

        /** Max raw key-lines echoed in the diagnostic output. */
        private const val MAX_RAW_LINES: Int = 8

        private val SUMMARY_REGEX = Regex(
            """streaming sidecar summary jobId=(\S+) totalEvents=(\d+) chatIdMatched=(\d+) """ +
                """assistantDeltas=(\d+) dispatchCalls=(\d+) paragraphDispatches=(\d+) """ +
                """dispatchSuccess=(\d+) streamingDelivered=(true|false)"""
        )

        private val AGENT_START_REGEX = Regex("""agent run start jobId=(\S+)""")
    }

    /**
     * Executor entry — registered via `AIToolHandler.registerTool("diagnose_cron_streaming", ...)`.
     */
    fun diagnose(tool: AITool): ToolResult {
        val jobIdParam = tool.parameters.find { it.name == "job_id" }?.value?.trim()?.takeIf { it.isNotEmpty() }
        val tailKb = tool.parameters.find { it.name == "tail_kb" }?.value?.trim()?.toIntOrNull()
            ?: DEFAULT_TAIL_KB

        val logFile = resolveLogFile()
        if (logFile == null || !logFile.exists() || !logFile.canRead()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "cron.log not found or unreadable at ${logFile?.absolutePath ?: "(path resolution failed)"}"
            )
        }

        val tail = readTail(logFile, tailKb.coerceAtLeast(1))
        val analysis = analyze(tail, jobIdParam)
        return ToolResult(
            toolName = tool.name,
            success = true,
            result = StringResultData(analysis)
        )
    }

    // -------------------------------------------------------------------------
    // analysis — pure function, easy to unit-test
    // -------------------------------------------------------------------------

    /**
     * Visible for unit tests.  Parses `text` (already-narrowed tail window) and
     * returns the fixed-structure markdown report.
     */
    fun analyze(text: String, jobIdParam: String?): String {
        // Locate the target window: either the explicit job_id block, or the last
        // `agent run start` in the tail.
        val starts = AGENT_START_REGEX.findAll(text).toList()
        if (starts.isEmpty()) {
            return renderRule1(text)
        }

        val targetMatch = if (jobIdParam != null) {
            starts.lastOrNull { it.groupValues[1] == jobIdParam }
                ?: return renderNoSuchJob(jobIdParam, text)
        } else {
            starts.last()
        }

        val jobId = targetMatch.groupValues[1]
        val windowStart = targetMatch.range.first
        // window ends at the next `agent run start` OR EOF
        val nextStartIdx = starts.firstOrNull { it.range.first > windowStart }?.range?.first
        val windowEnd = nextStartIdx ?: text.length
        val window = text.substring(windowStart, windowEnd)

        val startLineTime = extractStartTime(window)
        val summary = SUMMARY_REGEX.find(window)
        if (summary == null) {
            return renderRule2(jobId, startLineTime, window)
        }

        // parse counters
        val totalEvents = summary.groupValues[2].toLong()
        val chatIdMatched = summary.groupValues[3].toLong()
        val assistantDeltas = summary.groupValues[4].toLong()
        val dispatchCalls = summary.groupValues[5].toLong()
        val paragraphDispatches = summary.groupValues[6].toLong()
        val dispatchSuccess = summary.groupValues[7].toLong()
        val streamingDelivered = summary.groupValues[8] == "true"

        val counters = Counters(
            jobId = jobId,
            startTime = startLineTime,
            totalEvents = totalEvents,
            chatIdMatched = chatIdMatched,
            assistantDeltas = assistantDeltas,
            dispatchCalls = dispatchCalls,
            paragraphDispatches = paragraphDispatches,
            dispatchSuccess = dispatchSuccess,
            streamingDelivered = streamingDelivered
        )

        // priority-ordered rules (rule#1 / rule#2 already handled above)
        val hit: HitRule = when {
            // rule#7 (empty response) — check before rule#4/#5 because an empty
            // strip may still show dispatchSuccess=1 with placeholder
            window.contains("(empty response)") -> rule7(window)

            // rule#3: zero dispatch
            dispatchCalls == 0L || chatIdMatched == 0L -> rule3(counters)

            // rule#5: dispatch partial failure
            dispatchCalls > dispatchSuccess -> rule5(counters, window)

            // rule#6: streamingDelivered=false despite dispatchSuccess>0
            !streamingDelivered && dispatchSuccess > 0 -> rule6(counters)

            // rule#4: single paragraph — model didn't follow multi-message hint
            paragraphDispatches > 0 && paragraphDispatches == assistantDeltas &&
                dispatchSuccess > 0 && assistantDeltas <= 1L -> rule4(counters)

            // rule#8: healthy
            dispatchSuccess >= 1 && streamingDelivered -> rule8(counters)

            // fallback — treat as rule#2 style "something unexpected" but with counters
            else -> rule8Unknown(counters)
        }

        return renderMarkdown(counters, hit, window)
    }

    // -------------------------------------------------------------------------
    // rule renderers
    // -------------------------------------------------------------------------

    private data class Counters(
        val jobId: String,
        val startTime: String,
        val totalEvents: Long,
        val chatIdMatched: Long,
        val assistantDeltas: Long,
        val dispatchCalls: Long,
        val paragraphDispatches: Long,
        val dispatchSuccess: Long,
        val streamingDelivered: Boolean
    )

    private data class HitRule(val id: String, val cause: String, val nextStep: String)

    private fun rule3(c: Counters): HitRule = HitRule(
        id = "rule#3",
        cause = "sidecar 启动了但 0 派发（chatIdMatched=${c.chatIdMatched} dispatchCalls=${c.dispatchCalls}）",
        nextStep = "检查 cronjob 的 dispatch_target / originPlatform / originChatId 是否注入；或 @chatroom 群聊回退路径是否生效。"
    )

    private fun rule4(c: Counters): HitRule = HitRule(
        id = "rule#4",
        cause = "agent 单 turn 输出整段（paragraphDispatches=${c.paragraphDispatches}，没听 multi-message hint 留空行）",
        nextStep = "(a) 强化 cron prompt 里 \"先空行后正文\" 的示范；(b) 让 agent 用工具循环把任务拆多 turn。"
    )

    private fun rule5(c: Counters, window: String): HitRule {
        val reasonLine = window.lineSequence()
            .firstOrNull { it.contains("streaming dispatch failed") || it.contains("streaming dispatch threw") }
        val reasonHint = reasonLine?.let { " 具体 reason 行：$it" } ?: ""
        return HitRule(
            id = "rule#5",
            cause = "gateway adapter 派发失败（dispatchCalls=${c.dispatchCalls} dispatchSuccess=${c.dispatchSuccess}）。$reasonHint",
            nextStep = "看 cron.log 里 `streaming dispatch failed` / `streaming dispatch threw` 行的 reason 字段，定位 adapter 失败原因。"
        )
    }

    private fun rule6(c: Counters): HitRule = HitRule(
        id = "rule#6",
        cause = "sidecar 派发成功（dispatchSuccess=${c.dispatchSuccess}）但 streamingDelivered=false。状态机异常。",
        nextStep = "怀疑 sidecar 与主路 collect 之间的内存 visibility 或 cancel 时序问题；查 NonCancellable / drain 窗口逻辑。"
    )

    private fun rule7(window: String): HitRule {
        val stripLine = window.lineSequence().firstOrNull { it.contains("strip emptied output") || it.contains("(empty response)") }
        val hint = stripLine?.let { " 现场：$it" } ?: ""
        return HitRule(
            id = "rule#7",
            cause = "agent 输出被 strip 剥成空（典型：纯 <think> 或 unclosed think）。$hint",
            nextStep = "问题在 LLM 行为，不在 sidecar；检查 model / system prompt 是否引导出整段 thinking 而无可见 reply。"
        )
    }

    private fun rule8(c: Counters): HitRule = HitRule(
        id = "rule#8",
        cause = "cron streaming 链路本次 PASS，无异常（dispatchSuccess=${c.dispatchSuccess} streamingDelivered=true）。",
        nextStep = "若用户仍报问题：核对 originPlatform / originChatId 与实际 IM 账号是否对得上；或确认是否 R-CRON-STREAMING-002 段落数与用户期望一致。"
    )

    private fun rule8Unknown(c: Counters): HitRule = HitRule(
        id = "rule#8",
        cause = "字段组合未命中已知规则。dispatchCalls=${c.dispatchCalls} dispatchSuccess=${c.dispatchSuccess} streamingDelivered=${c.streamingDelivered}",
        nextStep = "回看原始关键行，必要时补充新规则。"
    )

    // -------------------------------------------------------------------------
    // rendering
    // -------------------------------------------------------------------------

    private fun renderRule1(text: String): String {
        val sb = StringBuilder()
        sb.append("## 诊断 jobId=(未找到) @ (未知)\n\n")
        sb.append("### 关键计数器\n")
        sb.append("- (cron.log 末尾窗口内未发现 agent run start)\n\n")
        sb.append("### 概率最大根因\n")
        sb.append("**[rule#1]** cron.log 里没有该 job 的运行痕迹（可能 alarm 没触发 / 进程被杀 / 写入权限失败）。\n\n")
        sb.append("### 建议下一步\n")
        sb.append("(1) 确认 cron 是否真的到点触发（CronExactAlarmReceiver）；")
        sb.append("(2) 检查 app 是否被系统杀（电池优化白名单）；")
        sb.append("(3) 增大 `tail_kb` 入参回看更早日志。\n\n")
        sb.append("### 原始关键行（最多 $MAX_RAW_LINES 行）\n")
        sb.append("```\n")
        sb.append(text.lineSequence().filter { it.isNotBlank() }.take(MAX_RAW_LINES).joinToString("\n"))
        sb.append("\n```\n")
        return sb.toString()
    }

    private fun renderNoSuchJob(jobId: String, text: String): String {
        val sb = StringBuilder()
        sb.append("## 诊断 jobId=$jobId @ (未找到)\n\n")
        sb.append("### 关键计数器\n")
        sb.append("- (window 内未找到匹配 jobId=$jobId 的 agent run start)\n\n")
        sb.append("### 概率最大根因\n")
        sb.append("**[rule#1]** cron.log 里没有该 job 的运行痕迹（jobId 拼错？或被 log rotation 截掉？）。\n\n")
        sb.append("### 建议下一步\n")
        sb.append("(1) 不带 `job_id` 让工具分析最近一次；")
        sb.append("(2) 加大 `tail_kb` 回看更早记录。\n\n")
        sb.append("### 原始关键行（最多 $MAX_RAW_LINES 行）\n")
        sb.append("```\n")
        sb.append(text.lineSequence().filter { AGENT_START_REGEX.containsMatchIn(it) }.take(MAX_RAW_LINES).joinToString("\n"))
        sb.append("\n```\n")
        return sb.toString()
    }

    private fun renderRule2(jobId: String, startTime: String, window: String): String {
        val sb = StringBuilder()
        sb.append("## 诊断 jobId=$jobId @ $startTime\n\n")
        sb.append("### 关键计数器\n")
        sb.append("- (sidecar summary 行未出现 —— sidecar 没跑完)\n\n")
        sb.append("### 概率最大根因\n")
        sb.append("**[rule#2]** sidecar 协程没启动或在 collect 前被 cancel。\n\n")
        sb.append("### 建议下一步\n")
        sb.append("看 cron.log 里 `headless agent invocation failed` / `NonRetriableException` / 异常堆栈，")
        sb.append("通常意味着 LLM 请求挂了或主路提前抛错。\n\n")
        sb.append("### 原始关键行（最多 $MAX_RAW_LINES 行）\n")
        sb.append("```\n")
        sb.append(pickKeyLines(window))
        sb.append("\n```\n")
        return sb.toString()
    }

    private fun renderMarkdown(c: Counters, hit: HitRule, window: String): String {
        val sb = StringBuilder()
        sb.append("## 诊断 jobId=${c.jobId} @ ${c.startTime}\n\n")
        sb.append("### 关键计数器\n")
        sb.append("- assistantDeltas=${c.assistantDeltas}\n")
        sb.append("- dispatchCalls=${c.dispatchCalls} paragraphDispatches=${c.paragraphDispatches}\n")
        sb.append("- dispatchSuccess=${c.dispatchSuccess}\n")
        sb.append("- streamingDelivered=${c.streamingDelivered}\n")
        sb.append("- chatIdMatched=${c.chatIdMatched}\n\n")
        sb.append("### 概率最大根因\n")
        sb.append("**[${hit.id}]** ${hit.cause}\n\n")
        sb.append("### 建议下一步\n")
        sb.append(hit.nextStep).append("\n\n")
        sb.append("### 原始关键行（最多 $MAX_RAW_LINES 行）\n")
        sb.append("```\n")
        sb.append(pickKeyLines(window))
        sb.append("\n```\n")
        return sb.toString()
    }

    private fun pickKeyLines(window: String): String {
        val keyTokens = listOf(
            "agent run start",
            "strip applied",
            "strip emptied output",
            "(empty response)",
            "streaming sidecar summary",
            "streaming dispatch failed",
            "streaming dispatch threw",
            "headless agent invocation failed"
        )
        return window.lineSequence()
            .filter { line -> keyTokens.any { line.contains(it) } }
            .take(MAX_RAW_LINES)
            .joinToString("\n")
    }

    private fun extractStartTime(window: String): String {
        // first line of the window starts with "yyyy-MM-dd HH:mm:ss.SSS I/Cron..."
        val firstLine = window.lineSequence().firstOrNull { AGENT_START_REGEX.containsMatchIn(it) } ?: return "(未知)"
        val tsLen = "yyyy-MM-dd HH:mm:ss.SSS".length
        return if (firstLine.length >= tsLen) firstLine.substring(0, tsLen) else "(未知)"
    }

    // -------------------------------------------------------------------------
    // tail read
    // -------------------------------------------------------------------------

    private fun resolveLogFile(): File? {
        val override = logFileOverride
        if (override != null) return override
        val path = CronFileLogger.getLogFilePath()
        if (path == "(unavailable)") return null
        return File(path)
    }

    private fun readTail(file: File, tailKb: Int): String {
        val tailBytes = tailKb.toLong() * 1024L
        val length = file.length()
        if (length <= tailBytes) {
            return file.readText(Charsets.UTF_8)
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val offset = length - tailBytes
                raf.seek(offset)
                val buf = ByteArray(tailBytes.toInt())
                var read = 0
                while (read < buf.size) {
                    val n = raf.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                // drop the first partial line so we don't half-parse a row
                val raw = String(buf, 0, read, Charsets.UTF_8)
                val firstNewline = raw.indexOf('\n')
                if (firstNewline < 0) raw else raw.substring(firstNewline + 1)
            }
        } catch (_: Throwable) {
            ""
        }
    }
}
