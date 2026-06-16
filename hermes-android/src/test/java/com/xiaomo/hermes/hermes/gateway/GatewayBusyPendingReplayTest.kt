package com.xiaomo.hermes.hermes.gateway

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.platforms.BasePlatformAdapter
import com.xiaomo.hermes.hermes.gateway.platforms.MessageEvent
import com.xiaomo.hermes.hermes.gateway.platforms.MessageType
import com.xiaomo.hermes.hermes.gateway.platforms.SendResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * R-GATEWAY-038: busy default-path abort+replay 对齐守护。
 *
 * 守的是 Kotlin 早已实现但没人立 R 的"agent busy 时收到非命令文本 →
 * 当前 turn 通过 `INTERRUPTED_SENTINEL` abort → 新消息作为下一 turn user
 * input replay 出去"这条端到端语义。Mirrors Python 上游：
 *  - default busy 分支：`gateway/run.py:1547-1559`
 *  - pending-event 在 interrupt 后的消费：`gateway/run.py:10437-10470`
 *
 * 本 R 不动生产代码；纯测试守护。fixture 复用 `GatewayDrainBehaviorTest` /
 * `GatewayAgentWiringTest` 的 FakeAdapter + reflection helper 模板。
 */
class GatewayBusyPendingReplayTest {

    /** Records outbound send() calls so we can assert which reply made it out. */
    private data class SendCall(val chatId: String, val content: String, val replyTo: String?)

    private class FakeAdapter(
        platform: Platform,
        config: PlatformConfig,
    ) : BasePlatformAdapter(config, platform) {
        val sent: CopyOnWriteArrayList<SendCall> = CopyOnWriteArrayList()
        override suspend fun connect(): Boolean { markConnected(); return true }
        override suspend fun disconnect() { markDisconnected() }
        override suspend fun send(
            chatId: String,
            content: String,
            replyTo: String?,
            metadata: JSONObject?,
        ): SendResult {
            sent += SendCall(chatId, content, replyTo)
            return SendResult(success = true, messageId = "m-${sent.size}")
        }
    }

    private fun newRunner(): GatewayRunner {
        val ctx: Context = mock()
        val cfg = GatewayConfig(
            hermesHome = "",
            platforms = emptyMap(),
            maxConcurrentSessions = 4,
        )
        return GatewayRunner(ctx, cfg)
    }

    /** Build a runner pre-wired with a fake adapter registered into _adapters + deliveryRouter. */
    private fun newRunnerWithFakeAdapter(
        platform: Platform = Platform.TELEGRAM,
    ): Pair<GatewayRunner, FakeAdapter> {
        val runner = newRunner()
        val cfg = PlatformConfig(platform = platform, enabled = true)
        val adapter = FakeAdapter(platform, cfg)
        @Suppress("UNCHECKED_CAST")
        val adaptersField = runner.javaClass.getDeclaredField("_adapters").apply { isAccessible = true }
        val adapters = adaptersField.get(runner) as MutableMap<String, BasePlatformAdapter>
        adapters[platform.value] = adapter
        runner.deliveryRouter.register(adapter)
        return runner to adapter
    }

    private fun event(
        text: String,
        platform: Platform = Platform.TELEGRAM,
        chatId: String = "c1",
        userId: String = "u1",
        msgId: String = "mid-1",
    ): MessageEvent {
        val src = SessionSource(
            platform = platform.value,
            chatId = chatId,
            chatName = "test-chat",
            chatType = "dm",
            userId = userId,
            userName = "tester",
        )
        return MessageEvent(text = text, messageType = MessageType.TEXT, source = src, message_id = msgId)
    }

    /** Reflectively access `_pendingEvents` for direct injection / inspection. */
    @Suppress("UNCHECKED_CAST")
    private fun pendingEvents(runner: GatewayRunner): MutableMap<String, MessageEvent> {
        val f = runner.javaClass.getDeclaredField("_pendingEvents")
        f.isAccessible = true
        return f.get(runner) as MutableMap<String, MessageEvent>
    }

    /** Reflectively invoke private suspend `_handleMessage(event)` synchronously. */
    private fun dispatch(runner: GatewayRunner, event: MessageEvent) {
        val method = runner.javaClass.declaredMethods.first { it.name == "_handleMessage" }
        method.isAccessible = true
        runBlocking {
            val completion = kotlinx.coroutines.CompletableDeferred<Any?>()
            val continuation = object : kotlin.coroutines.Continuation<Any?> {
                override val context: kotlin.coroutines.CoroutineContext
                    get() = kotlin.coroutines.EmptyCoroutineContext
                override fun resumeWith(result: Result<Any?>) {
                    if (result.isSuccess) completion.complete(result.getOrNull())
                    else completion.completeExceptionally(result.exceptionOrNull()!!)
                }
            }
            method.invoke(runner, event, continuation)
            completion.await()
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // TC-GATEWAY-038-a: end-to-end abort + replay
    // ────────────────────────────────────────────────────────────────────

    /**
     * TC-GATEWAY-038-a: agent busy 时收到非命令文本 → 当前 turn abort →
     * 新消息作为下一 turn user input replay。
     *
     * 驱动方式：
     *  1. 第 1 次 `_handleMessage` 进入 normal 分支（_processingSessions.add 成功）；
     *  2. agentRunner 第 1 次调用内部，**反射**模拟"用户在 agent busy 时
     *     发了第二条消息"——直接把"二号消息"塞进 `_pendingEvents` 并把
     *     interrupt flag 置 true，然后返回 `INTERRUPTED_SENTINEL`；
     *  3. `_handleMessage` 落入 pending-event 循环（Run.kt:596-688），
     *     用二号消息的 text 第 2 次调 agentRunner，第 2 次返回正常 reply；
     *  4. 断言：调用次数 + 第 2 次 text + adapter 发出的 reply 内容/replyTo +
     *     `_pendingEvents` 最终为空。
     */
    @Test
    fun `TC-GATEWAY-038-a busy non-command text aborts current turn and replays as next turn user input`() {
        val (runner, adapter) = newRunnerWithFakeAdapter(Platform.TELEGRAM)

        val callCount = AtomicInteger(0)
        val seenTexts = CopyOnWriteArrayList<String>()
        val sessionKeyHolder = arrayOfNulls<String>(1)

        runner.agentRunner = { text, sessionKey, _, _, _ ->
            val n = callCount.incrementAndGet()
            seenTexts += text
            sessionKeyHolder[0] = sessionKey
            if (n == 1) {
                // 模拟第一条消息处理到一半，第二条消息到达 busy 分支：
                //  - mergePendingMessageEvent 入 _pendingEvents
                //  - _interruptFlags[key].set(true)
                // 我们直接做等价反射注入（避免起第二个协程并发调 _handleMessage 引入时序不确定）。
                val secondEvent = event(
                    text = "二号消息",
                    platform = Platform.TELEGRAM,
                    chatId = "c1",
                    userId = "u1",
                    msgId = "mid-2",
                )
                pendingEvents(runner)[sessionKey] = secondEvent
                runner.getInterruptFlag(sessionKey)?.set(true)
                GatewayRunner.INTERRUPTED_SENTINEL
            } else {
                "echo: $text"
            }
        }

        dispatch(runner, event(text = "一号消息", msgId = "mid-1"))

        assertEquals("agentRunner must be invoked exactly twice (initial + replay)", 2, callCount.get())
        assertEquals("first call sees the original text", "一号消息", seenTexts[0])
        assertEquals("replay call sees the pending text (no /steer prefix needed)", "二号消息", seenTexts[1])

        // adapter 收到的应该是 replay 的 reply（一号消息因为返回 INTERRUPTED_SENTINEL 不发）
        assertEquals("only the replayed reply makes it out (interrupted reply is dropped)", 1, adapter.sent.size)
        val out = adapter.sent[0]
        assertEquals("replay reply content matches second-call agentRunner return", "echo: 二号消息", out.content)
        assertEquals("replyTo points to the pending event's message_id", "mid-2", out.replyTo)

        // pending map 最终被清空
        assertTrue("_pendingEvents must be drained after replay", pendingEvents(runner).isEmpty())
    }

    // ────────────────────────────────────────────────────────────────────
    // TC-GATEWAY-038-b: 源码结构守护
    // ────────────────────────────────────────────────────────────────────

    /**
     * TC-GATEWAY-038-b: 源码结构扫描守住 default busy 分支的三行核心 wiring：
     *  - mergePendingMessageEvent(_pendingEvents, ...)
     *  - _interruptFlags[event.sessionKey]?.set(true)
     *  - _sendBusyAck(event)
     * 三行必须共存于 `if (!_processingSessions.add(event.sessionKey))` 内部，
     * 且位于 drain 检查 + resolveCommand 命令路由之后、return 之前。
     *
     * 这是与 TC-GATEWAY-037-d 同款的源码扫描守护，目标是让任何"删/改这条
     * 路径"的重构在第一时间被测试拉住，无需端到端再跑一次。
     */
    @Test
    fun `TC-GATEWAY-038-b busy default branch wires pending+interrupt+ack`() {
        val srcRoots = listOf(
            java.io.File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
            java.io.File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
        )
        val src = srcRoots.firstOrNull { it.exists() }?.readText()
            ?: error("Cannot locate Run.kt; cwd=${java.io.File(".").absolutePath}")

        val busyGuardIdx = src.indexOf("if (!_processingSessions.add(event.sessionKey))")
        assertTrue("busy guard must exist", busyGuardIdx >= 0)
        val drainCheckIdx = src.indexOf("if (_draining)", startIndex = busyGuardIdx)
        val cmdRouteIdx = src.indexOf("resolveCommand(event.text)", startIndex = busyGuardIdx)
        val mergeIdx = src.indexOf("mergePendingMessageEvent(", startIndex = busyGuardIdx)
        val interruptSetIdx = src.indexOf("_interruptFlags[event.sessionKey]?.set(true)", startIndex = busyGuardIdx)
        val busyAckIdx = src.indexOf("_sendBusyAck(event)", startIndex = busyGuardIdx)

        assertTrue("drain check must exist inside busy branch", drainCheckIdx > busyGuardIdx)
        assertTrue("command routing must exist inside busy branch", cmdRouteIdx > busyGuardIdx)
        assertTrue("mergePendingMessageEvent must exist in busy default path", mergeIdx > busyGuardIdx)
        assertTrue("_interruptFlags ... set(true) must exist in busy default path", interruptSetIdx > busyGuardIdx)
        assertTrue("_sendBusyAck(event) must exist in busy default path", busyAckIdx > busyGuardIdx)

        // 顺序：drain → cmd routing → default(merge + interrupt + ack)
        assertTrue("drain check must precede command routing", drainCheckIdx < cmdRouteIdx)
        assertTrue("command routing must precede default path merge", cmdRouteIdx < mergeIdx)
        assertTrue("merge must precede interrupt flag set", mergeIdx < interruptSetIdx)
        assertTrue("interrupt flag set must precede busy ack", interruptSetIdx < busyAckIdx)
    }

    // ────────────────────────────────────────────────────────────────────
    // TC-GATEWAY-038-c: MAX_INTERRUPT_DEPTH 兜底
    // ────────────────────────────────────────────────────────────────────

    /**
     * TC-GATEWAY-038-c: pending-event 循环最多接力 `MAX_INTERRUPT_DEPTH=3`
     * 层（`Run.kt:601-604`），超出后 break 不再消费 pending。
     *
     * 驱动：连续在 agentRunner 内塞下一条 pending 并返回 INTERRUPTED_SENTINEL，
     * 直到 5 层。期望：agentRunner 总调用次数 = 1 (initial) + 3 (depth1..3) = 4。
     *
     * **生产实际行为**：break 之后 `Run.kt:700` 的 finally 块会
     * `_pendingEvents.remove(event.sessionKey)`，所以 map 最终是空的——
     * "未消费的深度溢出 event"在生产里直接被丢弃，不残留。这与 Python 上游
     * `gateway/run.py:601-604` 行为等价（depth cap 命中时丢消息 + log warn）。
     * 因此本 TC 的强守护点是"调用次数封顶"而不是"残留检查"。
     *
     * Kotlin pending-event 循环位置：`Run.kt:596-688`。`MAX_INTERRUPT_DEPTH`
     * 定义在 `Run.kt:40`。
     */
    @Test
    fun `TC-GATEWAY-038-c pending replay caps at MAX_INTERRUPT_DEPTH`() {
        val (runner, _) = newRunnerWithFakeAdapter(Platform.TELEGRAM)

        val callCount = AtomicInteger(0)
        // 计划塞 5 层 pending（每次 agentRunner 调用都返回 INTERRUPTED + 塞下一层），
        // 让 MAX_INTERRUPT_DEPTH=3 兜底生效。
        val plannedPendings = 5

        runner.agentRunner = { _, sessionKey, _, _, _ ->
            val n = callCount.incrementAndGet()
            if (n <= plannedPendings) {
                val nextEvent = event(
                    text = "msg-$n",
                    platform = Platform.TELEGRAM,
                    chatId = "c1",
                    userId = "u1",
                    msgId = "mid-$n",
                )
                pendingEvents(runner)[sessionKey] = nextEvent
                runner.getInterruptFlag(sessionKey)?.set(true)
                GatewayRunner.INTERRUPTED_SENTINEL
            } else {
                "should-not-reach"
            }
        }

        dispatch(runner, event(text = "msg-0", msgId = "mid-0"))

        // 1 (initial) + MAX_INTERRUPT_DEPTH (3) = 4 次调用。这是核心守护点：
        // 即使外部喂无限 pending，循环也必须在 depth=3 后 break。
        assertEquals(
            "agentRunner total invocations must be capped at 1 (initial) + MAX_INTERRUPT_DEPTH (3) = 4",
            4, callCount.get(),
        )
        // finally 块（Run.kt:700）会清掉 _pendingEvents[sessionKey]，所以 map 最终为空。
        // 这与 Python 行为等价——depth cap 命中即丢消息，不残留供下次重启复活。
        assertTrue(
            "_pendingEvents must be cleared by finally block (Run.kt:700) even after depth cap break",
            pendingEvents(runner).isEmpty(),
        )
    }
}
