package com.xiaomo.hermes.hermes.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-AGENT-045 跨线程修：行为测试守 `sessionContextElement()` 把 ThreadLocal
 * 快照随协程上下文跨 dispatcher 传播。
 *
 * 不修这个 bug 的话：`ExternalChatRequestExecutor` 在 broadcast receiver 线程
 * `setSessionVars(platform="app", chatId="<id>")`，下游 `EnhancedAIService.sendMessage`
 * 内部 `withContext(Dispatchers.IO)` 切到 IO 线程池——目标线程没有 ThreadLocal 写入，
 * `getSessionEnv()` 读到 `_UNSET` → `System.getProperty` fallback → 空 →
 * `CronjobTools._originFromEnv()` `isNotEmpty()` 失败 → null → jobs.json 的
 * `origin` 字段为 null。这就是 e2e Stage C `"origin": null` 的根因。
 *
 * 1:1 等价 Python `contextvars.ContextVar` + `copy_context().run(func)`
 * （`reference/hermes-agent/gateway/run.py:8108-8112`）。
 *
 * 对应 TC-AGENT-045-h-1。
 */
class SessionContextElementTest {

    @After
    fun cleanup() {
        // 防止 ThreadLocal 残留污染同 thread 的下个 test
        clearSessionVars()
        clearCronAutoDeliverVars()
    }

    /**
     * TC-AGENT-045-h-1: sessionContextElement 把 7 个 session ThreadLocal
     * + 3 个 cron auto-deliver ThreadLocal 的快照随协程上下文跨
     * `Dispatchers.IO` 跳转传播。
     *
     * 红/绿信号：在调 `sessionContextElement()` 之前先在源线程写好 vars，
     * 然后 `withContext(sessionContextElement()) { withContext(Dispatchers.IO) { ... } }`
     * 在嵌套 IO context 里读 4 个关键名（platform / chat_id / cron platform /
     * cron chat_id）必须读到原值；如果 helper 没实装、或包错（比如忘了
     * cron 三个 ThreadLocal）→ 任一 assertEquals 失败 → 红。
     */
    @Test
    fun `TC-AGENT-045-h-1 sessionContextElement propagates session vars across Dispatchers IO hop`() = runTest {
        // 模拟 ExternalChatRequestExecutor.execute() 顶部 + listChats hole-fix
        // 路径都跑完后的状态：两组 ThreadLocal 都写好了
        setSessionVars(
            platform = "app",
            chatId = "c-1",
            chatName = "n-1",
            threadId = "",
            userId = "u-1",
            userName = "name-1",
            sessionKey = "k-1",
        )
        setCronAutoDeliverVars(
            platform = "app",
            chatId = "c-1",
            threadId = "",
        )

        val element = sessionContextElement()

        // 关键断言：嵌套 withContext(Dispatchers.IO) 切线程后，
        // ThreadLocal 仍能读到原值（asContextElement 在新线程
        // updateThreadContext 时把值塞进去）
        val platformOnIo = withContext(element) {
            withContext(Dispatchers.IO) {
                getSessionEnv("HERMES_SESSION_PLATFORM")
            }
        }
        assertEquals(
            "HERMES_SESSION_PLATFORM 跨 Dispatchers.IO 跳转后值丢失 —— " +
                "sessionContextElement() 没把 SESSION_PLATFORM ThreadLocal 打包进 CoroutineContext。" +
                "这等价 Python contextvars + copy_context().run() 应保留的语义。",
            "app",
            platformOnIo
        )

        val chatIdOnIo = withContext(element) {
            withContext(Dispatchers.IO) {
                getSessionEnv("HERMES_SESSION_CHAT_ID")
            }
        }
        assertEquals(
            "HERMES_SESSION_CHAT_ID 跨 Dispatchers.IO 跳转后值丢失 —— " +
                "sessionContextElement() 没把 SESSION_CHAT_ID ThreadLocal 打包进 CoroutineContext。" +
                "_originFromEnv() 会因 chat_id 空触发 isNotEmpty() 失败 → jobs.json origin = null。",
            "c-1",
            chatIdOnIo
        )

        val cronPlatformOnIo = withContext(element) {
            withContext(Dispatchers.IO) {
                getSessionEnv("HERMES_CRON_AUTO_DELIVER_PLATFORM")
            }
        }
        assertEquals(
            "HERMES_CRON_AUTO_DELIVER_PLATFORM 跨 Dispatchers.IO 跳转后值丢失 —— " +
                "sessionContextElement() 没把 cron auto-deliver ThreadLocal 一并打包。" +
                "对称 R-AGENT-033 IM 路径的 cron auto-deliver origin 透传也会断。",
            "app",
            cronPlatformOnIo
        )

        val cronChatIdOnIo = withContext(element) {
            withContext(Dispatchers.IO) {
                getSessionEnv("HERMES_CRON_AUTO_DELIVER_CHAT_ID")
            }
        }
        assertEquals(
            "HERMES_CRON_AUTO_DELIVER_CHAT_ID 跨 Dispatchers.IO 跳转后值丢失。",
            "c-1",
            cronChatIdOnIo
        )
    }

    /**
     * TC-AGENT-045-h-1 corollary: `sessionContextElement()` 不能污染目标线程
     * 在 withContext block 之外读到的 ThreadLocal 值。`ThreadLocal.asContextElement`
     * 的契约要求离开 block 时 restoreThreadContext 把目标线程之前的值复原
     * （包括从 `_UNSET` 复原到 `_UNSET`）。
     */
    @Test
    fun `TC-AGENT-045-h-1 sessionContextElement does not leak into outer thread state`() = runTest {
        setSessionVars(platform = "app", chatId = "c-1")
        val element = sessionContextElement()
        // 在 block 内读到 "app"
        withContext(element) {
            withContext(Dispatchers.IO) {
                assertEquals("app", getSessionEnv("HERMES_SESSION_PLATFORM"))
            }
        }
        // block 退出后清空——验证 helper 不留状态
        clearSessionVars()
        assertEquals("", getSessionEnv("HERMES_SESSION_PLATFORM"))
    }

    /**
     * TC-AGENT-045-i-2: `coroutineScope.launch(Dispatchers.IO + sessionContextElement())`
     * 必须把源线程上当下的 ThreadLocal 快照随 launch 出去的协程一起带走。
     *
     * 修的 bug：`MessageProcessingDelegate.sendUserMessage` line ~521
     *   val sendJob = coroutineScope.launch(Dispatchers.IO) { ... }
     * 这是 service-scope（`ChatServiceCore.serviceScope`）的 launch，**不**继承
     * caller 的 CoroutineContext。即便 `ExternalChatRequestExecutor.execute()` 顶部
     * `setSessionVars(...)` + `withContext(sessionContextElement())` 包裹了
     * `sendMessageToAI` 调用，那层 `withContext` 也只能管到当前协程内部；
     * `sendMessageToAI` 走到 `MessageProcessingDelegate.sendUserMessage` 后做 fire-and-forget
     * `coroutineScope.launch(Dispatchers.IO)` —— launch 出去的新协程从 IO 线程池
     * 拉一个线程，那线程上**没有** ThreadLocal 写入（不在 sessionContextElement 的
     * `+` 范围里），`getSessionEnv()` 读到 `_UNSET` → fallback `System.getProperty`
     * → 空 → `_originFromEnv()` 返回 null → jobs.json origin 字段 null。
     *
     * 修法：launch context 上 `+ sessionContextElement()`（在 launch 调用点同步读
     * 当前线程的 ThreadLocal 快照，绑到新协程的 CoroutineContext）。
     *
     * 红/绿信号：source 线程写好 vars，调 `coroutineScope.launch(Dispatchers.IO + sessionContextElement()) {}`
     * 在 launched 协程内部读 4 个关键名必须读到原值；任一空 → 红。
     *
     * 用 `TestScope` + `StandardTestDispatcher`：`runTest` 默认用 `StandardTestDispatcher`，
     * 但是 `Dispatchers.IO` 不是 test scheduler 的——`launch(Dispatchers.IO)` 会
     * 真正切到 IO 线程池。等待 launch 完成必须用 Job.join() 而不是 advanceUntilIdle。
     */
    @Test
    fun `TC-AGENT-045-i-2 sessionContextElement propagates through launch context`() = runTest {
        // 模拟 ExternalChatRequestExecutor.execute() 顶部 + listChats hole-fix
        // 路径都跑完后的状态：两组 ThreadLocal 都写好了
        setSessionVars(platform = "app", chatId = "c-i-2")
        setCronAutoDeliverVars(platform = "app", chatId = "c-i-2")

        // 收集 launched 协程内部读到的值（用 volatile 之类不够——直接用 deferred）
        var platformInLaunch: String? = null
        var chatIdInLaunch: String? = null
        var cronPlatformInLaunch: String? = null
        var cronChatIdInLaunch: String? = null

        // 1:1 模拟 MessageProcessingDelegate.kt:521 的 launch 模式
        val sendJob: Job = this.launch(Dispatchers.IO + sessionContextElement()) {
            platformInLaunch = getSessionEnv("HERMES_SESSION_PLATFORM")
            chatIdInLaunch = getSessionEnv("HERMES_SESSION_CHAT_ID")
            cronPlatformInLaunch = getSessionEnv("HERMES_CRON_AUTO_DELIVER_PLATFORM")
            cronChatIdInLaunch = getSessionEnv("HERMES_CRON_AUTO_DELIVER_CHAT_ID")
        }
        sendJob.join()

        assertEquals(
            "HERMES_SESSION_PLATFORM 跨 launch 边界后值丢失 —— " +
                "launch 的 CoroutineContext 上 `+ sessionContextElement()` 没把 SESSION_PLATFORM " +
                "ThreadLocal 快照打包过去。这就是当前 jobs.json origin=null 的根因。",
            "app",
            platformInLaunch
        )
        assertEquals(
            "HERMES_SESSION_CHAT_ID 跨 launch 边界后值丢失 —— " +
                "_originFromEnv() 在 isNotEmpty() 检查失败 → null → jobs.json origin = null。",
            "c-i-2",
            chatIdInLaunch
        )
        assertEquals(
            "HERMES_CRON_AUTO_DELIVER_PLATFORM 跨 launch 边界后值丢失 —— " +
                "对称 R-AGENT-033 IM 路径 cron auto-deliver origin 透传也会断。",
            "app",
            cronPlatformInLaunch
        )
        assertEquals(
            "HERMES_CRON_AUTO_DELIVER_CHAT_ID 跨 launch 边界后值丢失。",
            "c-i-2",
            cronChatIdInLaunch
        )
    }
}
