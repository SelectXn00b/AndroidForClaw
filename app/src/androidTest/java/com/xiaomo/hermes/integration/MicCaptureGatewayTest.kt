package com.xiaomo.hermes.integration

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import ai.openclaw.app.voice.MicCaptureManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * MicCaptureManager 网关连接状态测试
 *
 * 验证修复：localChatChannel 模式下 gatewayConnected 需为 true，
 * 否则 sendQueuedIfIdle 中 !gatewayConnected 检查导致消息永远发不出。
 *
 * 运行:
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xiaomo.hermes.integration.MicCaptureGatewayTest
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class MicCaptureGatewayTest {

    companion object {
        private const val TAG = "MicGatewayTest"
    }

    /**
     * 测试 1: gatewayConnected=false 时消息不发送，状态显示排队等待
     */
    @Test
    fun test01_messageNotSentWhenGatewayDisconnected() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sentMessages = mutableListOf<String>()

        val mic = MicCaptureManager(
            context = context,
            scope = scope,
            sendToGateway = { message, onRunIdKnown ->
                sentMessages.add(message)
                onRunIdKnown("run_test_1")
                "run_test_1"
            },
        )

        // 不调用 onGatewayConnectionChanged(true) — 模拟旧的 bug 场景
        // 通过反射注入消息到 messageQueue
        injectMessageToQueue(mic, "Hello from voice test")

        // 触发 sendQueuedIfIdle
        callSendQueuedIfIdle(mic)

        // 等一下让协程执行
        Thread.sleep(500)

        // 消息不应被发送
        assertEquals("gatewayConnected=false 时不应发送消息", 0, sentMessages.size)

        // statusText 应该显示排队状态
        val status = runBlocking { mic.statusText.first() }
        Log.i(TAG, "disconnected status: $status")
        assertTrue(
            "statusText 应包含排队信息，实际: $status",
            status.contains("queued", ignoreCase = true) || status.contains("waiting", ignoreCase = true)
        )

        scope.cancel()
        Log.i(TAG, "✅ test01 PASSED: 消息在 gateway 断开时不发送")
    }

    /**
     * 测试 2: gatewayConnected=true 后消息正常发送
     */
    @Test
    fun test02_messageSentWhenGatewayConnected() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sentMessages = mutableListOf<String>()
        val sendLatch = CountDownLatch(1)

        val mic = MicCaptureManager(
            context = context,
            scope = scope,
            sendToGateway = { message, onRunIdKnown ->
                Log.i(TAG, "sendToGateway called with: $message")
                sentMessages.add(message)
                onRunIdKnown("run_test_2")
                sendLatch.countDown()
                "run_test_2"
            },
        )

        // 先标记已连接
        mic.onGatewayConnectionChanged(true)

        // 注入消息
        injectMessageToQueue(mic, "Hello connected test")

        // 触发发送
        callSendQueuedIfIdle(mic)

        // 等待发送完成
        val sent = sendLatch.await(5, TimeUnit.SECONDS)
        assertTrue("消息应在 5s 内发送", sent)
        assertEquals("应发送 1 条消息", 1, sentMessages.size)
        assertEquals("Hello connected test", sentMessages[0])

        scope.cancel()
        Log.i(TAG, "✅ test02 PASSED: 消息在 gateway 连接后正常发送")
    }

    /**
     * 测试 3: 先断开再连接，积压消息被发送
     */
    @Test
    fun test03_queuedMessagesSentAfterReconnect() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sentMessages = mutableListOf<String>()
        val sendLatch = CountDownLatch(1)

        val mic = MicCaptureManager(
            context = context,
            scope = scope,
            sendToGateway = { message, onRunIdKnown ->
                Log.i(TAG, "sendToGateway (reconnect): $message")
                sentMessages.add(message)
                onRunIdKnown("run_test_3")
                sendLatch.countDown()
                "run_test_3"
            },
        )

        // 注入消息（此时 gateway 未连接）
        injectMessageToQueue(mic, "Queued before connect")

        // 验证未发送
        callSendQueuedIfIdle(mic)
        Thread.sleep(300)
        assertEquals("连接前不应发送", 0, sentMessages.size)

        // 现在连接 — onGatewayConnectionChanged 内部会调用 sendQueuedIfIdle
        mic.onGatewayConnectionChanged(true)

        val sent = sendLatch.await(5, TimeUnit.SECONDS)
        assertTrue("连接后应自动发送积压消息", sent)
        assertEquals(1, sentMessages.size)
        assertEquals("Queued before connect", sentMessages[0])

        scope.cancel()
        Log.i(TAG, "✅ test03 PASSED: 重连后积压消息自动发送")
    }

    /**
     * 测试 4: chat final 事件触发 TTS 并重置 isSending
     *
     * 流程：连接 → 注入消息 → 发送（sendToGateway 设 pendingRunId）→ 发 chat final 事件 → 验证
     */
    @Test
    fun test04_chatFinalEventTriggersTtsAndResetsSending() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val spokenText = AtomicReference<String?>(null)
        val sendLatch = CountDownLatch(1)

        val mic = MicCaptureManager(
            context = context,
            scope = scope,
            sendToGateway = { message, onRunIdKnown ->
                // 设置 pendingRunId 以便 handleGatewayEvent 匹配
                onRunIdKnown("run_test_4")
                sendLatch.countDown()
                "run_test_4"
            },
            speakAssistantReply = { text ->
                Log.i(TAG, "speakAssistantReply: $text")
                spokenText.set(text)
            },
        )

        mic.onGatewayConnectionChanged(true)
        injectMessageToQueue(mic, "What is 1+1?")
        callSendQueuedIfIdle(mic)

        // 等发送完成（pendingRunId 已设置）
        assertTrue("消息应发送", sendLatch.await(5, TimeUnit.SECONDS))
        Thread.sleep(300)

        // 此时 isSending=true，pendingRunId="run_test_4"
        val sendingBefore = runBlocking { mic.isSending.first() }
        Log.i(TAG, "isSending before final event: $sendingBefore")

        // 模拟 chat final 事件
        val chatPayload = """
            {
                "state": "final",
                "runId": "run_test_4",
                "message": {
                    "role": "assistant",
                    "content": [{"type": "text", "text": "The answer is 2."}]
                }
            }
        """.trimIndent()
        mic.handleGatewayEvent("chat", chatPayload)
        Thread.sleep(500)

        // 验证 TTS 被调用
        assertEquals("speakAssistantReply 应被调用", "The answer is 2.", spokenText.get())

        // 验证 isSending 重置
        val sendingAfter = runBlocking { mic.isSending.first() }
        assertFalse("final 事件后 isSending 应为 false", sendingAfter)

        scope.cancel()
        Log.i(TAG, "✅ test04 PASSED: chat final 事件触发 TTS 并重置发送状态")
    }

    /**
     * 测试 5: 模拟完整本地模式流程（localChatChannel 场景）
     * 验证 onGatewayConnectionChanged(true) 在 init 后立即调用的效果
     */
    @Test
    fun test05_localChannelModeFullFlow() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sentMessages = mutableListOf<String>()
        val sendLatch = CountDownLatch(1)

        val mic = MicCaptureManager(
            context = context,
            scope = scope,
            sendToGateway = { message, onRunIdKnown ->
                Log.i(TAG, "localChannel sendToGateway: $message")
                sentMessages.add(message)
                onRunIdKnown("run_local_1")
                sendLatch.countDown()
                "run_local_1"
            },
        )

        // 模拟 NodeRuntime init 中的修复：立即标记为已连接
        scope.launch { mic.onGatewayConnectionChanged(true) }

        // 稍等让 launch 执行
        Thread.sleep(200)

        // 注入消息并发送
        injectMessageToQueue(mic, "Local mode voice test")
        callSendQueuedIfIdle(mic)

        val sent = sendLatch.await(5, TimeUnit.SECONDS)
        assertTrue("本地模式消息应正常发送", sent)
        assertEquals(1, sentMessages.size)
        assertEquals("Local mode voice test", sentMessages[0])

        scope.cancel()
        Log.i(TAG, "✅ test05 PASSED: 本地模式完整流程验证通过")
    }

    // ==================== 反射工具方法 ====================

    /**
     * 通过反射向 messageQueue 注入消息
     */
    private fun injectMessageToQueue(mic: MicCaptureManager, message: String) {
        try {
            val queueField = MicCaptureManager::class.java.getDeclaredField("messageQueue")
            queueField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val queue = queueField.get(mic) as ArrayDeque<String>
            queue.addLast(message)

            // 同时更新 _queuedMessages flow
            val publishMethod = MicCaptureManager::class.java.getDeclaredMethod("publishQueue")
            publishMethod.isAccessible = true
            publishMethod.invoke(mic)

            Log.i(TAG, "Injected message to queue: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject message: ${e.message}", e)
            fail("反射注入失败: ${e.message}")
        }
    }

    /**
     * 通过反射调用 sendQueuedIfIdle
     */
    private fun callSendQueuedIfIdle(mic: MicCaptureManager) {
        try {
            val method = MicCaptureManager::class.java.getDeclaredMethod("sendQueuedIfIdle")
            method.isAccessible = true
            method.invoke(mic)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call sendQueuedIfIdle: ${e.message}", e)
            fail("反射调用失败: ${e.message}")
        }
    }
}
