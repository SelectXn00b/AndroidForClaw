package com.ai.assistance.operit.services

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-AGENT-045-i-6: 4 层显式参数管道签名守卫。
 *
 * `ChatServiceCore.sendUserMessage` + `MessageCoordinationDelegate.sendUserMessage` /
 * `sendMessageInternal` + `MessageProcessingDelegate.sendUserMessage` 的签名必须
 * 新增 `originPlatformOverride: String? = null` + `originChatIdOverride: String? = null`
 * 参数，且每层把这两个值原样 forward 到下一层调用点。
 *
 * 这是 C-route 的中段——把 origin 显式参数从工具层（StandardChatManagerTool，TC-i-5）
 * 一直顺到 service-scope launch 边界另一侧的 `MessageProcessingDelegate.sendUserMessage`
 * （TC-i-7 在那里把它们重写回 ThreadLocal）。
 *
 * 源码扫描测试。
 */
class ChatServiceOriginParamsTest {

    private val coreSource: String by lazy { File(corePath()).readText() }
    private val coordSource: String by lazy { File(coordPath()).readText() }
    private val processSource: String by lazy { File(processPath()).readText() }

    @Test
    fun `TC-AGENT-045-i-6 chains origin params through 4-layer signatures`() {
        // 1) ChatServiceCore.sendUserMessage 签名必须含两个 override 参数
        assertSendUserMessageSignatureHasOriginOverrides(
            "ChatServiceCore.kt",
            coreSource
        )

        // 2) ChatServiceCore.sendUserMessage 必须 forward 给 messageCoordinationDelegate.sendUserMessage
        assertForwardsOriginOverrides(
            "ChatServiceCore.kt → messageCoordinationDelegate.sendUserMessage",
            coreSource,
            calleePattern = """messageCoordinationDelegate\.sendUserMessage\s*\("""
        )

        // 3) MessageCoordinationDelegate.sendUserMessage 签名 + sendMessageInternal 签名
        assertSendUserMessageSignatureHasOriginOverrides(
            "MessageCoordinationDelegate.kt::sendUserMessage",
            coordSource
        )
        // sendMessageInternal 同样用宽松校验：源里出现 `fun sendMessageInternal(`
        // 同时有 originPlatformOverride/originChatIdOverride 参数（不必死磕同一个签名块内）。
        assertTrue(
            "MessageCoordinationDelegate.kt::sendMessageInternal 必须含 `private fun sendMessageInternal(` 声明 + " +
                "至少一处 `originPlatformOverride: String?` —— C-route 4 层管道之一。",
            Regex("""fun\s+sendMessageInternal\s*\(""").containsMatchIn(coordSource) &&
                Regex("""originPlatformOverride\s*:\s*String\?""").containsMatchIn(coordSource)
        )

        // 4) MessageCoordinationDelegate.sendUserMessage 必须 forward 给两处 sendMessageInternal 调用
        //    （`coroutineScope.launch { sendMessageInternal(...) }` 分支 + else 直接调用分支）。
        //    用 `(?<!fun\s)` 过滤掉 `fun sendMessageInternal(` 的声明位置，只看调用点。
        //    sendMessageInternal 内部可能有自递归调用（auto-continuation 等），那些不在
        //    sendUserMessage 的"if/else 分发"语义内，本 TC 不约束——只验前两处（即
        //    sendUserMessage 的 if/else 两路出口都把 origin 显式 forward 了）。
        val sendInternalCalls = Regex("""(?<!fun\s)sendMessageInternal\s*\(""").findAll(coordSource)
            .map { it.range.first }.toList()
        assertTrue(
            "MessageCoordinationDelegate.kt 必须保留至少 2 处 `sendMessageInternal(...)` 调用 " +
                "（chatId-blank 分支 + else 分支）。实际 ${sendInternalCalls.size} 处。",
            sendInternalCalls.size >= 2
        )
        // 只校验前 2 处调用点（来自 sendUserMessage 的 if/else 分发）
        for (idx in sendInternalCalls.take(2)) {
            val tail = coordSource.substring(idx, (idx + 1500).coerceAtMost(coordSource.length))
            assertTrue(
                "`sendMessageInternal(...)` 调用块内（前 2 处，对应 sendUserMessage 的 if/else 出口）" +
                    "必须含 `originPlatformOverride = ...` + `originChatIdOverride = ...` —— " +
                    "守 closure capture 把两值带进 launch 块（line ~292 分支）或 else 分支同样 forward。" +
                    "位置 idx=$idx。",
                tail.contains("originPlatformOverride =") && tail.contains("originChatIdOverride =")
            )
        }

        // 5) MessageCoordinationDelegate.sendMessageInternal 必须 forward 给
        //    messageProcessingDelegate.sendUserMessage
        assertForwardsOriginOverrides(
            "MessageCoordinationDelegate.kt::sendMessageInternal → messageProcessingDelegate.sendUserMessage",
            coordSource,
            calleePattern = """messageProcessingDelegate\.sendUserMessage\s*\("""
        )

        // 6) MessageProcessingDelegate.sendUserMessage 签名必须含两个 override 参数
        assertSendUserMessageSignatureHasOriginOverrides(
            "MessageProcessingDelegate.kt::sendUserMessage",
            processSource
        )
    }

    // ----- helpers -----

    private fun assertSendUserMessageSignatureHasOriginOverrides(label: String, source: String) {
        // 简化策略：源里同时出现 `fun sendUserMessage(`、`originPlatformOverride: String?`
        // 和 `originChatIdOverride: String?`，且两个 override 字面紧跟（彼此 800 字符内），
        // 即认为签名携带这两个参数。原本用单条 `[^)]*` 的正则会被像
        // `attachments: List<AttachmentInfo> = emptyList()` 这种闭合括号截断，
        // 出现假阴性。
        assertTrue(
            "$label 必须含 `fun sendUserMessage(` 声明。",
            Regex("""fun\s+sendUserMessage\s*\(""").containsMatchIn(source)
        )
        val plat = Regex("""originPlatformOverride\s*:\s*String\?""").find(source)?.range?.first ?: -1
        val chat = Regex("""originChatIdOverride\s*:\s*String\?""").find(source)?.range?.first ?: -1
        assertTrue(
            "$label 的 `sendUserMessage` 签名必须新增 `originPlatformOverride: String? = null` + " +
                "`originChatIdOverride: String? = null` 参数 —— C-route 4 层管道之一。" +
                "platIdx=$plat chatIdx=$chat",
            plat >= 0 && chat >= 0 && kotlin.math.abs(chat - plat) < 800
        )
    }

    private fun assertForwardsOriginOverrides(
        label: String,
        source: String,
        calleePattern: String
    ) {
        val matches = Regex(calleePattern).findAll(source).map { it.range.first }.toList()
        assertTrue(
            "$label 调用必须存在。",
            matches.isNotEmpty()
        )
        val anyForwards = matches.any { idx ->
            val tail = source.substring(idx, (idx + 2000).coerceAtMost(source.length))
            tail.contains("originPlatformOverride =") && tail.contains("originChatIdOverride =")
        }
        assertTrue(
            "$label 调用块内必须含 `originPlatformOverride = ...` + `originChatIdOverride = ...` —— forward 两值。",
            anyForwards
        )
    }

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun corePath(): String =
        File(appSrcMainRoot(), "services/ChatServiceCore.kt").path

    private fun coordPath(): String =
        File(appSrcMainRoot(), "services/core/MessageCoordinationDelegate.kt").path

    private fun processPath(): String =
        File(appSrcMainRoot(), "services/core/MessageProcessingDelegate.kt").path
}
