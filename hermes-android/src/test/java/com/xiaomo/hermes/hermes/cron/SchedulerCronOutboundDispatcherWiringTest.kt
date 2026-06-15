package com.xiaomo.hermes.hermes.cron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (2026-06-15) Bug C: `Scheduler.kt` 必须暴露顶层 `var cronOutboundDispatcher`
 * 注入点（hermes-android 不能 import app 模块的 HermesGatewayController），并由
 * `deliverResult` 按 platform+chatId 调用之；旧的 `// TODO: Route through Android platform adapters`
 * stub 必须删除。
 *
 * **架构合规性**：app→hermes-android 是单向依赖，所以不能在 Scheduler 里直接调
 * HermesGatewayController.dispatchOutgoing。改用顶层 `var`（lambda 注入点），
 * OperitApplication 启动时把 lambda 注入指向 `dispatchOutgoing`。这是对 R-AGENT-031
 * 路径 1 决策"不引入注入点"的必要偏离（已记入 docs/hermes-requirements.md R-AGENT-033）。
 *
 * 对应 TC-AGENT-033-e / TC-AGENT-033-f（见 docs/hermes-test-cases.md）。
 */
class SchedulerCronOutboundDispatcherWiringTest {

    private val source: String by lazy { File(schedulerPath()).readText() }

    /**
     * 抓 `deliverResult` 函数体（从签名 `fun deliverResult(` 起，跨大括号深度到 0）。
     */
    private fun extractDeliverResultBody(): String {
        val anchor = source.indexOf("fun deliverResult(")
        if (anchor < 0) return ""
        val openBrace = source.indexOf('{', anchor)
        if (openBrace < 0) return ""
        var depth = 0
        var i = openBrace
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(openBrace, i + 1)
            }
            i++
        }
        return source.substring(openBrace)
    }

    /**
     * TC-AGENT-033-e: `cronOutboundDispatcher` 顶层 var 声明存在；类型签名含 suspend + Boolean。
     */
    @Test
    fun `TC-AGENT-033-e Scheduler exposes cronOutboundDispatcher injection point`() {
        assertTrue(
            "TC-AGENT-033-e: Scheduler.kt 必须含 `cronOutboundDispatcher` 字面值 —— " +
                "顶层 var 注入点，让 app 模块（OperitApplication）能注入指向 HermesGatewayController.dispatchOutgoing 的 lambda。\n" +
                "（不能在 Scheduler 里 import app 模块——单向依赖红线。）",
            source.contains("cronOutboundDispatcher")
        )

        // 类型签名含 suspend（lambda 是 suspend 的）
        assertTrue(
            "TC-AGENT-033-e: cronOutboundDispatcher 类型必须含 `suspend` —— " +
                "platform adapter send 是 suspend，lambda 也必须是。",
            Regex("""cronOutboundDispatcher[\s\S]{0,300}suspend""").containsMatchIn(source)
        )
        // 类型签名含 Boolean（返回值，告诉调用方投递是否成功）
        assertTrue(
            "TC-AGENT-033-e: cronOutboundDispatcher 类型必须含 `Boolean` —— " +
                "返回值告诉 deliverResult 此次投递是否成功（false 则记录到 deliveryErrors）。",
            Regex("""cronOutboundDispatcher[\s\S]{0,300}Boolean""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-033-f: `deliverResult` 函数体调用 cronOutboundDispatcher，且不再含 TODO stub。
     */
    @Test
    fun `TC-AGENT-033-f deliverResult invokes dispatcher per target`() {
        val body = extractDeliverResultBody()
        assertTrue(
            "TC-AGENT-033-f: 找不到 `deliverResult` 函数体 —— 结构可能被改。",
            body.isNotEmpty()
        )

        assertTrue(
            "TC-AGENT-033-f: `deliverResult` 函数体必须含 `cronOutboundDispatcher` 引用 —— " +
                "否则注入点摆设、cron→IM 还是死信。\n实际函数体:\n$body",
            body.contains("cronOutboundDispatcher")
        )

        // 引用 target.platform / target.chatId（按 platform+chatId 直投到 IM adapter）
        assertTrue(
            "TC-AGENT-033-f: `deliverResult` 必须引用 `target.platform` —— " +
                "按平台名路由到对应 IM adapter。\n实际函数体:\n$body",
            body.contains("target.platform")
        )
        assertTrue(
            "TC-AGENT-033-f: `deliverResult` 必须引用 `target.chatId` —— " +
                "投递目标 chatId。\n实际函数体:\n$body",
            body.contains("target.chatId")
        )

        // 红线：原 stub TODO 必须移除
        assertFalse(
            "TC-AGENT-033-f 红线: `deliverResult` 函数体**不得**含 " +
                "`TODO: Route through Android platform adapters` 字面值 —— " +
                "原 R-AGENT-031 留的 stub TODO 在本 R 必须删除。\n实际函数体:\n$body",
            body.contains("TODO: Route through Android platform adapters")
        )
    }

    // ----- helpers -----

    private fun schedulerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/cron/Scheduler.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/cron/Scheduler.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Scheduler.kt — cwd=${File(".").absolutePath}")
    }
}
