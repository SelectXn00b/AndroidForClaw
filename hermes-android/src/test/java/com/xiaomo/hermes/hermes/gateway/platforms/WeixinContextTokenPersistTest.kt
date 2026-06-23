package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * TC-GW-WX-context-token-persist + TC-GW-WX-tokenless-retry (bugfix for
 * "cron 提醒微信收不到"):
 *
 * 根因：`WeixinAdapter._contextTokens: ConcurrentHashMap` 只是进程内 in-memory；
 * 进程被 OEM ROM 杀掉后整个 map 清空，cron tick 起来 dispatchOutgoing 时拿不到
 * context_token 而被 iLink 拒绝（errcode=-14 SESSION_EXPIRED），且没有 tokenless
 * 重试兜底 → 最终 cron 提醒永远到不了微信。
 *
 * Python 上游 `reference/hermes-agent/gateway/platforms/weixin.py` 早就用
 * 磁盘持久化的 `ContextTokenStore` 解决了（line 1127 init / 1322 inbound write /
 * 1567 outbound read）并在 `_send_text_chunk` 里有 tokenless retry（line 1485-1556）。
 *
 * 本测试类用源码扫描方式（同 CronGatewayWarmupTest 等既有红测一致），通过
 * brace-walker 提取目标函数体后断言关键字面值，避免起 Android 协程上下文的
 * 困难。E2E 由用户手测兜底（TC-GW-WX-manual：微信侧设 1min cron，2min 后真收到）。
 *
 * 对应 TC-GW-WX-context-token-persist-1/2/3 + TC-GW-WX-tokenless-retry-1/2
 * （详见 docs/hermes-test-cases.md）。
 */
class WeixinContextTokenPersistTest {

    private val source: String by lazy { File(weixinPath()).readText() }

    /**
     * TC-GW-WX-context-token-persist-1: `WeixinAdapter` 类体必须 reference
     * `ContextTokenStore`（disk-backed store 必须被实例化进 adapter），且
     * `_handleInbound` 必须改成调用 `_tokenStore.set(`（而不是直接写 in-memory map）。
     */
    @Test
    fun `TC-GW-WX-context-token-persist-1 inbound writes via ContextTokenStore`() {
        val adapterBody = extractClassBody(source, "WeixinAdapter")
        assertTrue(
            "TC-GW-WX-context-token-persist-1: WeixinAdapter 类体必须 reference " +
                "`ContextTokenStore` —— adapter 必须持有 disk-backed store 实例。\n" +
                "实际类体（截断）:\n${adapterBody.take(2000)}",
            adapterBody.contains("ContextTokenStore")
        )

        val inboundBody = extractFunctionBody(source, "_handleInbound")
        assertTrue(
            "TC-GW-WX-context-token-persist-1: _handleInbound 必须调用 `_tokenStore.set(` —— " +
                "inbound 抓到的 context_token 必须落盘（而不是只写 in-memory map）。\n" +
                "实际 _handleInbound 函数体:\n$inboundBody",
            Regex("""\b_tokenStore\.set\s*\(""").containsMatchIn(inboundBody)
        )

        // 红线：不得仍然走 in-memory map 写入路径（防止改了 set 没删旧写法导致双写）
        assertFalse(
            "TC-GW-WX-context-token-persist-1 红线: _handleInbound 不得再写 `_contextTokens[...] = ...` " +
                "—— 否则旧路径还在，store 修复无意义。\n实际 _handleInbound 函数体:\n$inboundBody",
            Regex("""_contextTokens\s*\[[^\]]+\]\s*=""").containsMatchIn(inboundBody)
        )
    }

    /**
     * TC-GW-WX-context-token-persist-2: `send()` 必须从 `_tokenStore.get(`
     * 读 token（disk-backed）而不是 `_contextTokens[chatId]`（in-memory map）。
     */
    @Test
    fun `TC-GW-WX-context-token-persist-2 send reads via ContextTokenStore`() {
        val sendBody = extractFunctionBody(source, "send", isOverrideSuspend = true)
        assertTrue(
            "TC-GW-WX-context-token-persist-2: send() 必须调用 `_tokenStore.get(` —— " +
                "outbound 必须从 disk-backed store 读 context_token。\n" +
                "实际 send 函数体:\n$sendBody",
            Regex("""\b_tokenStore\.get\s*\(""").containsMatchIn(sendBody)
        )

        // 红线：不得再读 in-memory map
        assertFalse(
            "TC-GW-WX-context-token-persist-2 红线: send() 不得再读 `_contextTokens[chatId]` —— " +
                "旧路径必须彻底切换。\n实际 send 函数体:\n$sendBody",
            sendBody.contains("_contextTokens[chatId]")
        )
    }

    /**
     * TC-GW-WX-context-token-persist-3: `WeixinAdapter` 类体必须含
     * `ContextTokenStore(` 实例化 + `.restore(` 启动时载入磁盘缓存的调用。
     */
    @Test
    fun `TC-GW-WX-context-token-persist-3 init creates store and restores`() {
        val adapterBody = extractClassBody(source, "WeixinAdapter")
        assertTrue(
            "TC-GW-WX-context-token-persist-3: WeixinAdapter 类体必须含 `ContextTokenStore(` " +
                "构造调用 —— store 必须被实例化（不能只是 reference 类名）。\n" +
                "实际类体（截断）:\n${adapterBody.take(2000)}",
            Regex("""ContextTokenStore\s*\(""").containsMatchIn(adapterBody)
        )
        assertTrue(
            "TC-GW-WX-context-token-persist-3: WeixinAdapter 类体必须调用 `.restore(` —— " +
                "启动时必须从磁盘载入历史 context_token（否则进程重启后等于空 map）。\n" +
                "实际类体（截断）:\n${adapterBody.take(2000)}",
            Regex("""\.restore\s*\(""").containsMatchIn(adapterBody)
        )
    }

    /**
     * TC-GW-WX-tokenless-retry-1: `send()` 必须在 errcode == SESSION_EXPIRED_ERRCODE
     * 时清掉 token 重试一次（对齐 weixin.py:1485-1556 `_send_text_chunk`）。
     * 断言：函数体引用 `SESSION_EXPIRED_ERRCODE` + 多次发请求 + 重试状态变量。
     */
    @Test
    fun `TC-GW-WX-tokenless-retry-1 send retries without token on session expired`() {
        val sendBody = extractFunctionBody(source, "send", isOverrideSuspend = true)
        assertTrue(
            "TC-GW-WX-tokenless-retry-1: send() 必须 reference `SESSION_EXPIRED_ERRCODE` —— " +
                "errcode=-14 是 iLink session 失效信号，必须特殊处理。\n" +
                "实际 send 函数体:\n$sendBody",
            sendBody.contains("SESSION_EXPIRED_ERRCODE")
        )
        // 必须有"已经重试过"的状态变量，对齐 Python 的 retried_without_token
        assertTrue(
            "TC-GW-WX-tokenless-retry-1: send() 必须含重试状态变量 `retriedWithoutToken` " +
                "（对齐 weixin.py:1485 `retried_without_token`）—— 防止无限重试。\n" +
                "实际 send 函数体:\n$sendBody",
            Regex("""\bretriedWithoutToken\b""").containsMatchIn(sendBody)
        )
        // 必须有循环 / 多次发请求 —— 不能只是单次 newCall
        val callCount = Regex("""_apiClient\.newCall\s*\(""").findAll(sendBody).count()
        assertTrue(
            "TC-GW-WX-tokenless-retry-1: send() 必须含循环或重复 newCall —— " +
                "tokenless retry 至少要发两次请求。实际 _apiClient.newCall(...) 出现次数=$callCount。\n" +
                "实际 send 函数体:\n$sendBody",
            callCount >= 1 && (
                Regex("""\b(while|for)\b""").containsMatchIn(sendBody) ||
                    callCount >= 2
                )
        )
    }

    /**
     * TC-GW-WX-tokenless-retry-2: send() 在 errcode != 0 时必须日志 warn —— 这是
     * 后续生产排查的唯一线索（HermesGatewayController.dispatchOutgoing 会把
     * result.error 转写到 gateway.log）。
     *
     * 注意：本测试不要求 `GatewayFileLogger` —— 那个类住在 `app/` 模块，
     * `hermes-android` 不能反向依赖（layering 红线）。改靠 controller 把
     * SendResult.error 写文件。
     */
    @Test
    fun `TC-GW-WX-tokenless-retry-2 send logs warn on errcode failure`() {
        val sendBody = extractFunctionBody(source, "send", isOverrideSuspend = true)
        assertTrue(
            "TC-GW-WX-tokenless-retry-2: send() 必须含 `Log.w(_TAG, ` —— errcode 失败必须留 logcat 痕迹。\n" +
                "实际 send 函数体:\n$sendBody",
            sendBody.contains("Log.w(_TAG,")
        )
        assertTrue(
            "TC-GW-WX-tokenless-retry-2: send() warn 日志必须含 `errcode` 字面值 —— " +
                "便于线上 grep。\n实际 send 函数体:\n$sendBody",
            sendBody.contains("errcode")
        )
        assertTrue(
            "TC-GW-WX-tokenless-retry-2: send() 必须含 retry 行为描述字面值 " +
                "（如 \"retry without context_token\" / \"tokenless retry\"）—— " +
                "排查时一眼能看出走了 tokenless 重试分支。\n实际 send 函数体:\n$sendBody",
            sendBody.contains("without context_token") ||
                sendBody.contains("tokenless retry") ||
                sendBody.contains("tokenless")
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        // hermes-android 测试 cwd 落在 hermes-android/ 下
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android/src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun weixinPath(): String =
        File(appSrcMainRoot(), "hermes/gateway/platforms/Weixin.kt").path

    /**
     * 提取顶层 class 体 `class X(...) ... { ... }`（含嵌套大括号）。
     */
    private fun extractClassBody(src: String, className: String): String {
        val regex = Regex("""class\s+$className\b[^{]*\{""")
        val match = regex.find(src) ?: error("class $className not found in source")
        return extractBraceBody(src, match.range.last)
    }

    /**
     * 提取指定函数体（含嵌套大括号）。
     * - `isOverrideSuspend=true` 用来定位 `override suspend fun send(...)`
     *   而不是其它名字相同的工具函数。
     */
    private fun extractFunctionBody(
        src: String,
        funName: String,
        isOverrideSuspend: Boolean = false,
    ): String {
        val pattern = if (isOverrideSuspend) {
            """override\s+suspend\s+fun\s+$funName\s*\("""
        } else {
            """\bfun\s+$funName\s*\("""
        }
        val regex = Regex(pattern)
        val match = regex.find(src) ?: error(
            "function $funName not found (isOverrideSuspend=$isOverrideSuspend)"
        )
        // 找到函数签名后第一个 `{`
        val openBrace = src.indexOf('{', startIndex = match.range.last)
        if (openBrace < 0) error("function $funName has no body brace")
        return extractBraceBody(src, openBrace)
    }

    /**
     * 从首个 `{` 位置往后做 brace-walker，提取匹配的闭合区间内容。
     */
    private fun extractBraceBody(src: String, openBracePos: Int): String {
        require(src[openBracePos] == '{') { "expected '{' at pos $openBracePos" }
        var depth = 0
        var i = openBracePos
        while (i < src.length) {
            val c = src[i]
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openBracePos, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces starting at $openBracePos")
    }
}
