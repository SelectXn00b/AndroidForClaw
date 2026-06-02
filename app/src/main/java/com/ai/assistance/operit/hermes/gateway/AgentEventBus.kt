package com.ai.assistance.operit.hermes.gateway

import com.xiaomo.hermes.hermes.AgentEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局 [AgentEvent] 转发总线。
 *
 * `EnhancedAIService.runAgentLoopViaHermes` 和 `HermesAdapter.sendMessage` 在创建
 * [com.xiaomo.hermes.hermes.HermesAgentLoop] 时，会把每个 [AgentEvent] 顺手转发到
 * 这里，方便其他模块（如 agent 状态悬浮球 service）订阅而不必去改 hermes loop 内部。
 *
 * 这是只读型 hook：sink 转发后再 emit，不影响原有事件消费链。
 *
 * @param chatId 对应 [HermesAgentLoop.taskId]（gateway 路径形如 "gw:feishu:xxx"，
 *               UI 路径形如真实 chatId 或 "chat_<execId>"）
 */
object AgentEventBus {

    data class TaggedEvent(val chatId: String, val event: AgentEvent)

    private val _events = MutableSharedFlow<TaggedEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<TaggedEvent> = _events.asSharedFlow()

    fun emit(chatId: String, event: AgentEvent) {
        _events.tryEmit(TaggedEvent(chatId, event))
    }
}

/**
 * 全局 token 用量总线。
 *
 * `EnhancedAIService` 在 `onTokensUpdated` / `onTurnComplete` 回调里把 input/output
 * token 转发到这里。HermesAdapter 路径目前没接 token 回调（[OperitChatCompletionServer]
 * 上 onTokensUpdated/onTurnComplete 默认是 no-op），所以这个 bus 在 UI/EnhancedAIService
 * 路径有数据，gateway-direct adapter 路径暂无。
 */
object AgentTokenBus {

    data class TokenUsage(
        val chatId: String,
        /** 单次请求 input token（注意 onTokensUpdated 是单次请求的累加值，不是 delta）*/
        val input: Int,
        /** 单次请求 output token */
        val output: Int,
        /** 是否本轮已完成（true=onTurnComplete 触发，可作为 turn 结束信号） */
        val turnComplete: Boolean,
    )

    private val _usage = MutableSharedFlow<TokenUsage>(extraBufferCapacity = 64)
    val usage: SharedFlow<TokenUsage> = _usage.asSharedFlow()

    fun emit(chatId: String, input: Int, output: Int, turnComplete: Boolean) {
        _usage.tryEmit(TokenUsage(chatId, input, output, turnComplete))
    }
}
