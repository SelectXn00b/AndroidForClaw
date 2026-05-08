package com.ai.assistance.operit.hermes.gateway

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight event bus for gateway → UI lifecycle signals.
 * Events notify the UI layer that a gateway chat has changed state,
 * so it can reload messages from DB and update processing indicators.
 */
object GatewayChatEventBus {

    sealed class Event(val chatId: String) {
        /** User message has been written to the DB. */
        class UserMessagePersisted(chatId: String) : Event(chatId)
        /** AI processing has started for this chat. */
        class ProcessingStarted(chatId: String) : Event(chatId)
        /** AI processing completed; final message written to DB. */
        class ProcessingCompleted(chatId: String) : Event(chatId)
        /** AI processing failed. */
        class ProcessingFailed(chatId: String) : Event(chatId)
        /** Streaming content snapshot has been persisted to DB. */
        class StreamingUpdate(chatId: String) : Event(chatId)
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
