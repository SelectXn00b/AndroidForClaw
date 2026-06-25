package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-STREAMING-h..k (R-CRON-STREAMING-002): cron 触发的单 turn 长回复按段落
 * 兜底切片发送。R-CRON-STREAMING-001 已经保证"每个 agent loop turn 一条 IM bubble"，
 * 但 `HermesAgentLoop` 每个 turn 至多 emit 一个 `AssistantDelta` —— 当 agent 一个
 * turn 写完整段回复（典型：纯文本提醒任务，无工具），sidecar 只看到一个事件 ⇒ 微信
 * 收到一条整段消息，无视用户"分多条说"的明确要求。
 *
 * 解法（A3 hybrid）：
 *  - **Prompt 层引导**：`wrappedPrompt` 追加 bilingual multi-message hint 常量，
 *    告诉 agent "请在自然段落之间留空行（\n\n），IM 端会按段落分多条发"。
 *  - **Sidecar 段落兜底**：sidecar 收到 `AssistantDelta` 后，strip → trim →
 *    按 `\n\s*\n+` 分段 → 逐段 `dispatchOutgoing`，段间 `delay(INTER_PARAGRAPH_DELAY_MS)`。
 *
 * 与 R-CRON-STREAMING-001 字节级兼容：单段路径 == R-001 既有行为；群聊回退、
 * `streamingDelivered` 去重、`writeLocalChatNote` / `saveJobOutput` 均不变。
 *
 * Source-scan only —— 同 `CronStreamingDispatchWiringTest` 的理由：真链路依赖
 * 活的 `EnhancedAIService` + Android coroutines + 网络栈，pure-JVM 不可达。
 * 字面值断言（hint 常量、`Regex(...)` / `\n\s*\n` pattern、`INTER_PARAGRAPH_DELAY_MS`、
 * `sidecarParagraphDispatches`、`paragraphCount=`）足够防回归。
 */
class CronStreamingParagraphSplitWiringTest {

    private val source: String by lazy { stripKotlinComments(File(runnerPath()).readText()) }
    private val runBody: String by lazy { extractFunctionBody(source, "suspend fun run(") }

    // ---------------------------------------------------------------------
    // TC-CRON-STREAMING-h: wrappedPrompt contains bilingual multi-message hint
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-h wrappedPrompt contains bilingual multi-message hint`() {
        // (1) Hint 常量必须存在（命名锚点：MULTI_MESSAGE_HINT 或同义）
        val hasHintConst = source.contains("MULTI_MESSAGE_HINT") ||
            source.contains("MULTI_MSG_HINT") ||
            source.contains("MULTIPLE_MESSAGES_HINT")
        assertTrue(
            "TC-CRON-STREAMING-h: `CronAgentRunner.kt` must declare a file-scope hint constant " +
                "(named like `MULTI_MESSAGE_HINT`) carrying the bilingual multi-message instruction.",
            hasHintConst
        )

        // (2) Hint 内容必须含双语关键字：英文 `blank line` + 中文 `空行`
        //     这两条确保 hint 真的传达"用空行分段"的语义，不是空壳常量。
        assertTrue(
            "TC-CRON-STREAMING-h: hint constant must mention English `blank line` keyword so the " +
                "instruction is understandable to English-mode agents.",
            source.contains("blank line")
        )
        assertTrue(
            "TC-CRON-STREAMING-h: hint constant must mention Chinese `空行` keyword so the " +
                "instruction reaches Chinese-mode agents too.",
            source.contains("空行")
        )

        // (3) Hint 必须落进 wrappedPrompt 构造（在 CRON_CONTEXT_PREFIX_* 之后、rawPrompt 之前）
        //     —— 这是把 hint 注入 system context 区域的硬约束，不是污染 user prompt。
        assertTrue("TC-CRON-STREAMING-h: run() body not found.", runBody.isNotEmpty())
        val prefixEnIdx = runBody.indexOf("CRON_CONTEXT_PREFIX_EN")
        val prefixCnIdx = runBody.indexOf("CRON_CONTEXT_PREFIX_CN")
        val hintIdx = listOf(
            runBody.indexOf("MULTI_MESSAGE_HINT"),
            runBody.indexOf("MULTI_MSG_HINT"),
            runBody.indexOf("MULTIPLE_MESSAGES_HINT")
        ).filter { it >= 0 }.minOrNull() ?: -1
        val rawPromptAppendIdx = runBody.indexOf("append(rawPrompt)")

        assertTrue(
            "TC-CRON-STREAMING-h: hint constant must be referenced inside `run()` body to be " +
                "wired into wrappedPrompt. prefixEnIdx=$prefixEnIdx prefixCnIdx=$prefixCnIdx " +
                "hintIdx=$hintIdx",
            hintIdx >= 0
        )
        assertTrue(
            "TC-CRON-STREAMING-h: hint must appear AFTER both `CRON_CONTEXT_PREFIX_EN` and " +
                "`CRON_CONTEXT_PREFIX_CN` references so the cron context guard reads first. " +
                "prefixEnIdx=$prefixEnIdx prefixCnIdx=$prefixCnIdx hintIdx=$hintIdx",
            hintIdx > prefixEnIdx && hintIdx > prefixCnIdx
        )
        assertTrue(
            "TC-CRON-STREAMING-h: hint must appear BEFORE `append(rawPrompt)` so the hint lands " +
                "in the system context region, not after the user's prompt. hintIdx=$hintIdx " +
                "rawPromptAppendIdx=$rawPromptAppendIdx",
            rawPromptAppendIdx >= 0 && hintIdx in 0 until rawPromptAppendIdx
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-STREAMING-i: sidecar splits AssistantDelta by blank lines
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-i sidecar splits AssistantDelta by blank lines before dispatch`() {
        assertTrue("TC-CRON-STREAMING-i: run() body not found.", runBody.isNotEmpty())
        val sidecarStart = runBody.indexOf("AssistantDelta")
        assertTrue(
            "TC-CRON-STREAMING-i: `AssistantDelta` literal missing — R-CRON-STREAMING-001 " +
                "broken? Cannot evaluate paragraph-split layer.",
            sidecarStart >= 0
        )
        val sidecarSlice = runBody.substring(sidecarStart)

        // (1) 必须含 split / Regex 字面值（段落切片 API）
        val hasSplit = sidecarSlice.contains(".split(") || sidecarSlice.contains("Regex(")
        assertTrue(
            "TC-CRON-STREAMING-i: sidecar must call `.split(` or `Regex(` to break the stripped " +
                "AssistantDelta text into paragraphs.",
            hasSplit
        )

        // (2) 必须含连续空行 pattern（接受多种等价写法）
        //     pattern 可能 inline 在 sidecar 内，也可能定义在 file-scope 常量（如 `PARAGRAPH_REGEX`）
        //     被 sidecar 引用。后者更整洁，所以两边都接受 —— 只要 sidecar 引用了常量名 + 常量
        //     声明里有真实 pattern 字面值即可。
        val blankLinePatterns = listOf(
            "\\n\\s*\\n",       // \n\s*\n
            "\\R\\s*\\R",       // \R\s*\R (Kotlin Regex 跨行)
            "(?:\\r?\\n){2,}",  // (?:\r?\n){2,}
            "\\n\\n"            // 最朴素的双 \n
        )
        val sidecarHasInlinePattern = blankLinePatterns.any { sidecarSlice.contains(it) }
        val sidecarReferencesParagraphConst =
            sidecarSlice.contains("PARAGRAPH_REGEX") || sidecarSlice.contains("paragraphRegex")
        val sourceHasPatternInConst = blankLinePatterns.any { source.contains(it) }
        val hasBlankLinePattern = sidecarHasInlinePattern ||
            (sidecarReferencesParagraphConst && sourceHasPatternInConst)
        assertTrue(
            "TC-CRON-STREAMING-i: sidecar must use a blank-line regex pattern such as " +
                "`\\\\n\\\\s*\\\\n`, `\\\\R\\\\s*\\\\R`, `(?:\\\\r?\\\\n){2,}`, or `\\\\n\\\\n`. " +
                "Either inline in the sidecar block, OR via a file-scope `PARAGRAPH_REGEX` " +
                "constant referenced from the sidecar. Neither found. Sidecar slice head:\n" +
                sidecarSlice.take(2000),
            hasBlankLinePattern
        )

        // (3) 段落迭代后再 dispatchOutgoing：必须含 forEach / for ( / .map 之一
        val hasIteration = sidecarSlice.contains("forEach") ||
            sidecarSlice.contains("for (") ||
            sidecarSlice.contains(".map {") ||
            sidecarSlice.contains(".map(")
        assertTrue(
            "TC-CRON-STREAMING-i: sidecar must iterate the split paragraphs via `forEach` / " +
                "`for (` / `.map`, then dispatch each paragraph — otherwise the split is no-op.",
            hasIteration
        )

        // (4) R-CRON-STREAMING-001 既有约束依旧：dispatchMutex.withLock + 空段过滤
        assertTrue(
            "TC-CRON-STREAMING-i: sidecar must keep `dispatchMutex.withLock` (R-CRON-STREAMING-001-d " +
                "sequential dispatch). Paragraph layer must respect the same mutex.",
            sidecarSlice.contains("dispatchMutex.withLock") || sidecarSlice.contains(".withLock")
        )
        val hasBlankGuard = sidecarSlice.contains("isNotBlank()") ||
            sidecarSlice.contains("isNotEmpty()")
        assertTrue(
            "TC-CRON-STREAMING-i: sidecar must keep blank/empty guard (`isNotBlank()` / " +
                "`isNotEmpty()`) so empty paragraphs after split don't trigger empty IM sends.",
            hasBlankGuard
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-STREAMING-j: INTER_PARAGRAPH_DELAY_MS constant used between dispatches
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-j inter-paragraph delay constant is used between dispatches`() {
        // (1) 常量声明存在
        assertTrue(
            "TC-CRON-STREAMING-j: `CronAgentRunner.kt` must declare `INTER_PARAGRAPH_DELAY_MS` " +
                "constant (file-scope or object-scope).",
            source.contains("INTER_PARAGRAPH_DELAY_MS")
        )

        // (2) 值 >= 150ms（避免微信短时高频限流，与需求文档约定的下限对齐）
        val declRe = Regex("""INTER_PARAGRAPH_DELAY_MS\s*[:=][^\d]*(\d+)L?""")
        val match = declRe.find(source)
        assertTrue(
            "TC-CRON-STREAMING-j: must be able to extract `INTER_PARAGRAPH_DELAY_MS = <number>` " +
                "from the source so the value is unit-test-readable. Found: ${match?.value}",
            match != null
        )
        val value = match!!.groupValues[1].toLong()
        assertTrue(
            "TC-CRON-STREAMING-j: `INTER_PARAGRAPH_DELAY_MS` must be >= 150 (need a real gap to " +
                "avoid WeChat short-window rate-limiting). Found $value.",
            value >= 150L
        )

        // (3) sidecar 块内必须真的用上 delay(INTER_PARAGRAPH_DELAY_MS)
        assertTrue("TC-CRON-STREAMING-j: run() body not found.", runBody.isNotEmpty())
        val sidecarStart = runBody.indexOf("AssistantDelta")
        assertTrue("TC-CRON-STREAMING-j: AssistantDelta missing from run().", sidecarStart >= 0)
        val sidecarSlice = runBody.substring(sidecarStart)
        assertTrue(
            "TC-CRON-STREAMING-j: sidecar block must call `delay(INTER_PARAGRAPH_DELAY_MS)` — " +
                "otherwise paragraphs fire back-to-back and WeChat rate-limits / merges them.",
            sidecarSlice.contains("delay(INTER_PARAGRAPH_DELAY_MS")
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-STREAMING-k: paragraph counter exposed for observability
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-k sidecar logs paragraph counter for observability`() {
        // (1) 计数器名存在
        assertTrue(
            "TC-CRON-STREAMING-k: source must declare `sidecarParagraphDispatches` counter to " +
                "track paragraph-level dispatch volume alongside `dispatchCalls` / `dispatchSuccess`.",
            source.contains("sidecarParagraphDispatches")
        )

        // (2) 必须在 sidecar 块内自增（按字面值匹配 ++ 或 += 1）
        assertTrue("TC-CRON-STREAMING-k: run() body not found.", runBody.isNotEmpty())
        val sidecarStart = runBody.indexOf("AssistantDelta")
        val sidecarSlice = runBody.substring(sidecarStart.coerceAtLeast(0))
        val incrementsInSidecar = sidecarSlice.contains("sidecarParagraphDispatches.incrementAndGet()") ||
            sidecarSlice.contains("sidecarParagraphDispatches++") ||
            sidecarSlice.contains("sidecarParagraphDispatches += 1") ||
            sidecarSlice.contains("sidecarParagraphDispatches.getAndIncrement()")
        assertTrue(
            "TC-CRON-STREAMING-k: `sidecarParagraphDispatches` must be incremented (`++`, " +
                "`+= 1`, or `.incrementAndGet()`) inside the sidecar block, otherwise it stays " +
                "at 0 and the counter is dead.",
            incrementsInSidecar
        )

        // (3) 日志中必须含 `paragraphCount=` 字面值（便于 cron.log 排查）
        assertTrue(
            "TC-CRON-STREAMING-k: at least one `CronFileLogger` line in `CronAgentRunner.kt` must " +
                "include the `paragraphCount=` literal so that operators can pin down `几 turn × 几段`.",
            source.contains("paragraphCount=")
        )
    }

    // =====================================================================
    // helpers (mirror CronStreamingDispatchWiringTest)
    // =====================================================================

    private fun extractFunctionBody(text: String, signaturePrefix: String): String {
        val anchor = text.indexOf(signaturePrefix)
        if (anchor < 0) return ""
        val openBrace = text.indexOf('{', anchor)
        if (openBrace < 0) return ""
        var depth = 0
        var i = openBrace
        while (i < text.length) {
            val c = text[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return text.substring(openBrace, i + 1)
            }
            i++
        }
        return text.substring(openBrace)
    }

    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun runnerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        return alt.path
    }
}
