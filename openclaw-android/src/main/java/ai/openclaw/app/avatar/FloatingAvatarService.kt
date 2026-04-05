package ai.openclaw.app.avatar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingAvatarService : Service() {

    private var windowManager: WindowManager? = null
    private var glView: Live2dGLSurfaceView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        createFloatingWindow()
        observeAvatarState()
        isRunning = true
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        val view = glView
        glView = null
        if (view != null) {
            // Release Live2D native resources on the GL thread first (needs EGL context),
            // then synchronously remove the view. Must be synchronous so a quick
            // stop-then-start doesn't race with a stale view still in the WindowManager.
            val latch = java.util.concurrent.CountDownLatch(1)
            view.releaseModelOnGLThread { latch.countDown() }
            try { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = "floating_avatar"
        val channel = NotificationChannel(
            channelId, "化身", NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentTitle("化身")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Safety: remove any stale view from a previous instance (Android 12 race condition)
        val oldView = glView
        if (oldView != null) {
            try { windowManager?.removeView(oldView) } catch (_: Exception) {}
        }

        val density = resources.displayMetrics.density
        val w = (180 * density).toInt()
        val h = (150 * density).toInt()

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }

        val view = Live2dGLSurfaceView(this)
        glView = view

        // Drag handling
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX - (event.rawX - touchX).toInt()
                    params.y = initY - (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
    }

    private fun observeAvatarState() {
        scope.launch {
            AvatarStateHolder.triggers.collect { trigger ->
                glView?.postOnGLThread { model ->
                    model ?: return@postOnGLThread
                    // Hiyori has no expressions, use motions for all triggers
                    model.startRandomMotion("TapBody", Live2dModel.PRIORITY_NORMAL)
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 9527
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingAvatarService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingAvatarService::class.java))
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }
    }
}
