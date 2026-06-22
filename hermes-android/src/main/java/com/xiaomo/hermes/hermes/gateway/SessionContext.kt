/** 1:1 对齐 hermes/gateway/session_context.py */
package com.xiaomo.hermes.hermes.gateway

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.asContextElement

/**
 * Session-scoped context variables for the Hermes gateway.
 *
 * Replaces the previous environment-variable-based session state
 * (HERMES_SESSION_PLATFORM, HERMES_SESSION_CHAT_ID, etc.) with
 * ThreadLocal variables.
 *
 * **Why this matters**
 *
 * The gateway processes messages concurrently. When two messages arrive
 * at the same time the old code did:
 *
 *     System.setProperty("HERMES_SESSION_THREAD_ID", context.source.threadId)
 *
 * Because system properties are process-global, Message A's value was
 * silently overwritten by Message B before Message A's agent finished
 * running.
 *
 * ThreadLocal values are thread-local so concurrent messages never interfere.
 */

// Sentinel to distinguish "never set in this thread" from "explicitly set to empty".
private val _UNSET: Any = Object()

// Per-thread session variables
private val SESSION_PLATFORM = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_CHAT_ID = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_CHAT_NAME = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_THREAD_ID = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_USER_ID = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_USER_NAME = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_KEY = ThreadLocal.withInitial<Any> { _UNSET }

// R-AGENT-033: Cron auto-deliver per-thread vars.
// Used by CronjobTools to capture origin (platform/chat_id/thread_id) when
// `deliver` arg is omitted, so cron job fires back to the same IM chat.
private val SESSION_CRON_AUTO_DELIVER_PLATFORM = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_CRON_AUTO_DELIVER_CHAT_ID = ThreadLocal.withInitial<Any> { _UNSET }
private val SESSION_CRON_AUTO_DELIVER_THREAD_ID = ThreadLocal.withInitial<Any> { _UNSET }

/** Python `_VAR_MAP` — HERMES_SESSION_* env name → backing ThreadLocal. */
private val _VAR_MAP: Map<String, ThreadLocal<Any>> = mapOf(
    "HERMES_SESSION_PLATFORM" to SESSION_PLATFORM,
    "HERMES_SESSION_CHAT_ID" to SESSION_CHAT_ID,
    "HERMES_SESSION_CHAT_NAME" to SESSION_CHAT_NAME,
    "HERMES_SESSION_THREAD_ID" to SESSION_THREAD_ID,
    "HERMES_SESSION_USER_ID" to SESSION_USER_ID,
    "HERMES_SESSION_USER_NAME" to SESSION_USER_NAME,
    "HERMES_SESSION_KEY" to SESSION_KEY,
    "HERMES_CRON_AUTO_DELIVER_PLATFORM" to SESSION_CRON_AUTO_DELIVER_PLATFORM,
    "HERMES_CRON_AUTO_DELIVER_CHAT_ID" to SESSION_CRON_AUTO_DELIVER_CHAT_ID,
    "HERMES_CRON_AUTO_DELIVER_THREAD_ID" to SESSION_CRON_AUTO_DELIVER_THREAD_ID,
)

/**
 * Python `set_session_vars` — set all session context variables.
 *
 * Unlike the Python version which returns reset tokens, the Android
 * version uses ThreadLocal which is automatically scoped to the thread.
 * Call [clearSessionVars] in a finally block to clean up.
 */
fun setSessionVars(
    platform: String = "",
    chatId: String = "",
    chatName: String = "",
    threadId: String = "",
    userId: String = "",
    userName: String = "",
    sessionKey: String = "",
) {
    SESSION_PLATFORM.set(platform)
    SESSION_CHAT_ID.set(chatId)
    SESSION_CHAT_NAME.set(chatName)
    SESSION_THREAD_ID.set(threadId)
    SESSION_USER_ID.set(userId)
    SESSION_USER_NAME.set(userName)
    SESSION_KEY.set(sessionKey)
}

/**
 * Python `clear_session_vars` — mark all session context variables as explicitly cleared.
 */
fun clearSessionVars(@Suppress("UNUSED_PARAMETER") tokens: List<Any?>? = null) {
    SESSION_PLATFORM.set("")
    SESSION_CHAT_ID.set("")
    SESSION_CHAT_NAME.set("")
    SESSION_THREAD_ID.set("")
    SESSION_USER_ID.set("")
    SESSION_USER_NAME.set("")
    SESSION_KEY.set("")
}

/**
 * R-AGENT-033: Set cron auto-deliver session vars (per-thread).
 *
 * Called from `_handleMessage` after `setSessionVars` so that any `cronjob`
 * tool call within this turn captures the inbound IM origin (platform / chat /
 * thread) and stores it as the job's `origin` — letting the Scheduler later
 * deliver the result back to that same chat.
 */
fun setCronAutoDeliverVars(
    platform: String = "",
    chatId: String = "",
    threadId: String = "",
) {
    SESSION_CRON_AUTO_DELIVER_PLATFORM.set(platform)
    SESSION_CRON_AUTO_DELIVER_CHAT_ID.set(chatId)
    SESSION_CRON_AUTO_DELIVER_THREAD_ID.set(threadId)
}

/**
 * R-AGENT-033: Clear cron auto-deliver session vars (per-thread).
 *
 * Must be called in the same finally block as [clearSessionVars] to avoid
 * leaking origin from one inbound turn into a later thread reuse.
 */
fun clearCronAutoDeliverVars() {
    SESSION_CRON_AUTO_DELIVER_PLATFORM.set("")
    SESSION_CRON_AUTO_DELIVER_CHAT_ID.set("")
    SESSION_CRON_AUTO_DELIVER_THREAD_ID.set("")
}

/**
 * Python `get_session_env` — read a session context variable by its HERMES_SESSION_* name.
 *
 * Resolution order:
 * 1. ThreadLocal variable (set by the gateway for concurrency-safe access).
 *    If the variable was explicitly set (even to "") via setSessionVars or
 *    clearSessionVars, that value is returned.
 * 2. System properties (only when the ThreadLocal was never set in this thread).
 * 3. default
 */
fun getSessionEnv(name: String, default: String = ""): String {
    val threadLocal = _VAR_MAP[name]
    if (threadLocal != null) {
        val value = threadLocal.get()
        if (value !== _UNSET) {
            return value as String
        }
    }
    return System.getProperty(name, default)
}

/**
 * R-AGENT-045 跨线程修：把当前线程的 session ThreadLocal 快照打包成
 * `CoroutineContext`，让 `withContext(sessionContextElement()) { ... }`
 * 在跨 dispatcher（典型场景：`withContext(Dispatchers.IO)`）切换协程
 * 调度线程后仍能从 ThreadLocal 读到原值。
 *
 * **为什么需要**
 *
 * `ExternalChatRequestExecutor.execute()` 在 broadcast receiver 线程上
 * `setSessionVars(platform = "app", chatId = ...)`。但 `chatTool.sendMessageToAI`
 * 内部进 `EnhancedAIService.sendMessage` 后立刻 `withContext(Dispatchers.IO)`
 * 切到 IO 线程池——目标线程不是写入 ThreadLocal 的源线程，
 * `getSessionEnv()` 读到 `_UNSET` → fallback 到 `System.getProperty` →
 * 拿到空 → `CronjobTools._originFromEnv()` `isNotEmpty()` 失败返回 null
 * → 落进 jobs.json 的 `origin` 字段为 null。这就是 e2e Stage C
 * `"origin": null` 的根因。
 *
 * **怎么修**
 *
 * 上游 Python 用 `contextvars.ContextVar` + `copy_context().run(func)`
 * （`reference/hermes-agent/gateway/run.py:8108-8112`），asyncio task 切换
 * 到 threadpool 时复制 contextvars 快照随 callable 一起带过去。Kotlin
 * 协程的等价物是 `ThreadLocal<T>.asContextElement(value)` —— 协程进
 * 新线程时 `updateThreadContext` 把值塞进新线程的 ThreadLocal，离开时
 * `restoreThreadContext` 还原。把所有 session ThreadLocal 各包一个
 * `asContextElement` 后 `+` 起来，就是当前线程 session 状态的"协程级
 * 快照"。`withContext(sessionContextElement()) { ... }` 内部的所有协程
 * 跳转（含嵌套 `withContext(Dispatchers.IO)`）都会带着这份快照走。
 *
 * **使用约束**
 *
 * 调用者必须先在当前线程上 `setSessionVars` / `setCronAutoDeliverVars`
 * 写值，再调本函数构造 element。本函数读取的是**当前线程当下**的值，
 * 不是 `_UNSET` 就一并打包；`_UNSET` 的 ThreadLocal 跳过（不污染目标
 * 线程的"未设置"状态）。
 */
fun sessionContextElement(): CoroutineContext {
    var ctx: CoroutineContext = EmptyCoroutineContext
    for ((_, threadLocal) in _VAR_MAP) {
        val value: Any? = threadLocal.get()
        if (value != null && value !== _UNSET) {
            // asContextElement 接受 T value（这里的 T = Any），协程在新线程上
            // 会把 threadLocal.set(value) 然后离开时还原。和 Python
            // copy_context().run(func) 语义一致：跨线程仍读得到 value。
            ctx = ctx + threadLocal.asContextElement(value)
        }
    }
    return ctx
}
