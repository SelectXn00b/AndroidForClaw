package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-011 (2026-06-14)：Gateway 处理消息时向用户发送 typing 提示的 wiring 守卫。
 *
 * **背景**：Python 上游 `gateway/platforms/base.py:1431-1475` 的 `_keep_typing` 后台循环 +
 * `:1812-1826` 的 `typing_task = asyncio.create_task(...)` lifecycle，在 Kotlin 侧已就位 80%：
 * - `Base.kt:313` 已声明 `open suspend fun sendTyping`。
 * - `Telegram.kt:662-687` 已正确 override `sendTyping`（POST sendChatAction action=typing）。
 * - `Base.kt:942-955` 已实现 `BasePlatformAdapter._keepTyping(chatId, intervalMs=2000L)` 扩展。
 * - **缺口**：`Run.kt::_handleMessage` 两处 `runner(...)` 调用（主入口 + pending-event 循环）
 *   都没用 `coroutineScope { launch { _keepTyping } }` 包住。本类守住"接通"。
 *
 * **测试策略**：源码字面值扫描 + 函数体局部正则。运行时行为（"消息发后 2s 内 chat 出现 typing 提示"）
 * 由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer，`sendChatAction` HTTP 行为
 * 由真 Telegram bot E2E 验证）。
 *
 * 对应 TC-GW-011-a..e（详见 docs/hermes-test-cases.md）。
 */
class RunTypingIndicatorWiringTest {

    private val runSource: String by lazy { File(runKtPath()).readText() }
    private val telegramSource: String by lazy { File(telegramKtPath()).readText() }
    private val baseSource: String by lazy { File(baseKtPath()).readText() }
    private val feishuSource: String by lazy { File(feishuKtPath()).readText() }
    private val weixinSource: String by lazy { File(weixinKtPath()).readText() }
    private val controllerSource: String? by lazy {
        val f = File(controllerKtPath())
        if (f.exists()) f.readText() else null
    }

    /**
     * 抓出 `_handleMessage` 函数体（从 `private suspend fun _handleMessage(` 到下一个
     * `private suspend fun ` / `private fun ` / `suspend fun ` / 顶层 `}` 之前）。
     */
    private val handleMessageBody: String by lazy {
        val startMarker = "private suspend fun _handleMessage(event: MessageEvent)"
        val startIdx = runSource.indexOf(startMarker)
        if (startIdx < 0) error("Cannot find `$startMarker` in Run.kt")
        // 找下一个同级 `private suspend fun ` / `private fun ` / `suspend fun `（行首带 4 空格缩进）
        val rest = runSource.substring(startIdx)
        val endRegex = Regex(
            """\n\s{4}(?:private\s+)?(?:suspend\s+)?fun\s+(?!_handleMessage\b)\w""")
        val match = endRegex.find(rest, startIndex = startMarker.length)
        if (match != null) rest.substring(0, match.range.first) else rest
    }

    /**
     * TC-GW-011-a: 主入口 `runner(event.text, ...)` 调用必须被 `_keepTyping(` 包裹。
     *
     * 检查：
     * - `_handleMessage` 函数体含 `runner(... event.text ...)` 调用（确保抓对了函数；允许多行格式化）
     * - 同函数体含 `_keepTyping(` 字面值
     * - `_keepTyping(` 第一次出现 < `runner(... event.text` 第一次出现（顺序：先 launch typing，再调 runner）
     * - 同函数体含 `coroutineScope`（结构化并发包装）
     * - 同函数体含 `typingJob` 变量名（约定俗成，便于阅读）
     * - 同函数体含 `cancel(` 调用（finally 中取消 typing job）
     */
    @Test
    fun `TC-GW-011-a main runner call wrapped with _keepTyping`() {
        assertTrue(
            "Run.kt 必须含 `_handleMessage(event: MessageEvent)` 函数。",
            handleMessageBody.isNotEmpty()
        )
        // `runner(...)` 主入口调用（允许多行格式化：`runner(\n  event.text,...)`）
        val mainRunnerRegex = Regex("""\brunner\s*\(\s*event\.text\b""")
        val mainRunnerMatch = mainRunnerRegex.find(handleMessageBody)
        assertTrue(
            "_handleMessage 函数体必须仍含主入口 `runner(event.text, ...)` 调用（允许多行格式化）。" +
                "若已重命名，请同步更新本测试。\n" +
                "实际函数体（前 4000 字符）:\n${handleMessageBody.take(4000)}",
            mainRunnerMatch != null
        )
        assertTrue(
            "R-GW-011: _handleMessage 必须含 `_keepTyping(` 调用 —— " +
                "把主入口 `runner(...)` 调用包到 `coroutineScope { launch { adapter._keepTyping(chatId) } ... finally { cancel } }`。\n" +
                "实际函数体（前 4000 字符）:\n${handleMessageBody.take(4000)}",
            handleMessageBody.contains("_keepTyping(")
        )
        val keepTypingIdx = handleMessageBody.indexOf("_keepTyping(")
        val mainRunnerIdx = mainRunnerMatch!!.range.first
        assertTrue(
            "R-GW-011: `_keepTyping(` 第一次出现位置 ($keepTypingIdx) 必须早于 " +
                "主入口 `runner(event.text` 第一次出现位置 ($mainRunnerIdx) —— " +
                "证明 launch typing 在 runner 调用之前。",
            keepTypingIdx in 0 until mainRunnerIdx
        )
        assertTrue(
            "R-GW-011: _handleMessage 必须含 `coroutineScope` 字面值 —— 结构化并发包装 launch+cancel。",
            handleMessageBody.contains("coroutineScope")
        )
        assertTrue(
            "R-GW-011: _handleMessage 必须含 `typingJob` 变量名 —— " +
                "约定俗成的 typing launch 句柄变量名，便于阅读 / grep。",
            handleMessageBody.contains("typingJob")
        )
        assertTrue(
            "R-GW-011: _handleMessage 必须含 `cancel(` 调用 —— " +
                "finally 中取消 typing job，保证 runner 抛错 / 完成 / 中断时 typing 都能停。",
            handleMessageBody.contains("cancel(")
        )
    }

    /**
     * TC-GW-011-b: pending-event 循环里的 `runner(pendingEvent.text, ...)` 也必须被包住。
     *
     * 检查：
     * - `_handleMessage` 函数体含 `runner(... pendingEvent.text ...)` 调用（允许多行格式化）
     * - `_keepTyping(` 在函数体中至少出现 2 次（主入口 + pending-event）
     * - pending 部分含对 `pendingEvent.source.chatId` 的引用作为 `_keepTyping` 入参
     *   （typing 跟随当前正在处理的事件，不是初始 event 的 chatId）
     */
    @Test
    fun `TC-GW-011-b pending event runner call wrapped with _keepTyping`() {
        // pending 入口 `runner(...)` 调用（允许多行格式化）
        val pendingRunnerRegex = Regex("""\brunner\s*\(\s*pendingEvent\.text\b""")
        val pendingRunnerMatch = pendingRunnerRegex.find(handleMessageBody)
        assertTrue(
            "_handleMessage 必须仍含 pending-event `runner(pendingEvent.text, ...)` 调用（允许多行格式化）。" +
                "若已重命名，请同步更新本测试。",
            pendingRunnerMatch != null
        )
        // 至少出现 2 次 `_keepTyping(` —— 一次主入口，一次 pending-event 循环
        val keepTypingCount = Regex("""_keepTyping\(""").findAll(handleMessageBody).count()
        assertTrue(
            "R-GW-011: _handleMessage 必须出现至少 2 次 `_keepTyping(` 调用（主入口 + pending-event 循环），" +
                "实际出现 $keepTypingCount 次。\n" +
                "实际函数体（前 6000 字符）:\n${handleMessageBody.take(6000)}",
            keepTypingCount >= 2
        )
        // 抓 pending 段：从 `runner(\n  pendingEvent.text,` 起点往前 2000 字符当作 pending 段
        val pendingRunnerIdx = pendingRunnerMatch!!.range.first
        val pendingSegmentStart = (pendingRunnerIdx - 2000).coerceAtLeast(0)
        val pendingSegmentEnd = (pendingRunnerIdx + 200).coerceAtMost(handleMessageBody.length)
        val pendingSegment = handleMessageBody.substring(pendingSegmentStart, pendingSegmentEnd)
        assertTrue(
            "R-GW-011: pending-event 循环段（pending runner 之前的 ~2000 字符）" +
                "必须含 `_keepTyping(` 调用 —— pending 那次也要 launch typing。\n" +
                "实际 pending 段:\n$pendingSegment",
            pendingSegment.contains("_keepTyping(")
        )
        assertTrue(
            "R-GW-011: pending-event 循环段必须含 `pendingEvent.source.chatId` —— " +
                "_keepTyping 入参用 pending 事件的 chatId，不是初始 event 的。\n" +
                "实际 pending 段:\n$pendingSegment",
            pendingSegment.contains("pendingEvent.source.chatId")
        )
    }

    /**
     * TC-GW-011-c (红线): Telegram.kt 的 sendTyping override 不被误改。
     */
    @Test
    fun `TC-GW-011-c Telegram sendTyping override intact`() {
        assertTrue(
            "Telegram.kt 必须仍含 " +
                "`override suspend fun sendTyping(chatId: String, metadata: JSONObject?` 函数声明。",
            Regex("""override\s+suspend\s+fun\s+sendTyping\s*\(\s*chatId\s*:\s*String\s*,\s*metadata\s*:\s*JSONObject\?""")
                .containsMatchIn(telegramSource)
        )
        assertTrue(
            "Telegram.kt sendTyping 函数体必须含 `sendChatAction` 字面值（Telegram Bot API 路径）。",
            telegramSource.contains("sendChatAction")
        )
        assertTrue(
            "Telegram.kt sendTyping 函数体必须含 `\"typing\"` 字面值（action 值）。",
            telegramSource.contains("\"typing\"")
        )
    }

    /**
     * TC-GW-011-d (红线): Base.kt 的 _keepTyping 扩展不被误改。
     */
    @Test
    fun `TC-GW-011-d Base _keepTyping extension intact`() {
        assertTrue(
            "Base.kt 必须仍含 `suspend fun BasePlatformAdapter._keepTyping(` 扩展函数声明。",
            Regex("""suspend\s+fun\s+BasePlatformAdapter\._keepTyping\s*\(""")
                .containsMatchIn(baseSource)
        )
        assertTrue(
            "Base.kt _keepTyping 函数体必须含 `sendTyping(` 调用（每 2s 刷一次的核心动作）。",
            baseSource.contains("sendTyping(")
        )
        assertTrue(
            "Base.kt _keepTyping 函数体必须含 `delay(intervalMs)`（2s 刷新循环）。",
            baseSource.contains("delay(intervalMs)")
        )
        assertTrue(
            "Base.kt _keepTyping 必须有 `finally` 块（自清理）。",
            baseSource.contains("finally")
        )
        assertTrue(
            "Base.kt _keepTyping finally 块必须调 `stopTyping(` 自清理 —— " +
                "防止取消时 chat 残留 typing 提示。",
            baseSource.contains("stopTyping(")
        )
        assertTrue(
            "Base.kt _keepTyping 默认 `intervalMs: Long = 2000L` —— " +
                "对齐 Python 上游 `_keep_typing` 默认 2 秒刷新。",
            Regex("""intervalMs\s*:\s*Long\s*=\s*2000L""").containsMatchIn(baseSource)
        )
    }

    /**
     * TC-GW-011-e (红线): 不引入新的 typing 网络调用 + typing 生命周期归 Run.kt 单点管。
     *
     * 注意：Feishu.kt 已有显式 no-op `override suspend fun sendTyping`（Python 上游 feishu.py
     * 的"API doesn't support typing indicator"注释的 Kotlin 落地），这个本就存在的 no-op 是
     * 正确的；本轮 R-GW-011 守的红线是"不引入新的 typing 网络调用"，所以要确认 Feishu/Weixin
     * 的 sendTyping override（如果存在）保持 no-op、不出现 `sendChatAction` 之类的
     * 平台 API 字面值。
     */
    @Test
    fun `TC-GW-011-e other platforms untouched, typing lifecycle owned by Run kt`() {
        // Feishu / Weixin 的 sendTyping override（已存在的 no-op）必须仍是 no-op：
        // 函数体里不得出现 Telegram 专有的 sendChatAction 字面值（说明本轮没把 Telegram 实现
        // 误改到别的平台上）。
        listOf("Feishu" to feishuSource, "Weixin" to weixinSource).forEach { (name, src) ->
            // 用正则抓 sendTyping override 函数体（{ ... } 块）
            val match = Regex(
                """override\s+suspend\s+fun\s+sendTyping\s*\([^)]*\)\s*\{([^{}]*(?:\{[^{}]*\}[^{}]*)*)\}""",
                RegexOption.DOT_MATCHES_ALL
            ).find(src)
            if (match != null) {
                val body = match.groupValues[1]
                assertFalse(
                    "$name.sendTyping override 函数体不应含 Telegram 专有 `sendChatAction` 字面值 —— " +
                        "本轮 R-GW-011 只动 Telegram 路径，别的平台保持现状（no-op 或既有实现）。\n" +
                        "实际 $name.sendTyping 函数体:\n$body",
                    body.contains("sendChatAction")
                )
            }
        }
        // typing 生命周期归 Run.kt 单点管：HermesGatewayController 等高层入口不应直接调 _keepTyping
        controllerSource?.let { src ->
            assertFalse(
                "typing 生命周期归 `Run.kt::_handleMessage` 单点管 —— " +
                    "高层入口 (HermesGatewayController) 不得直接调用 `_keepTyping(`。",
                src.contains("_keepTyping(")
            )
        }
    }

    // ----- helpers -----

    private fun hermesAndroidSrcMainRoot(): File {
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun runKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/Run.kt").path

    private fun telegramKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/platforms/Telegram.kt").path

    private fun baseKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/platforms/Base.kt").path

    private fun feishuKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/platforms/Feishu.kt").path

    private fun weixinKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/platforms/Weixin.kt").path

    /**
     * Optional: HermesGatewayController lives in the **app** module (not hermes-android).
     * Tests reach it via `../app/...` from the hermes-android working dir. If the file isn't
     * there, the controller-side red-line check is skipped (the rest of the test still runs).
     */
    private fun controllerKtPath(): String {
        // hermes-android cwd → ../app/...  ;  workspace cwd → app/...
        val rel = "src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt"
        listOf(
            File("../app", rel),
            File("app", rel),
            File("HermesApp/app", rel),
        ).firstOrNull { it.exists() }?.let { return it.path }
        return File(rel).path  // returns non-existent path; controllerSource will be null
    }
}
