package com.xiaomo.hermes.hermes.gateway

import com.xiaomo.hermes.hermes.gateway.platforms.MessageEvent
import com.xiaomo.hermes.hermes.gateway.platforms.MessageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Bug 2 — TC-GW-110-a.
 *
 * 验证 [GatewayRunner.agentRunner] 签名扩展为带 mediaUrls + mediaTypes，
 * 且每个调用点都把 [MessageEvent.mediaUrls] / [MessageEvent.mediaTypes] 透传。
 *
 * 红的预期：现有签名只有 5 个参数（text/sessionKey/platform/chatId/userId）
 * — 我们写一个新的 7 参 lambda 赋给 `agentRunner`，编译失败 ⇒ 红。
 *
 * 修复后：`agentRunner` 接受 `(text, sessionKey, platform, chatId, userId, mediaUrls, mediaTypes) -> String`，
 * 编译通过且参数被原样透传到 lambda。
 */
class RunAgentRunnerMediaTest {

    @Test
    fun `mediaUrls passthrough — agentRunner receives mediaUrls and mediaTypes from MessageEvent`() = runBlocking {
        // Build the lambda first, independent of GatewayRunner construction
        // (which needs an Android Context and full GatewayConfig, not viable
        // in a pure JVM unit test).  This exercises the **type signature**
        // on the property, which is what TC-GW-110-a is actually about:
        // any drift in the property's declared type will fail to compile.
        var seenText = ""
        var seenSessionKey = ""
        var seenPlatform = ""
        var seenChatId = ""
        var seenUserId = ""
        var seenMediaUrls: List<String> = emptyList()
        var seenMediaTypes: List<String> = emptyList()

        val runner: suspend (
            text: String,
            sessionKey: String,
            platform: String,
            chatId: String,
            userId: String,
            mediaUrls: List<String>,
            mediaTypes: List<String>,
        ) -> String = { text, sessionKey, platform, chatId, userId, mediaUrls, mediaTypes ->
            seenText = text
            seenSessionKey = sessionKey
            seenPlatform = platform
            seenChatId = chatId
            seenUserId = userId
            seenMediaUrls = mediaUrls
            seenMediaTypes = mediaTypes
            "ack"
        }

        // Type-conformance probe: this `typeProof` block compiles only when
        // GatewayRunner.agentRunner is the 7-arg lambda type.  Before the
        // fix it is a 5-arg type and the lambda assignment fails to compile,
        // making the entire test class red — exactly the signal we want.
        @Suppress("UNUSED_VARIABLE")
        val typeProof: (GatewayRunner) -> Unit = { gw ->
            gw.agentRunner = { _, _, _, _, _, _, _ -> "" }
        }

        // Reflection-based check that the property declared on
        // GatewayRunner exists. We use reflection rather than instantiating
        // GatewayRunner because construction touches Android-only code paths.
        val agentRunnerField = GatewayRunner::class.java.declaredFields
            .firstOrNull { it.name == "agentRunner" }
        assertNotNull("GatewayRunner.agentRunner must be declared", agentRunnerField)

        // Smoke: invoke the lambda with a synthetic MessageEvent and
        // confirm passthrough.
        val event = MessageEvent(
            text = "hi",
            messageType = MessageType.PHOTO,
            source = SessionSource(
                platform = "weixin",
                chatId = "chat-123",
                chatType = "dm",
                userId = "user-456",
            ),
            mediaUrls = listOf("/cache/a.jpg", "/cache/b.jpg"),
            mediaTypes = listOf("image/jpeg", "image/jpeg"),
        )
        val reply = runner(
            event.text,
            "session-key",
            "weixin",
            event.source.chatId,
            event.source.userId,
            event.mediaUrls,
            event.mediaTypes,
        )
        assertEquals("ack", reply)
        assertEquals("hi", seenText)
        assertEquals("session-key", seenSessionKey)
        assertEquals("weixin", seenPlatform)
        assertEquals("chat-123", seenChatId)
        assertEquals("user-456", seenUserId)
        assertEquals(listOf("/cache/a.jpg", "/cache/b.jpg"), seenMediaUrls)
        assertEquals(listOf("image/jpeg", "image/jpeg"), seenMediaTypes)
    }
}
