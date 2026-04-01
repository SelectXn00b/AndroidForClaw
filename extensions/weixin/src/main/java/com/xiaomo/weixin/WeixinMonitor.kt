/**
 * OpenClaw Source Reference:
 * - @tencent-weixin/openclaw-weixin/src/monitor/monitor.ts
 *
 * Long-poll loop: getUpdates → parse → dispatch inbound messages.
 */
package com.xiaomo.weixin

import android.util.Log
import com.xiaomo.weixin.api.*
import com.xiaomo.weixin.messaging.ContextTokenStore
import com.xiaomo.weixin.messaging.WeixinInboundMessage
import com.xiaomo.weixin.messaging.parseInbound
import com.xiaomo.weixin.storage.WeixinAccountStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WeixinMonitor(
    private val api: WeixinApi,
    private val accountId: String,
) {
    companion object {
        private const val TAG = "WeixinMonitor"
        private const val SESSION_EXPIRED_ERRCODE = -14
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val BACKOFF_DELAY_MS = 30_000L
        private const val RETRY_DELAY_MS = 2_000L
        private const val SESSION_PAUSE_MS = 5 * 60_000L // 5 min
        private const val SEEN_IDS_MAX_SIZE = 500
    }

    private val _messageFlow = MutableSharedFlow<WeixinInboundMessage>(
        replay = 0,
        extraBufferCapacity = 100,
    )
    val messageFlow: SharedFlow<WeixinInboundMessage> = _messageFlow.asSharedFlow()

    private var monitorJob: Job? = null
    @Volatile private var running = false

    /** 消息 ID 去重：防止 getUpdates 重复返回同一条消息 */
    private val seenMessageIds = java.util.Collections.newSetFromMap(
        java.util.LinkedHashMap<Long, Boolean>()
    )
    @Volatile private var seenIdsLock = Object()

    fun start(scope: CoroutineScope) {
        if (running) {
            Log.w(TAG, "Monitor already running")
            return
        }
        running = true
        monitorJob = scope.launch(Dispatchers.IO) {
            runPollLoop()
        }
        Log.i(TAG, "Monitor started for account=$accountId")
    }

    fun stop() {
        running = false
        monitorJob?.cancel()
        monitorJob = null
        synchronized(seenIdsLock) {
            seenMessageIds.clear()
        }
        Log.i(TAG, "Monitor stopped")
    }

    private suspend fun runPollLoop() {
        var getUpdatesBuf = WeixinAccountStore.loadSyncBuf()
        var consecutiveFailures = 0

        if (getUpdatesBuf.isNotBlank()) {
            Log.i(TAG, "Resuming from saved sync buf (${getUpdatesBuf.length} bytes)")
        } else {
            Log.i(TAG, "No previous sync buf, starting fresh")
        }

        while (running && currentCoroutineContext().isActive) {
            try {
                val resp = api.getUpdates(getUpdatesBuf)

                // Check for API errors
                val isApiError = (resp.ret != null && resp.ret != 0) ||
                        (resp.errcode != null && resp.errcode != 0)

                if (isApiError) {
                    val isSessionExpired = resp.errcode == SESSION_EXPIRED_ERRCODE ||
                            resp.ret == SESSION_EXPIRED_ERRCODE

                    if (isSessionExpired) {
                        Log.e(TAG, "Session expired (errcode=$SESSION_EXPIRED_ERRCODE), pausing ${SESSION_PAUSE_MS / 60000}min")
                        consecutiveFailures = 0
                        delay(SESSION_PAUSE_MS)
                        continue
                    }

                    consecutiveFailures++
                    Log.e(TAG, "getUpdates error: ret=${resp.ret} errcode=${resp.errcode} errmsg=${resp.errmsg} ($consecutiveFailures/$MAX_CONSECUTIVE_FAILURES)")

                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        Log.e(TAG, "$MAX_CONSECUTIVE_FAILURES consecutive failures, backing off ${BACKOFF_DELAY_MS}ms")
                        consecutiveFailures = 0
                        delay(BACKOFF_DELAY_MS)
                    } else {
                        delay(RETRY_DELAY_MS)
                    }
                    continue
                }

                consecutiveFailures = 0

                // Save new sync buf
                if (!resp.getUpdatesBuf.isNullOrBlank()) {
                    WeixinAccountStore.saveSyncBuf(resp.getUpdatesBuf)
                    getUpdatesBuf = resp.getUpdatesBuf
                }

                // Process messages
                val msgs = resp.msgs ?: emptyList()
                for (msg in msgs) {
                    // Skip non-user messages:
                    // - BOT(2): bot's own messages echoed by server
                    // - NONE(0): unknown type, possibly bot echo
                    // - null: missing field, possibly bot echo
                    val msgType = msg.messageType
                    if (msgType == MessageType.BOT || msgType == MessageType.NONE || msgType == null) {
                        Log.d(TAG, "⏭️ Skipping non-user message (type=$msgType)")
                        continue
                    }

                    // Skip messages without fromUserId
                    val fromUser = msg.fromUserId ?: continue

                    // Skip if this is a bot-sent message echoed back by server
                    if (WeixinApi.isSentClientId(msg.clientId)) {
                        Log.d(TAG, "⏭️ Skipping bot echo (clientId=${msg.clientId})")
                        continue
                    }

                    // Deduplicate by message ID (getUpdates may return same message across polls)
                    val msgId = msg.messageId
                    if (msgId != null) {
                        synchronized(seenIdsLock) {
                            if (!seenMessageIds.add(msgId)) {
                                Log.d(TAG, "⏭️ Duplicate message ID=$msgId, skipping")
                                continue
                            }
                            // Evict oldest entries when exceeding max size
                            while (seenMessageIds.size > SEEN_IDS_MAX_SIZE) {
                                val iterator = seenMessageIds.iterator()
                                if (iterator.hasNext()) {
                                    iterator.next()
                                    iterator.remove()
                                } else break
                            }
                        }
                    }

                    Log.i(TAG, "Inbound message from=$fromUser types=${msg.itemList?.map { it.type }?.joinToString(",") ?: "none"}")

                    val inbound = parseInbound(msg, accountId)
                    if (inbound.body.isNotBlank() || inbound.hasMedia) {
                        _messageFlow.emit(inbound)
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!running) return

                consecutiveFailures++
                Log.e(TAG, "getUpdates exception ($consecutiveFailures/$MAX_CONSECUTIVE_FAILURES): ${e.message}")

                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.e(TAG, "Backing off ${BACKOFF_DELAY_MS}ms after $MAX_CONSECUTIVE_FAILURES failures")
                    consecutiveFailures = 0
                    delay(BACKOFF_DELAY_MS)
                } else {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        Log.i(TAG, "Poll loop ended")
    }
}
