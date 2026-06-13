package com.ai.assistance.operit.core.cron

import android.content.Context
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.hermes.gateway.GatewayChatEventBus
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
                deliver(context, chatId = resolvedChatId, jobName = jobName, jobId = jobId, body = output)
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
     * Append a `[CRON]` delivery note to the chat via the persistence
     * layer ([ChatHistoryManager.addMessage]). Then emit
     * [GatewayChatEventBus.Event.ProcessingCompleted] so any active chat
     * panel reloads from DB.
     */
    private suspend fun deliver(
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
