/**
 * OpenClaw Source Reference:
 * - @tencent-weixin/openclaw-weixin/src/api/api.ts
 *
 * HTTP client for Weixin iLink Bot API.
 */
package com.xiaomo.weixin.api

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.random.Random
import android.util.Base64
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class WeixinApi(
    private val baseUrl: String,
    private val token: String? = null,
    private val routeTag: String? = null,
) {
    companion object {
        private const val TAG = "WeixinApi"
        private const val CHANNEL_VERSION = "1.0.3"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_LONG_POLL_TIMEOUT_MS = 35_000L
        private const val DEFAULT_API_TIMEOUT_MS = 15_000L
        private const val SENT_CLIENT_IDS_MAX_SIZE = 200

        /** 追踪 bot 发出的 clientId，用于过滤服务端 echo 回来的消息 */
        private val sentClientIds = java.util.Collections.newSetFromMap(
            java.util.LinkedHashMap<String, Boolean>()
        )
        private val sentClientIdsLock = Object()

        fun trackClientId(clientId: String) {
            synchronized(sentClientIdsLock) {
                sentClientIds.add(clientId)
                // Evict oldest
                while (sentClientIds.size > SENT_CLIENT_IDS_MAX_SIZE) {
                    val iter = sentClientIds.iterator()
                    if (iter.hasNext()) { iter.next(); iter.remove() } else break
                }
            }
        }

        fun isSentClientId(clientId: String?): Boolean {
            if (clientId == null) return false
            synchronized(sentClientIdsLock) {
                return sentClientIds.contains(clientId)
            }
        }

        /** 创建独立 Dispatcher，避免 shutdown 影响全局共享 executor */
        private fun newIsolatedDispatcher(name: String): Dispatcher {
            val counter = AtomicInteger(0)
            val executor = ThreadPoolExecutor(
                0, Int.MAX_VALUE, 60, TimeUnit.SECONDS,
                SynchronousQueue(),
                ThreadFactory { r ->
                    Thread(r, "WeixinApi-$name-${counter.getAndIncrement()}").apply {
                        isDaemon = true
                    }
                }
            )
            return Dispatcher(executor)
        }
    }

    private val gson = Gson()

    // 每个 client 使用独立的 Dispatcher，shutdown 时不会影响全局 OkHttp
    private val longPollDispatcher = newIsolatedDispatcher("longpoll")
    private val apiDispatcher = newIsolatedDispatcher("api")

    private val longPollClient = OkHttpClient.Builder()
        .dispatcher(longPollDispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)  // > server long-poll timeout
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val apiClient = OkHttpClient.Builder()
        .dispatcher(apiDispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun buildBaseInfo(): BaseInfo = BaseInfo(channelVersion = CHANNEL_VERSION)

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    /** X-WECHAT-UIN: random uint32 → decimal string → base64 */
    private fun randomWechatUin(): String {
        val uint32 = Random.nextInt().toUInt().toString()
        return Base64.encodeToString(uint32.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun buildHeaders(): Headers {
        val builder = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("AuthorizationType", "ilink_bot_token")
            .add("X-WECHAT-UIN", randomWechatUin())

        if (!token.isNullOrBlank()) {
            builder.add("Authorization", "Bearer ${token.trim()}")
        }
        if (!routeTag.isNullOrBlank()) {
            builder.add("SKRouteTag", routeTag.trim())
        }
        return builder.build()
    }

    /** 高频轮询接口，不打 request/response 日志以减少 logcat 噪音 */
    private val QUIET_LABELS = setOf("getUpdates")

    private suspend fun post(
        client: OkHttpClient,
        endpoint: String,
        body: String,
        label: String,
    ): String = suspendCancellableCoroutine { cont ->
        val url = "${ensureTrailingSlash(baseUrl)}$endpoint"
        val quiet = label in QUIET_LABELS
        if (!quiet) Log.d(TAG, "POST $url [$label]")

        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "$label failed: ${response.code} $responseBody")
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IOException("$label ${response.code}: $responseBody")
                        )
                    }
                } else {
                    if (!quiet) Log.d(TAG, "$label OK: ${responseBody.take(200)}")
                    if (cont.isActive) cont.resume(responseBody)
                }
            }
        })
    }

    // ── getUpdates (long-poll) ──────────────────────────────────────────────

    suspend fun getUpdates(getUpdatesBuf: String): GetUpdatesResponse {
        val req = GetUpdatesRequest(
            getUpdatesBuf = getUpdatesBuf,
            baseInfo = buildBaseInfo(),
        )
        return try {
            val raw = post(longPollClient, "ilink/bot/getupdates", gson.toJson(req), "getUpdates")
            gson.fromJson(raw, GetUpdatesResponse::class.java)
        } catch (e: IOException) {
            // Timeout = normal for long-poll, return empty
            if (e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("cancel", ignoreCase = true) == true
            ) {
                Log.d(TAG, "getUpdates: client timeout, returning empty")
                GetUpdatesResponse(ret = 0, msgs = emptyList(), getUpdatesBuf = getUpdatesBuf)
            } else {
                throw e
            }
        }
    }

    // ── sendMessage ─────────────────────────────────────────────────────────

    suspend fun sendMessage(msg: WeixinMessage) {
        val req = SendMessageRequest(msg = msg, baseInfo = buildBaseInfo())
        val body = gson.toJson(req)
        Log.d(TAG, "sendMessage request: ${body.take(500)}")
        val resp = post(apiClient, "ilink/bot/sendmessage", body, "sendMessage")
        Log.d(TAG, "sendMessage response: ${resp.take(500)}")
    }

    /** Send a text message to a user. */
    suspend fun sendText(toUserId: String, text: String, contextToken: String?) {
        val clientId = generateClientId()
        trackClientId(clientId)
        val msg = WeixinMessage(
            fromUserId = "",
            toUserId = toUserId,
            clientId = clientId,
            contextToken = contextToken,
            messageType = MessageType.BOT,
            messageState = MessageState.FINISH,
            itemList = listOf(
                MessageItem(
                    type = MessageItemType.TEXT,
                    textItem = TextItem(text = text),
                )
            )
        )
        sendMessage(msg)
    }

    /** Generate a unique client ID for outbound messages (aligned with OpenClaw). */
    private fun generateClientId(): String {
        val rand = Random.nextLong().toULong().toString(36)
        return "openclaw-weixin-$rand"
    }

    // ── sendTyping ──────────────────────────────────────────────────────────

    suspend fun sendTyping(userId: String, typingTicket: String, status: Int = TypingStatus.TYPING) {
        val req = SendTypingRequest(
            ilinkUserId = userId,
            typingTicket = typingTicket,
            status = status,
            baseInfo = buildBaseInfo(),
        )
        post(apiClient, "ilink/bot/sendtyping", gson.toJson(req), "sendTyping")
    }

    // ── getConfig ───────────────────────────────────────────────────────────

    suspend fun getConfig(userId: String, contextToken: String? = null): GetConfigResponse {
        val req = GetConfigRequest(
            ilinkUserId = userId,
            contextToken = contextToken,
            baseInfo = buildBaseInfo(),
        )
        val raw = post(apiClient, "ilink/bot/getconfig", gson.toJson(req), "getConfig")
        return gson.fromJson(raw, GetConfigResponse::class.java)
    }

    // ── getUploadUrl ────────────────────────────────────────────────────────

    suspend fun getUploadUrl(req: GetUploadUrlRequest): GetUploadUrlResponse {
        val body = req.copy(baseInfo = buildBaseInfo())
        val raw = post(apiClient, "ilink/bot/getuploadurl", gson.toJson(body), "getUploadUrl")
        return gson.fromJson(raw, GetUploadUrlResponse::class.java)
    }

    // ── QR Login (no auth needed) ───────────────────────────────────────────

    suspend fun fetchQRCode(botType: String = "3"): QRCodeResponse {
        val url = "${ensureTrailingSlash(baseUrl)}ilink/bot/get_bot_qrcode?bot_type=$botType"
        Log.i(TAG, "Fetching QR code from: $url")

        return suspendCancellableCoroutine { cont ->
            val requestBuilder = Request.Builder().url(url).get()
            if (!routeTag.isNullOrBlank()) {
                requestBuilder.addHeader("SKRouteTag", routeTag.trim())
            }
            val call = apiClient.newCall(requestBuilder.build())
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException("fetchQRCode ${response.code}: $body")
                            )
                        }
                    } else {
                        if (cont.isActive) {
                            cont.resume(gson.fromJson(body, QRCodeResponse::class.java))
                        }
                    }
                }
            })
        }
    }

    suspend fun pollQRStatus(qrcode: String): QRStatusResponse {
        val url = "${ensureTrailingSlash(baseUrl)}ilink/bot/get_qrcode_status?qrcode=${java.net.URLEncoder.encode(qrcode, "UTF-8")}"
        Log.d(TAG, "Polling QR status...")

        return suspendCancellableCoroutine { cont ->
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .addHeader("iLink-App-ClientVersion", "1")
            if (!routeTag.isNullOrBlank()) {
                requestBuilder.addHeader("SKRouteTag", routeTag.trim())
            }

            val call = longPollClient.newCall(requestBuilder.build())
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Timeout returns "wait" status (normal for long-poll)
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        if (cont.isActive) cont.resume(QRStatusResponse(status = "wait"))
                    } else {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException("pollQRStatus ${response.code}: $body")
                            )
                        }
                    } else {
                        if (cont.isActive) {
                            cont.resume(gson.fromJson(body, QRStatusResponse::class.java))
                        }
                    }
                }
            })
        }
    }

    fun shutdown() {
        // 取消所有排队和进行中的请求
        longPollClient.dispatcher.cancelAll()
        apiClient.dispatcher.cancelAll()
        // 关闭独立的 executor（不会影响全局 OkHttp）
        longPollClient.dispatcher.executorService.shutdown()
        apiClient.dispatcher.executorService.shutdown()
        // 关闭连接池
        longPollClient.connectionPool.evictAll()
        apiClient.connectionPool.evictAll()
    }
}
