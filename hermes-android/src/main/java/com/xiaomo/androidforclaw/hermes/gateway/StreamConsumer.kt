package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Stream consumer — consumes the agent's streaming output and delivers
 * partial/final responses to the platform adapter.
 *
 * Ported from gateway/stream_consumer.py
 */

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single chunk from the agent's streaming output.
 */
data class StreamChunk(
    /** The text content of this chunk. */
    val text: String = "",
    /** Whether this is the final chunk. */
    val isFinal: Boolean = false,
    /** Optional tool-call metadata (JSON). */
    val toolCall: JSONObject? = null,
    /** Optional usage metadata (tokens, etc.). */
    val usage: JSONObject? = null,
    /** Optional error message. */
    val error: String? = null,
    /** Optional annotation metadata. */
    val annotations: List<JSONObject> = emptyList(),
)

/**
 * Configuration for the stream consumer.
 */
data class StreamConsumerConfig(
    /** Minimum interval between edit updates (milliseconds). */
    val minEditIntervalMs: Long = 1000,
    /** Maximum number of edits per message. */
    val maxEdits: Int = 50,
    /** Whether to use edit-in-place (true) or send new messages (false). */
    val useEditInPlace: Boolean = true,
    /** Whether to send typing indicator before streaming starts. */
    val sendTypingIndicator: Boolean = true,
    /** Whether to suppress empty chunks. */
    val suppressEmptyChunks: Boolean = true,
)

/**
 * Stream consumer — consumes streaming chunks and delivers them to the
 * platform adapter.
 *
 * Manages the lifecycle of a single streaming response: sends an initial
 * message, then updates it with progressive edits (if the platform supports
 * edit-in-place) or sends new messages for each significant update.
 */
class StreamConsumer(
    private val deliveryRouter: DeliveryRouter,
    private val config: StreamConsumerConfig = StreamConsumerConfig(),
) {
    companion object {
        private const val TAG = "StreamConsumer"
    }

    /** Whether streaming is currently active. */
    private val _isActive = AtomicBoolean(false)

    /** Accumulated text from all chunks. */
    private var _accumulatedText = StringBuilder()

    /** The message id of the initial message (for edit-in-place). */
    private var _messageId: String? = null

    /** The platform name. */
    private var _platform: String = ""

    /** The chat id. */
    private var _chatId: String = ""

    /** The number of edits sent so far. */
    private var _editCount = 0

    /** Timestamp of the last edit. */
    private var _lastEditTime = 0L

    /**
     * Start consuming a stream of chunks.
     *
     * @param platform  Platform name.
     * @param chatId    Target chat/channel id.
     * @param replyTo   Optional message id to reply to.
     * @param chunks    Flow of StreamChunk from the agent.
     * @return The final message id (if available).
     */
    suspend fun consume(
        platform: String,
        chatId: String,
        replyTo: String? = null,
        chunks: Flow<StreamChunk>,
    ): String? {
        if (_isActive.getAndSet(true)) {
            Log.w(TAG, "StreamConsumer is already active")
            return null
        }

        _platform = platform
        _chatId = chatId
        _accumulatedText.clear()
        _editCount = 0
        _lastEditTime = 0L

        try {
            // Send typing indicator if configured
            if (config.sendTypingIndicator) {
                deliveryRouter.sendTyping(platform, chatId)
            }

            // Consume chunks
            chunks.collect { chunk ->
                if (chunk.error != null) {
                    Log.w(TAG, "Stream error: ${chunk.error}")
                    _handleError(chunk.error)
                    return@collect
                }

                if (chunk.isFinal) {
                    _handleFinalChunk(chunk, replyTo)
                    return@collect
                }

                _handleProgressChunk(chunk, replyTo)
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Stream cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream consumer error: ${e.message}")
            _handleError(e.message ?: "Unknown error")
        } finally {
            _isActive.set(false)
        }

        return _messageId
    }

    /**
     * Consume from a Channel instead of a Flow.
     */
    suspend fun consumeChannel(
        platform: String,
        chatId: String,
        replyTo: String? = null,
        channel: Channel<StreamChunk>,
    ): String? {
        if (_isActive.getAndSet(true)) {
            Log.w(TAG, "StreamConsumer is already active")
            return null
        }

        _platform = platform
        _chatId = chatId
        _accumulatedText.clear()
        _editCount = 0
        _lastEditTime = 0L

        try {
            if (config.sendTypingIndicator) {
                deliveryRouter.sendTyping(platform, chatId)
            }

            for (chunk in channel) {
                if (chunk.error != null) {
                    _handleError(chunk.error)
                    break
                }
                if (chunk.isFinal) {
                    _handleFinalChunk(chunk, replyTo)
                    break
                }
                _handleProgressChunk(chunk, replyTo)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream consumer error: ${e.message}")
            _handleError(e.message ?: "Unknown error")
        } finally {
            _isActive.set(false)
        }

        return _messageId
    }

    /** True when streaming is active. */
    val isActive: Boolean get() = _isActive.get()

    /** Cancel streaming. */
    fun cancel() {
        _isActive.set(false)
    }

    // ------------------------------------------------------------------
    // Internal handlers
    // ------------------------------------------------------------------

    private suspend fun _handleProgressChunk(chunk: StreamChunk, replyTo: String?) {
        if (config.suppressEmptyChunks && chunk.text.isEmpty()) return

        _accumulatedText.append(chunk.text)
        val text = _accumulatedText.toString()

        // Decide whether to send an edit
        val now = System.currentTimeMillis()
        val timeSinceLastEdit = now - _lastEditTime
        val shouldEdit = config.useEditInPlace
            && _messageId != null
            && _editCount < config.maxEdits
            && timeSinceLastEdit >= config.minEditIntervalMs

        if (shouldEdit) {
            _editMessage(text)
        } else if (_messageId == null) {
            // First chunk — send initial message
            _sendInitialMessage(text, replyTo)
        }
    }

    private suspend fun _handleFinalChunk(chunk: StreamChunk, replyTo: String?) {
        _accumulatedText.append(chunk.text)
        val finalText = _accumulatedText.toString()

        if (finalText.isNotEmpty()) {
            if (_messageId != null && config.useEditInPlace) {
                _editMessage(finalText)
            } else {
                _sendInitialMessage(finalText, replyTo)
            }
        }

        _isActive.set(false)
    }

    private suspend fun _handleError(error: String) {
        val errorText = if (_accumulatedText.isNotEmpty()) {
            "${_accumulatedText}\n\n[Error: $error]"
        } else {
            "[Error: $error]"
        }

        if (_messageId != null) {
            _editMessage(errorText)
        } else {
            deliveryRouter.deliverText(_platform, _chatId, errorText)
        }

        _isActive.set(false)
    }

    private suspend fun _sendInitialMessage(text: String, replyTo: String?) {
        val result = deliveryRouter.deliverText(_platform, _chatId, text, replyTo)
        if (result.success) {
            _messageId = result.messageId
            _lastEditTime = System.currentTimeMillis()
            _editCount++
        }
    }

    private suspend fun _editMessage(text: String) {
        val adapter = deliveryRouter.getAdapter(_platform) ?: return
        try {
            // Most adapters support edit via sendText with the same chatId
            // and a special "edit_message_id" in metadata
            val result = adapter.send(_chatId, text, null, null)
            if (result.success) {
                _lastEditTime = System.currentTimeMillis()
                _editCount++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Edit failed: ${e.message}")
        }
    }



    /** True if at least one message was sent or edited during the run. */
    fun alreadySent(): Boolean {
        return false
    }
    /** True when the stream consumer delivered the final assistant reply. */
    fun finalResponseSent(): Boolean {
        return false
    }
    /** Finalize the current stream segment and start a fresh message. */
    fun onSegmentBreak(): Unit {
        // TODO: implement onSegmentBreak
    }
    /** Queue a completed interim assistant commentary message. */
    fun onCommentary(text: String): Unit {
        // TODO: implement onCommentary
    }
    fun _resetSegmentState(): Unit {
        // TODO: implement _resetSegmentState
    }
    /** Thread-safe callback — called from the agent's worker thread. */
    fun onDelta(text: String): Unit {
        // TODO: implement onDelta
    }
    /** Signal that the stream is complete. */
    fun finish(): Unit {
        // TODO: implement finish
    }
    /** Add a text delta to the accumulated buffer, suppressing think blocks. */
    fun _filterAndAccumulate(text: String): Unit {
        // TODO: implement _filterAndAccumulate
    }
    /** Flush any held-back partial-tag buffer into accumulated text. */
    fun _flushThinkBuffer(): Unit {
        // TODO: implement _flushThinkBuffer
    }
    /** Async task that drains the queue and edits the platform message. */
    suspend fun run(): Unit {
        // TODO: implement run
    }
    /** Strip MEDIA: directives and internal markers from text before display. */
    fun _cleanForDisplay(text: String): String {
        return ""
    }
    /** Send a new message chunk, optionally threaded to a previous message. */
    suspend fun _sendNewChunk(text: String, replyToId: String?): String? {
        return null
    }
    /** Return the visible text already shown in the streamed message. */
    fun _visiblePrefix(): String {
        return ""
    }
    /** Return only the part of final_text the user has not already seen. */
    fun _continuationText(finalText: String): String {
        return ""
    }
    /** Split text into reasonably sized chunks for fallback sends. */
    fun _splitTextChunks(text: String, limit: Int): List<String> {
        return emptyList()
    }
    /** Send the final continuation after streaming edits stop working. */
    suspend fun _sendFallbackFinal(text: String): Unit {
        // TODO: implement _sendFallbackFinal
    }
    /** Check if a SendResult failure is due to flood control / rate limiting. */
    fun _isFloodError(result: Any?): Boolean {
        return false
    }
    /** Best-effort edit to remove the cursor from the last visible message. */
    suspend fun _tryStripCursor(): Unit {
        // TODO: implement _tryStripCursor
    }
    /** Send a completed interim assistant commentary message. */
    suspend fun _sendCommentary(text: String): Boolean {
        return false
    }
    /** Send or edit the streaming message. */
    suspend fun _sendOrEdit(text: String): Boolean {
        return false
    }

}
