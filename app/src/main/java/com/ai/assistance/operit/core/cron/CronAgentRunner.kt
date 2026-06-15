package com.ai.assistance.operit.core.cron

import android.content.Context
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.hermes.gateway.GatewayChatEventBus
import com.ai.assistance.operit.hermes.gateway.HermesGatewayController
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequest
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequestExecutor
import com.ai.assistance.operit.util.AppLogger
import com.xiaomo.hermes.hermes.cron.markJobRun
import com.xiaomo.hermes.hermes.cron.saveJobOutput

/**
 * R-AGENT-031: headless agent invocation for cron jobs.
 *
 * Runs a single due cron job by invoking [ExternalChatRequestExecutor]
 * (the same path the external-chat broadcast uses), then writes a
 * delivery note back through [ChatHistoryManager] (path 4 — persistence
 * layer, not UI-bound `ChatHistoryDelegate`) and emits
 * [GatewayChatEventBus.Event.ProcessingCompleted] so any active chat
 * panel reloads from DB.
 *
 * Path 3 (recursive cronjob soft-defense): the prompt is wrapped with
 * bilingual `[CRON CONTEXT]` / `[CRON 上下文]` tags so the agent knows
 * the run was fired by cron and avoids registering more cron jobs in
 * this turn.
 *
 * R-AGENT-035: cron tick real-path origin → IM delivery.
 *
 * R-AGENT-033 wired `cronOutboundDispatcher` into `Scheduler.deliverResult`,
 * but Android's actual cron tick goes through this file (`CronTickWorker` →
 * `CronAgentRunner.run` → `CronAgentRunner.deliver`), bypassing
 * `Scheduler.deliverResult`. So this file consumes `job["origin"]` /
 * `job["deliver"]` directly and routes IM delivery through
 * [HermesGatewayController.dispatchOutgoing] (which is the same target
 * the dispatcher injection in `Scheduler.kt` would have hit). Local-only
 * jobs (`deliver = "local"` or origin missing) keep the original
 * [ChatHistoryManager] path so the user can still see cron output in the
 * app UI.
 */
object CronAgentRunner {

    private const val TAG = "CronAgentRunner"

    /**
     * Bilingual cron-context prefix. The agent reads both lines so
     * neither English nor Chinese system-prompt locale sneaks past.
     */
    private const val CRON_CONTEXT_PREFIX_EN =
        "[CRON CONTEXT] This turn was triggered automatically by a scheduled cron job. " +
            "Do NOT register additional cron jobs in this turn unless the user previously asked for nested scheduling. " +
            "Focus on completing the task and producing the user-facing response."
    private const val CRON_CONTEXT_PREFIX_CN =
        "[CRON 上下文] 本回合由计划任务（cronjob）自动触发。" +
            "本回合不要再注册新的 cronjob（除非用户先前明确要求嵌套调度），" +
            "专注完成任务并输出最终回复给用户。"

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

        AppLogger.d(TAG, "running cron job '$jobName' (id=$jobId)")
        val executor = ExternalChatRequestExecutor(context.applicationContext)
        val request = ExternalChatRequest(
            requestId = "cron-$jobId-${System.currentTimeMillis()}",
            message = wrappedPrompt,
            createNewChat = false,
            createIfNone = true,
            returnToolStatus = false
        )

        val result = try {
            executor.execute(request)
        } catch (e: Exception) {
            AppLogger.e(TAG, "agent invocation failed for job '$jobId'", e)
            markJobRun(jobId, success = false, error = e.message ?: "agent invocation threw")
            return
        }

        val output = result.aiResponse.orEmpty()
        try {
            saveJobOutput(jobId, output)
        } catch (e: Exception) {
            AppLogger.w(TAG, "saveJobOutput failed for '$jobId': ${e.message}")
        }

        val resolvedChatId = result.chatId?.takeIf { it.isNotBlank() }
        var deliveryError: String? = null
        if (resolvedChatId != null) {
            try {
                deliver(
                    context = context,
                    chatId = resolvedChatId,
                    jobName = jobName,
                    jobId = jobId,
                    body = output,
                    job = job,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "delivery failed for job '$jobId'", e)
                deliveryError = e.message ?: "delivery threw"
            }
        } else if (result.success) {
            AppLogger.w(TAG, "job '$jobId' succeeded but no chatId in result; skipping delivery note")
        }

        markJobRun(
            jobId,
            success = result.success,
            error = if (result.success) null else result.error,
            deliveryError = deliveryError
        )
    }

    /**
     * R-AGENT-035: Append the cron output to the originating chat.
     *
     * Routing rules (mirrors Python `gateway/run.py` cron deliver loop):
     * - `deliver = "origin"` and `origin` map present → invoke
     *   [HermesGatewayController.dispatchOutgoing] for the IM platform
     *   captured at job-creation time. Also writes to local
     *   [ChatHistoryManager] so the user can see the same output in the
     *   app UI (no information loss either way).
     * - `deliver = "local"` (or any value when `origin` is missing) →
     *   write only to [ChatHistoryManager] and emit
     *   [GatewayChatEventBus.Event.ProcessingCompleted]. This is the
     *   R-AGENT-031 baseline behavior, untouched by this change.
     *
     * `deliveryError` is bubbled up via the caller's `markJobRun(...)`
     * so `last_delivery_error` is observable by the user via
     * `cronjob(action="list")`.
     */
    private suspend fun deliver(
        context: Context,
        chatId: String,
        jobName: String,
        jobId: String,
        body: String,
        job: Map<String, Any?>,
    ) {
        val deliverMode = (job["deliver"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "local"
        @Suppress("UNCHECKED_CAST")
        val origin = job["origin"] as? Map<String, Any?>

        // Always write to local chat history so the user can review cron
        // output in the app UI. R-AGENT-035 only ADDS the IM dispatch path;
        // it does not replace R-AGENT-031's local persistence.
        writeLocalChatNote(context, chatId, jobName, jobId, body)

        // Decide whether to ALSO push to an IM platform.
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

        AppLogger.d(
            TAG,
            "deliver: job '$jobId' dispatching to platform=$originPlatform chatId=$originChatId thread=$originThreadId len=${body.length}"
        )
        val gateway = HermesGatewayController.getInstance(context.applicationContext)
        val ok = try {
            gateway.dispatchOutgoing(
                platform = originPlatform,
                chatId = originChatId,
                text = body,
                threadId = originThreadId,
            )
        } catch (e: Throwable) {
            AppLogger.e(TAG, "deliver: dispatchOutgoing threw for job '$jobId': ${e.message}", e)
            throw e
        }
        if (!ok) {
            // Surface to caller so markJobRun records last_delivery_error.
            throw IllegalStateException(
                "dispatchOutgoing returned false for platform=$originPlatform chatId=$originChatId " +
                    "(gateway not running or adapter not registered)"
            )
        }
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
