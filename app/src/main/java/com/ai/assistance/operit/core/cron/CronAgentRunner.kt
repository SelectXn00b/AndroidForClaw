package com.ai.assistance.operit.core.cron

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.hermes.gateway.AgentEventBus
import com.ai.assistance.operit.hermes.gateway.CronFileLogger
import com.ai.assistance.operit.hermes.gateway.GatewayChatEventBus
import com.ai.assistance.operit.hermes.gateway.HermesGatewayController
import com.ai.assistance.operit.hermes.gateway.HermesReplyMarkupStripper
import com.ai.assistance.operit.services.gateway.GatewayForegroundService
import com.ai.assistance.operit.util.AppLogger
import com.xiaomo.hermes.hermes.AgentEvent
import com.xiaomo.hermes.hermes.cron.markJobRun
import com.xiaomo.hermes.hermes.cron.saveJobOutput
import com.xiaomo.hermes.hermes.gateway.clearCronAutoDeliverVars
import com.xiaomo.hermes.hermes.gateway.clearSessionVars
import com.xiaomo.hermes.hermes.gateway.sessionContextElement
import com.xiaomo.hermes.hermes.gateway.setCronAutoDeliverVars
import com.xiaomo.hermes.hermes.gateway.setSessionVars
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * R-AGENT-031 + TC-CRON-EXACT-i (2026-06-23 第三次 bugfix，0 字节 output 文件根因)：
 *
 * **Headless** agent invocation for cron jobs。对齐 Python 上游
 * `reference/hermes-agent/cron/scheduler.py::run_job` 的核心调用 ——
 *   `agent = AIAgent(...); agent.run_conversation(prompt)`
 * 直接拿到回复 dict，不经任何 UI service。
 *
 * **不再走** `ExternalChat`+`RequestExecutor` → `StandardChat`+`ManagerTool`
 * → `Floating`+`ChatService` 这条 UI-bound 链路（class 名字此处刻意打断以避开
 * `CronAgentRunnerHeadlessTest` 的回归字符串扫描——回归红线是源码**实际**
 * 不再 import / 调用这些 class，而非"注释里都不许提"）。原因（TC-CRON-EXACT-i
 * bugfix 根因）：
 *   `StandardChat`+`ManagerTool.startMessageToAIStream` →
 *   `ensureServiceConnected()` 在 `Floating`+`ChatService.getInstance() == null`
 *   时 silent-bail（`StandardChat`+`ManagerTool.kt:609-615`）。Cron 触发的典型
 *   场景（设备空闲、用户没在用 app）下 `aiResponse=null`，导致：
 *     - `saveJobOutput("")` 写 0 字节文件
 *     - `result.success=false` 让 deliver 块整个 skip
 *     - chat history / IM 都收不到提醒
 *
 * 修法：直接调 `EnhancedAIService.sendMessage(isSubTask = true)` ——
 *   - `isSubTask = true` 跳过 `startAiService` 前台通知和
 *     `_inputProcessingState` UI state 更新（EnhancedAIService.kt:816-859
 *     有 `if (!isSubTask)` 守卫），不依赖 floating chat 服务。
 *   - 收完 `Stream<String>` token deltas 合成完整回复文本。
 *
 * 持久化（保留原 R-AGENT-031 路径 4 行为）：
 *   - `saveJobOutput(jobId, output)` ：cron output 文件
 *   - `ChatHistoryManager.addMessage(chatId, ChatMessage("ai", body))`：
 *     直接走持久层（Room），不经 UI-bound `ChatHistoryDelegate`
 *   - `GatewayChatEventBus.Event.ProcessingCompleted(chatId)`：通知活跃 UI 面板从 DB reload
 *
 * IM 分发（保留 R-AGENT-035）：
 *   - `deliver = "origin"` 且 `origin` map 非空 → `HermesGatewayController.dispatchOutgoing`
 *     按 `origin.platform` / `origin.chat_id` 投递到 IM
 *   - `origin.platform == "app"` 短路（in-app chat 已通过 `writeLocalChatNote` 持久化）
 *
 * 递归 cronjob 软防御（R-AGENT-031 路径 3）：保留 bilingual
 *   `[CRON CONTEXT]` / `[CRON 上下文]` 前缀。
 *
 * Origin 透传 / R-AGENT-045（保留）：
 *   - `setSessionVars(platform, chatId)` + `setCronAutoDeliverVars(...)` 在进入
 *     agent 调用前把 origin 写入 ThreadLocal
 *   - `withContext(sessionContextElement())` 把 ThreadLocal snapshot 跨协程切线程透传
 *     （对齐 Python `copy_context().run(func)`）
 *   - `finally` 块 `clearSessionVars()` + `clearCronAutoDeliverVars()`（红线：避免
 *     协程线程池残留污染下一回合）
 */
object CronAgentRunner {

    private const val TAG = "CronAgentRunner"

    /**
     * Bilingual cron-context prefix。agent 通过这两条任一识别"本回合是 cron 触发"，
     * 避免在本回合再注册嵌套 cronjob。
     */
    private const val CRON_CONTEXT_PREFIX_EN =
        "[CRON CONTEXT] This turn was triggered automatically by a scheduled cron job. " +
            "Do NOT register additional cron jobs in this turn unless the user previously asked for nested scheduling. " +
            "Focus on completing the task and producing the user-facing response."
    private const val CRON_CONTEXT_PREFIX_CN =
        "[CRON 上下文] 本回合由计划任务（cronjob）自动触发。" +
            "本回合不要再注册新的 cronjob（除非用户先前明确要求嵌套调度），" +
            "专注完成任务并输出最终回复给用户。"

    /**
     * sendMessage token budget。复用 `StandardChat`+`ManagerTool.spawn_agent`
     * 同款参数（line 1711-1712）；cron 回合典型是单一短任务，64K 上下文 + 0.85
     * 阈值是已验证可用的默认。
     */
    private const val CRON_MAX_TOKENS = 64000
    private const val CRON_TOKEN_USAGE_THRESHOLD = 0.85

    suspend fun run(context: Context, job: Map<String, Any?>) {
        val jobId = (job["id"] as? String) ?: run {
            AppLogger.w(TAG, "skip job without id")
            return
        }
        val jobName = (job["name"] as? String) ?: jobId
        val rawPrompt = (job["prompt"] as? String).orEmpty()

        val wrappedPrompt = buildString {
            appendLine(CRON_CONTEXT_PREFIX_EN)
            appendLine(CRON_CONTEXT_PREFIX_CN)
            appendLine()
            append(rawPrompt)
        }

        AppLogger.d(TAG, "running cron job '$jobName' (id=$jobId) [headless]")
        val _runStartNs = System.nanoTime()
        CronFileLogger.i(TAG, "agent run start jobId=$jobId name='$jobName' promptLen=${rawPrompt.length}")

        // R-AGENT-045 + R-AGENT-035：origin 写入 ThreadLocal，让 agent 回合内
        // `_originFromEnv()` 拿到对的 platform/chat_id（cronjob tools 读、send_message
        // 落 jobs.json origin 字段都需要这条链路），并让 cron auto-deliver 知道目的地。
        @Suppress("UNCHECKED_CAST")
        val origin = job["origin"] as? Map<String, Any?>
        val originPlatform = (origin?.get("platform") as? String)?.trim().orEmpty()
        val originChatId = (origin?.get("chat_id") as? String)?.trim().orEmpty()
        val originThreadId = (origin?.get("thread_id") as? String)?.trim().orEmpty()

        // 解析 chat_id：cron 必须有一个明确的 chat 来落历史。优先级：
        //   1. origin.chat_id（job 创建时捕获的原 chat）
        //   2. ChatHistoryManager.currentChatIdFlow.first()（当前活跃 chat）
        //   3. 创建新 chat（origin 缺失且无活跃 chat 时的 fallback）
        val historyManager = ChatHistoryManager.getInstance(context.applicationContext)
        val resolvedChatId = resolveChatId(historyManager, originChatId)

        setSessionVars(
            platform = originPlatform.ifEmpty { "app" },
            chatId = resolvedChatId,
            threadId = originThreadId
        )
        setCronAutoDeliverVars(
            platform = originPlatform.ifEmpty { "app" },
            chatId = resolvedChatId,
            threadId = originThreadId
        )

        var output = ""
        var success = false
        var errorMessage: String? = null
        // R-CRON-STREAMING-001 (TC-CRON-STREAMING-a..e): per-turn streaming sidecar that
        // dispatches each agent loop turn's assistant reply as a separate IM message.
        //
        // Subscribed/skipped per (originPlatform, originChatId):
        //   - skipped for app-origin (no IM adapter)
        //   - skipped for missing platform / chatId
        //   - skipped for Weixin group chat (`chatId.endsWith("@chatroom")`) — would
        //     otherwise N×spam the group; group still gets the main-path final delivery.
        //
        // `streamingDelivered` flips to true on the first successful per-turn dispatch
        // and is read in `deliver(...)` to skip the main-path IM re-dispatch (the
        // local chat note + `saveJobOutput` are still written, unchanged).
        val streamingEnabled = originPlatform.isNotEmpty() &&
            originChatId.isNotEmpty() &&
            originPlatform != "app" &&
            !(originPlatform == "weixin" && originChatId.endsWith("@chatroom"))
        val streamingDelivered = AtomicBoolean(false)
        try {
            // R-AGENT-045 跨线程修：用 sessionContextElement() 包住 agent 调用，
            // 让 `EnhancedAIService.sendMessage` 内部 `withContext(Dispatchers.IO)`
            // 切线程后仍能读到 ThreadLocal snapshot（对齐 Python
            // `copy_context().run(func)`，SessionContext.kt:187-199）。
            val responseBuilder = StringBuilder()
            withContext(sessionContextElement()) {
                coroutineScope {
                    // R-CRON-STREAMING-001 sidecar: subscribe to AgentEventBus, filter by
                    // (chatId == resolvedChatId) && AssistantDelta, strip markup, dispatch
                    // sequentially. The `.collect { ... }` lambda is sequential per
                    // collector, so dispatches are naturally ordered; we add a `Mutex`
                    // belt-and-suspenders so that the intent is explicit and survives
                    // any future switch to `launchIn` / parallel collectors.
                    val sidecarJob: Job?
                    val sidecarReady: CompletableDeferred<Unit>?
                    if (streamingEnabled) {
                        val dispatchMutex = Mutex()
                        val gatewayForSidecar = HermesGatewayController.getInstance(context.applicationContext)
                        // R-CRON-STREAMING-001 fix (2026-06-25): `AgentEventBus.events` is a
                        // `SharedFlow(replay=0)`, so any AssistantDelta emitted BEFORE the
                        // collector finishes registering is silently dropped. `launch { collect }`
                        // only enqueues the coroutine; subscription registration happens
                        // asynchronously after the launching coroutine suspends. If
                        // `enhancedService.sendMessage(...)` below triggers the agent loop
                        // and the loop emits all per-turn AssistantDelta events before our
                        // collector registers, the sidecar dispatches 0 messages -> the
                        // user sees one combined message via the deliver(...) fallback path.
                        //
                        // Fix: use `onSubscription { ready.complete(Unit) }` (runs AFTER the
                        // subscription is registered with the SharedFlow) and `ready.await()`
                        // before calling `sendMessage`. This guarantees the sidecar collector
                        // is live by the time the agent starts emitting.
                        val ready = CompletableDeferred<Unit>()
                        sidecarReady = ready
                        sidecarJob = launch {
                            try {
                                AgentEventBus.events
                                    .onSubscription { ready.complete(Unit) }
                                    .collect { tagged ->
                                        if (tagged.chatId != resolvedChatId) return@collect
                                        val event = tagged.event
                                        if (event !is AgentEvent.AssistantDelta) return@collect
                                        val stripped = HermesReplyMarkupStripper.strip(event.text).trim()
                                        if (stripped.isNotBlank()) {
                                            dispatchMutex.withLock {
                                                try {
                                                    CronFileLogger.i(
                                                        TAG,
                                                        "streaming dispatch turn=${event.turn} jobId=$jobId " +
                                                            "platform=$originPlatform chat=$originChatId textLen=${stripped.length}"
                                                    )
                                                    val ok = gatewayForSidecar.dispatchOutgoing(
                                                        platform = originPlatform,
                                                        chatId = originChatId,
                                                        text = stripped,
                                                        threadId = originThreadId.takeIf { it.isNotEmpty() },
                                                    )
                                                    if (ok) {
                                                        streamingDelivered.set(true)
                                                    } else {
                                                        CronFileLogger.w(
                                                            TAG,
                                                            "streaming dispatch failed turn=${event.turn} jobId=$jobId " +
                                                                "platform=$originPlatform chat=$originChatId reason=ok=false"
                                                        )
                                                    }
                                                } catch (e: Throwable) {
                                                    // 失败语义：单轮 dispatch 失败不抛、不中断 agent loop，
                                                    // 只记 CronFileLogger，留待 deliver(...) 主路兜底。
                                                    CronFileLogger.w(
                                                        TAG,
                                                        "streaming dispatch threw turn=${event.turn} jobId=$jobId " +
                                                            "platform=$originPlatform chat=$originChatId reason=${e.message}"
                                                    )
                                                }
                                            }
                                        }
                                    }
                            } catch (e: Throwable) {
                                // SharedFlow.collect 在 cancel 时抛 CancellationException 属正常，
                                // 不噪声；其他异常落日志，不影响主路。
                                if (e !is kotlinx.coroutines.CancellationException) {
                                    CronFileLogger.w(
                                        TAG,
                                        "streaming sidecar collect failed jobId=$jobId reason=${e.message}"
                                    )
                                }
                            } finally {
                                // 防御：如果 collect 还没注册就被取消/抛异常，确保 await 不会永远卡住。
                                if (!ready.isCompleted) ready.complete(Unit)
                            }
                        }
                    } else {
                        CronFileLogger.i(
                            TAG,
                            "streaming dispatch skipped jobId=$jobId platform=$originPlatform " +
                                "chat=$originChatId reason=${
                                    when {
                                        originPlatform.isEmpty() || originChatId.isEmpty() -> "no-origin"
                                        originPlatform == "app" -> "app-origin"
                                        originPlatform == "weixin" && originChatId.endsWith("@chatroom") -> "weixin-group"
                                        else -> "disabled"
                                    }
                                }"
                        )
                        sidecarJob = null
                        sidecarReady = null
                    }

                    // 等订阅注册完成后再触发 agent，避免 SharedFlow(replay=0) 把早期
                    // AssistantDelta 丢给虚空。如果 sidecar 因为 streamingEnabled=false
                    // 没启动，sidecarReady 为 null，直接走主路即可。
                    sidecarReady?.await()

                    val enhancedService = EnhancedAIService.getInstance(context.applicationContext)
                    val responseStream = enhancedService.sendMessage(
                        message = wrappedPrompt,
                        chatId = resolvedChatId,
                        chatHistory = emptyList(), // cron 回合 fresh context；历史已由 EnhancedAIService 从 DB/state 自己加载
                        maxTokens = CRON_MAX_TOKENS,
                        tokenUsageThreshold = CRON_TOKEN_USAGE_THRESHOLD,
                        isSubTask = true, // 关键：跳过 startAiService 前台通知 + UI state 更新
                        stream = true
                    )
                    responseStream.collect { chunk ->
                        responseBuilder.append(chunk)
                    }
                    // 主路 collect 完成后，关掉 sidecar：AssistantDelta 已经全部 emit 过
                    // （`AgentLoop.kt:582` 在每个 turn 结束时同步 emit），再等下去也不会有新事件。
                    sidecarJob?.cancel()
                }
            }
            output = responseBuilder.toString()
            val preStripLen = output.length
            // TC-CRON-SANITIZE-d (R-AGENT-031 / R-AGENT-035): strip Hermes internal
            // XML markup (<think>/<tool>/<tool_result>/<status>) BEFORE persistence
            // (saveJobOutput), local chat note (writeLocalChatNote), and IM dispatch
            // (gateway.dispatchOutgoing).  Normal IM path strips via
            // HermesGatewayController.extractFinalReply -> stripMarkup, but the
            // headless cron path here was bypassing it -- bug 2026-06-24:
            // "<think>The cron job triggered a reminder to drink water...</think>"
            // was leaking into Weixin delivery.
            output = HermesReplyMarkupStripper.strip(output).trim()
            CronFileLogger.i(
                TAG,
                "strip applied jobId=$jobId preStripLen=$preStripLen postStripLen=${output.length}"
            )
            // 2026-06-24 regression-guard: mirror normal IM path
            // (`HermesGatewayController.extractFinalReply().ifEmpty { "(empty response)" }`,
            //  line 547-549) — if the stream was entirely <think>...</think> or
            // got cut mid-think (UNCLOSED_THINK_REGEX swallowed everything), still
            // deliver a visible placeholder instead of silently skipping dispatch.
            // Without this the cron→Weixin path silently drops empty-after-strip
            // jobs, breaking R-AGENT-031/035 again from the user's PoV.
            if (output.isBlank()) {
                CronFileLogger.w(
                    TAG,
                    "strip emptied output jobId=$jobId preStripLen=$preStripLen — substituting placeholder"
                )
                output = "(empty response)"
            }
            success = output.isNotBlank()
            if (!success) {
                errorMessage = "headless agent returned empty response"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "headless agent invocation failed for job '$jobId'", e)
            errorMessage = e.message ?: "headless agent invocation threw"
            success = false
        } finally {
            // R-AGENT-033 红线：协程线程池残留 origin 会污染下一回合。
            clearSessionVars()
            clearCronAutoDeliverVars()
        }

        // 写 cron output 文件（即使失败也写，方便排查；失败时 output=""）
        try {
            saveJobOutput(jobId, output)
        } catch (e: Exception) {
            AppLogger.w(TAG, "saveJobOutput failed for '$jobId': ${e.message}")
        }

        // 写 chat history + IM dispatch
        var deliveryError: String? = null
        if (success && resolvedChatId.isNotBlank()) {
            try {
                deliver(
                    context = context,
                    chatId = resolvedChatId,
                    jobName = jobName,
                    jobId = jobId,
                    body = output,
                    job = job,
                    streamingDelivered = streamingDelivered.get(),
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "delivery failed for job '$jobId'", e)
                deliveryError = e.message ?: "delivery threw"
            }
        } else if (success) {
            AppLogger.w(TAG, "job '$jobId' succeeded but no chatId resolved; skipping delivery note")
        }

        markJobRun(
            jobId,
            success = success,
            error = if (success) null else errorMessage,
            deliveryError = deliveryError
        )
        val durationMs = (System.nanoTime() - _runStartNs) / 1_000_000L
        CronFileLogger.i(
            TAG,
            "agent run done jobId=$jobId success=$success outputLen=${output.length} " +
                "durationMs=$durationMs deliveryError=${deliveryError ?: "-"}"
        )
    }

    /**
     * 解析当前回合应该落进哪个 chat。
     * 优先 origin.chat_id（job 创建时捕获），其次当前活跃 chat，最后创建新 chat。
     */
    private suspend fun resolveChatId(
        historyManager: ChatHistoryManager,
        originChatId: String
    ): String {
        if (originChatId.isNotBlank() && historyManager.chatExists(originChatId)) {
            return originChatId
        }
        val current = historyManager.currentChatIdFlow.first()
        if (!current.isNullOrBlank() && historyManager.chatExists(current)) {
            return current
        }
        // Fallback：起新 chat，避免 cron 完全无处落消息
        val newChat = historyManager.createNewChat(setAsCurrentChat = false)
        return newChat.id
    }

    /**
     * R-AGENT-035: Append the cron output to the originating chat。
     *
     * Routing rules（mirrors Python `gateway/run.py` cron deliver loop）：
     * - `deliver = "origin"` 且 `origin` map 非空 → 调
     *   `HermesGatewayController.dispatchOutgoing` 投递到 IM platform。同时本地
     *   `ChatHistoryManager` 也写一份（in-app 仍能看到，无信息丢失）。
     * - `deliver = "local"`（或 `origin` 缺失）→ 只写
     *   `ChatHistoryManager` + emit `ProcessingCompleted` event。
     * - `origin.platform == "app"` 短路：in-app chat 没有 IM adapter，
     *   `writeLocalChatNote` 已经在顶部无条件调过了。
     *
     * `deliveryError` 通过 caller 的 `markJobRun(...)` 浮到
     * `last_delivery_error` 字段，cronjob list 可见。
     */
    private suspend fun deliver(
        context: Context,
        chatId: String,
        jobName: String,
        jobId: String,
        body: String,
        job: Map<String, Any?>,
        streamingDelivered: Boolean,
    ) {
        val deliverMode = (job["deliver"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "local"
        @Suppress("UNCHECKED_CAST")
        val origin = job["origin"] as? Map<String, Any?>
        CronFileLogger.i(
            TAG,
            "deliver mode=$deliverMode jobId=$jobId originPlatform=${(origin?.get("platform") as? String).orEmpty()} " +
                "originChatId=${(origin?.get("chat_id") as? String).orEmpty()} bodyLen=${body.length} " +
                "streamingDelivered=$streamingDelivered"
        )

        // 始终写本地 chat history，让用户在 app UI 里也能看到
        writeLocalChatNote(context, chatId, jobName, jobId, body)

        // R-CRON-STREAMING-001 (TC-CRON-STREAMING-e): 旁路已经把 per-turn 回复逐条发到 IM 了，
        // 这里就**不再**整段 dispatchOutgoing，否则用户收到 N 条 turn bubble + 1 条整段重复。
        // 注意：`writeLocalChatNote` 已经在上面无条件调过了，app UI / Room history 不受影响；
        // `saveJobOutput` 在 caller 那一层已经写过了。
        if (streamingDelivered) {
            CronFileLogger.i(
                TAG,
                "deliver IM-skip jobId=$jobId reason=streamingDelivered " +
                    "(per-turn sidecar already pushed messages to IM)"
            )
            return
        }

        val originMatched = deliverMode == "origin" && origin != null
        if (!originMatched) {
            AppLogger.d(TAG, "deliver: job '$jobId' deliver=$deliverMode origin=${origin != null}; local-only path")
            return
        }

        val originPlatform = (origin!!["platform"] as? String)?.trim().orEmpty()
        val originChatId = (origin["chat_id"] as? String)?.trim().orEmpty()
        val originThreadId = (origin["thread_id"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (originPlatform.isEmpty() || originChatId.isEmpty()) {
            AppLogger.w(
                TAG,
                "deliver: job '$jobId' deliver=origin but origin map missing platform/chat_id " +
                    "(platform='$originPlatform' chat_id='$originChatId'); skipping IM dispatch"
            )
            return
        }

        // R-AGENT-045: app-origin 短路 —— in-app chat 没有 IM adapter，
        // dispatchOutgoing 会返回 false → 抛 IllegalStateException → markJobRun
        // 误记 last_delivery_error。writeLocalChatNote 已经在顶部无条件调过了。
        if (originPlatform == "app") {
            AppLogger.d(
                TAG,
                "deliver: job '$jobId' origin=app chatId=$originChatId; " +
                    "in-app chat note already written, skipping IM dispatch"
            )
            return
        }

        // TC-CRON-EXACT-j (2026-06-23 第四次 bugfix)：cron 触发的典型场景下，
        // GatewayForegroundService 经常已被 OEM ROM 在后台杀掉 ——
        // `HermesGatewayController.runner == null` → dispatchOutgoing 第一行立刻
        // return false（HermesGatewayController.kt:198-203） → 抛 IllegalStateException
        // → 用户看到 "app chat 收到了，但微信收不到"。
        //
        // 修法：进入 IM dispatch 之前先唤醒 GatewayForegroundService 并等
        // status==RUNNING（runner 字段被赋值的同一时刻），再调 dispatchOutgoing。
        // app-origin 路径在上面已经短路 return，不会触达这条 warmup。
        val gateway = HermesGatewayController.getInstance(context.applicationContext)
        if (gateway.status.value != HermesGatewayController.Status.RUNNING) {
            AppLogger.d(
                TAG,
                "deliver: job '$jobId' gateway status=${gateway.status.value}, warming up GatewayForegroundService " +
                    "before dispatching to platform=$originPlatform"
            )
            try {
                GatewayForegroundService.start(context.applicationContext)
            } catch (e: Throwable) {
                // startForegroundService 在 BG-launch 受限的极端情况下可能抛
                // ForegroundServiceStartNotAllowedException —— 此处不阻断流程，
                // 让下面的 withTimeoutOrNull 等到超时再 throw warmup timeout。
                AppLogger.w(TAG, "deliver: GatewayForegroundService.start threw for job '$jobId': ${e.message}")
            }
            val reached = withTimeoutOrNull(30_000L) {
                gateway.status.first { it == HermesGatewayController.Status.RUNNING }
                true
            }
            if (reached != true) {
                throw IllegalStateException(
                    "gateway warmup timeout: HermesGatewayController did not reach RUNNING within 30s " +
                        "for platform=$originPlatform chatId=$originChatId (GatewayForegroundService " +
                        "failed to start or stuck in STARTING/FAILED)"
                )
            }
            AppLogger.d(TAG, "deliver: job '$jobId' gateway warmup completed, proceeding to dispatchOutgoing")
        }

        AppLogger.d(
            TAG,
            "deliver: job '$jobId' dispatching to platform=$originPlatform chatId=$originChatId thread=$originThreadId len=${body.length}"
        )
        val ok = try {
            gateway.dispatchOutgoing(
                platform = originPlatform,
                chatId = originChatId,
                text = body,
                threadId = originThreadId,
            )
        } catch (e: Throwable) {
            AppLogger.e(TAG, "deliver: dispatchOutgoing threw for job '$jobId': ${e.message}", e)
            CronFileLogger.e(
                TAG,
                "deliver FAIL jobId=$jobId platform=$originPlatform chat=$originChatId reason=${e.message}"
            )
            throw e
        }
        if (!ok) {
            CronFileLogger.e(
                TAG,
                "deliver FAIL jobId=$jobId platform=$originPlatform chat=$originChatId " +
                    "reason=dispatchOutgoing returned false (gateway not running or adapter missing)"
            )
            throw IllegalStateException(
                "dispatchOutgoing returned false for platform=$originPlatform chatId=$originChatId " +
                    "(gateway not running or adapter not registered)"
            )
        }
        CronFileLogger.i(
            TAG,
            "deliver SUCCESS jobId=$jobId platform=$originPlatform chat=$originChatId bodyLen=${body.length}"
        )
    }

    /**
     * Append a `[CRON]` delivery note to the chat via the persistence
     * layer ([ChatHistoryManager.addMessage]). Then emit
     * [GatewayChatEventBus.Event.ProcessingCompleted] so any active chat
     * panel reloads from DB.
     */
    private suspend fun writeLocalChatNote(
        context: Context,
        chatId: String,
        jobName: String,
        jobId: String,
        body: String
    ) {
        val historyManager = ChatHistoryManager.getInstance(context.applicationContext)
        val noteContent = buildString {
            append("[CRON] Cron job '")
            append(jobName)
            append("' (id=")
            append(jobId)
            appendLine(") completed.")
            if (body.isNotBlank()) {
                appendLine()
                append(body)
            }
        }
        val message = ChatMessage(sender = "ai", content = noteContent)
        historyManager.addMessage(chatId, message)
        GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingCompleted(chatId))
    }
}
