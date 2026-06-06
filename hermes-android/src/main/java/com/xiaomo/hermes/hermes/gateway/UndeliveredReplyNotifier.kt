package com.xiaomo.hermes.hermes.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pops a local Android notification when a gateway send fails. Tapping the notification
 * copies the agent's reply text to the clipboard so the user can paste it manually into
 * the platform that just failed.
 *
 * **R-GW-003 bugfix (2026-06-06)**: this is the user-facing half of the
 * "Feishu rescue kit". `UndeliveredReplyStore` persists the entry; this notifier shouts.
 *
 * Why not just retry forever?  Because if the platform is genuinely down (Feishu API
 * outage, expired token, banned app credentials), endless retries hammer the failing
 * endpoint and still don't deliver.  Surfacing to the user via a local notification
 * lets them either fix the credential or just manually relay the reply.
 *
 * Uses platform `android.app.Notification` API (minSdk 26) to avoid pulling androidx
 * into the hermes-android module.
 */
class UndeliveredReplyNotifier(private val context: Context) {

    /** Ensure the notification channel exists. Idempotent; safe to call repeatedly. */
    fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "未送达回复 (Undelivered Replies)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Agent 算出回复但飞书/微信等通道发送失败时弹出，点击复制到剪贴板。"
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Pop a notification for one failed delivery. Tap → copy [text] to clipboard.
     *
     * @param platform e.g. "feishu"
     * @param chatId source chat id, shown in the title for context
     * @param text the agent reply that failed to send (full text, copied on tap)
     * @param error short error message, shown in the body preview
     */
    fun notify(platform: String, chatId: String, text: String, error: String) {
        try {
            ensureChannel()
            // Lazily register the copy receiver (once per process)
            ensureReceiverRegistered()

            val notifId = NOTIF_ID_SEQ.incrementAndGet()
            val copyIntent = Intent(ACTION_COPY).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, notifId, copyIntent, flags)

            val title = "[$platform] 回复未送达"
            val body = "点击复制全文 · $error · chat=${chatId.take(24)}"
            val preview = text.take(200)

            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText("$body\n\n$preview"))
                .setAutoCancel(true)
                .setContentIntent(pi)

            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notifId, builder.build())
        } catch (e: Throwable) {
            Log.w(TAG, "notify failed: ${e.message}")
        }
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            try {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action != ACTION_COPY) return
                        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
                        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("undelivered reply", text))
                            Toast.makeText(ctx, "已复制 ${text.length} 字符到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                        if (notifId > 0) {
                            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                .cancel(notifId)
                        }
                    }
                }
                val filter = IntentFilter(ACTION_COPY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(receiver, filter)
                }
                receiverRegistered = true
            } catch (e: Throwable) {
                Log.w(TAG, "registerReceiver failed: ${e.message}")
            }
        }
    }

    @Volatile private var receiverRegistered = false

    companion object {
        private const val TAG = "UndeliveredNotifier"
        const val CHANNEL_ID = "hermes_undelivered_replies"
        private const val ACTION_COPY = "com.xiaomo.hermes.gateway.action.COPY_UNDELIVERED"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_NOTIF_ID = "notif_id"
        private val NOTIF_ID_SEQ = AtomicInteger(10_000)
    }
}
