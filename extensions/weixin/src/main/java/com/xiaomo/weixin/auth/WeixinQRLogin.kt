/**
 * OpenClaw Source Reference:
 * - @tencent-weixin/openclaw-weixin/src/auth/login-qr.ts
 *
 * QR code login flow for Weixin channel.
 */
package com.xiaomo.weixin.auth

import android.util.Log
import com.xiaomo.weixin.api.WeixinApi
import com.xiaomo.weixin.storage.WeixinAccountData
import com.xiaomo.weixin.storage.WeixinAccountStore
import kotlinx.coroutines.delay

data class QRLoginResult(
    val connected: Boolean,
    val botToken: String? = null,
    val accountId: String? = null,
    val baseUrl: String? = null,
    val userId: String? = null,
    val message: String,
)

/**
 * QR login manager. Emits status updates via callback.
 */
class WeixinQRLogin(
    private val apiBaseUrl: String,
    private val routeTag: String? = null,
) {
    companion object {
        private const val TAG = "WeixinQRLogin"
        private const val BOT_TYPE = "3"
        private const val MAX_QR_REFRESH = 3
        private const val DEFAULT_TIMEOUT_MS = 480_000L // 8 minutes
    }

    private val api = WeixinApi(baseUrl = apiBaseUrl, routeTag = routeTag)

    /**
     * Step 1: Fetch QR code URL for display.
     * Returns the URL to render as QR code image + internal qrcode string for polling.
     */
    suspend fun fetchQRCode(): Pair<String, String>? {
        return try {
            val resp = api.fetchQRCode(BOT_TYPE)
            val qrcodeUrl = resp.qrcodeImgContent
            val qrcode = resp.qrcode
            if (qrcodeUrl.isNullOrBlank() || qrcode.isNullOrBlank()) {
                Log.e(TAG, "QR code response missing data")
                null
            } else {
                Log.i(TAG, "QR code fetched: url=${qrcodeUrl.take(50)}...")
                Pair(qrcodeUrl, qrcode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch QR code", e)
            null
        }
    }

    /**
     * Step 2: Poll QR status until confirmed, expired, or timeout.
     *
     * @param qrcode The qrcode string from fetchQRCode
     * @param onStatusUpdate Callback for UI updates: "wait", "scaned", "expired", "confirmed"
     * @param onQRRefreshed Callback when QR is refreshed (returns new qrcodeUrl)
     */
    suspend fun waitForLogin(
        qrcode: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onStatusUpdate: ((String) -> Unit)? = null,
        onQRRefreshed: ((String) -> Unit)? = null,
    ): QRLoginResult {
        var currentQrcode = qrcode
        var qrRefreshCount = 1
        val deadline = System.currentTimeMillis() + timeoutMs
        var scannedNotified = false

        while (System.currentTimeMillis() < deadline) {
            try {
                val status = api.pollQRStatus(currentQrcode)
                Log.i(TAG, "QR status: ${status.status}, botToken=${status.botToken?.take(20)}, botId=${status.ilinkBotId}")

                when (status.status) {
                    "wait" -> {
                        onStatusUpdate?.invoke("wait")
                    }

                    "scaned" -> {
                        if (!scannedNotified) {
                            Log.i(TAG, "QR scanned, waiting for confirmation...")
                            onStatusUpdate?.invoke("scaned")
                            scannedNotified = true
                        }
                    }

                    "expired" -> {
                        qrRefreshCount++
                        if (qrRefreshCount > MAX_QR_REFRESH) {
                            Log.w(TAG, "QR expired $MAX_QR_REFRESH times, giving up")
                            return QRLoginResult(
                                connected = false,
                                message = "二维码多次过期，请重新开始登录。"
                            )
                        }

                        Log.i(TAG, "QR expired, refreshing ($qrRefreshCount/$MAX_QR_REFRESH)...")
                        onStatusUpdate?.invoke("expired")

                        val newQR = fetchQRCode()
                        if (newQR == null) {
                            return QRLoginResult(
                                connected = false,
                                message = "刷新二维码失败。"
                            )
                        }
                        currentQrcode = newQR.second
                        scannedNotified = false
                        onQRRefreshed?.invoke(newQR.first)
                    }

                    "confirmed" -> {
                        if (status.ilinkBotId.isNullOrBlank()) {
                            Log.e(TAG, "Confirmed but no ilink_bot_id")
                            return QRLoginResult(
                                connected = false,
                                message = "登录失败：服务器未返回 Bot ID。"
                            )
                        }

                        val accountId = normalizeAccountId(status.ilinkBotId)
                        Log.i(TAG, "✅ Login confirmed! accountId=$accountId")

                        // Save account data
                        val accountData = WeixinAccountData(
                            token = status.botToken,
                            baseUrl = status.baseurl,
                            userId = status.ilinkUserId,
                            accountId = accountId,
                            savedAt = java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                java.util.Locale.US
                            ).format(java.util.Date()),
                        )
                        WeixinAccountStore.saveAccount(accountData)

                        onStatusUpdate?.invoke("confirmed")

                        return QRLoginResult(
                            connected = true,
                            botToken = status.botToken,
                            accountId = accountId,
                            baseUrl = status.baseurl,
                            userId = status.ilinkUserId,
                            message = "✅ 与微信连接成功！"
                        )
                    }
                    else -> {
                        Log.w(TAG, "Unknown QR status: '${status.status}'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling QR status", e)
                return QRLoginResult(
                    connected = false,
                    message = "登录失败: ${e.message}"
                )
            }

            delay(1000)
        }

        return QRLoginResult(connected = false, message = "登录超时，请重试。")
    }

    /**
     * Normalize account ID: replace special chars with dashes.
     * e.g., "abc@im.bot" → "abc-im-bot"
     */
    private fun normalizeAccountId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9-]"), "-").trimEnd('-')
    }
}
