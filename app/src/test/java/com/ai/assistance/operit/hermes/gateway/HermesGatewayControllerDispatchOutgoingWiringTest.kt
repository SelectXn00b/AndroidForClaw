package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (2026-06-15)：`HermesGatewayController` 必须暴露 `suspend fun dispatchOutgoing(
 * platform, chatId, text, threadId)`，由 OperitApplication 在 gateway 启动时把它注入到
 * `Scheduler.cronOutboundDispatcher` 顶层 var；stop 时把 dispatcher 置回 null。这样 cron
 * 触发的 `deliverResult` 就能反向走到 GatewayRunner.deliveryRouter → 各平台 adapter.send 真投出去。
 *
 * 对齐 Python `gateway/run.py` 内 cron deliver 逻辑（adapters dict 直访）。
 *
 * 对应 TC-AGENT-033-g / TC-AGENT-033-h（见 docs/hermes-test-cases.md）。
 */
class HermesGatewayControllerDispatchOutgoingWiringTest {

    private val source: String by lazy { File(controllerPath()).readText() }

    /**
     * TC-AGENT-033-g: `dispatchOutgoing` 函数声明 + 函数体调 deliveryRouter.getAdapter + adapter.send +
     * Telegram 分支用 message_thread_id metadata。
     */
    @Test
    fun `TC-AGENT-033-g dispatchOutgoing exposed and threads metadata`() {
        // 函数声明
        assertTrue(
            "TC-AGENT-033-g: HermesGatewayController.kt 必须含 `suspend fun dispatchOutgoing(` " +
                "函数声明 —— 这是 cron→IM 反向投递的唯一入口。",
            Regex("""\bsuspend\s+fun\s+dispatchOutgoing\s*\(""").containsMatchIn(source)
        )

        // 参数列表四参数
        for (param in listOf("platform", "chatId", "text", "threadId")) {
            assertTrue(
                "TC-AGENT-033-g: dispatchOutgoing 参数列表必须含 `$param` —— " +
                    "签名应为 `dispatchOutgoing(platform: String, chatId: String, text: String, threadId: String?)`。",
                source.contains(param)
            )
        }

        // 函数体调 deliveryRouter.getAdapter
        assertTrue(
            "TC-AGENT-033-g: dispatchOutgoing 函数体必须调 `deliveryRouter.getAdapter(` —— " +
                "按 platform 名拿 adapter；不能 hard-code 各 adapter 引用。",
            source.contains("deliveryRouter.getAdapter(")
        )

        // 函数体调 adapter.send（不强求 adapter 这个变量名，但函数体应含 .send( 调用）
        assertTrue(
            "TC-AGENT-033-g: dispatchOutgoing 函数体必须调 platform adapter 的 `.send(` —— " +
                "对齐 BasePlatformAdapter.send(chatId, content, replyTo, metadata) 接口。",
            Regex("""\.\s*send\s*\(""").containsMatchIn(source)
        )

        // Telegram 分支 metadata 用 message_thread_id（Telegram thread 路由专用 key）
        assertTrue(
            "TC-AGENT-033-g: dispatchOutgoing 必须含 `message_thread_id` 字面值 —— " +
                "Telegram 用此 metadata key 路由到 thread。",
            source.contains("message_thread_id")
        )
    }

    /**
     * TC-AGENT-033-h: `cronOutboundDispatcher` 注入 (start 时设值) + 清空 (stop 时置 null) 对称接线。
     */
    @Test
    fun `TC-AGENT-033-h dispatcher injected on start and cleared on stop`() {
        val occurrences = Regex("""\bcronOutboundDispatcher\b""").findAll(source).count()
        assertTrue(
            "TC-AGENT-033-h: HermesGatewayController.kt 必须含 `cronOutboundDispatcher` 至少 2 处 —— " +
                "start() 注入 + stop() 置 null 对称接线，否则注入点泄漏到下次启动。\n" +
                "实际找到 $occurrences 处。",
            occurrences >= 2
        )
    }

    // ----- helpers -----

    private fun controllerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt"),
            File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate HermesGatewayController.kt — cwd=${File(".").absolutePath}")
    }
}
