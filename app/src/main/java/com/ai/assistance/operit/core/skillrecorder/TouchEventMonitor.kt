package com.ai.assistance.operit.core.skillrecorder

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.core.tools.system.shell.ShellProcess
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 通过 Shizuku shell 运行 `getevent -lt` 捕获真实触摸坐标。
 *
 * getevent -lt 输出格式（示例）：
 * [  12345.678901] /dev/input/event2: EV_ABS       ABS_MT_POSITION_X    000002d0
 * [  12345.678901] /dev/input/event2: EV_ABS       ABS_MT_POSITION_Y    000004b0
 * [  12345.678901] /dev/input/event2: EV_KEY       BTN_TOUCH            DOWN
 * [  12345.679012] /dev/input/event2: EV_KEY       BTN_TOUCH            UP
 *
 * 坐标是触摸屏设备坐标（非显示像素），需要通过设备 max 值缩放到显示像素。
 */
class TouchEventMonitor(private val context: Context) {

    companion object {
        private const val TAG = "TouchEventMonitor"
    }

    data class TouchEvent(
        val x: Int,  // 显示像素坐标
        val y: Int,
        val timestamp: Long
    )

    private var process: ShellProcess? = null
    private var monitorJob: Job? = null
    private var callback: ((TouchEvent) -> Unit)? = null

    // 触摸屏设备坐标范围（从 getevent -il 获取）
    private var deviceMaxX: Int = 0
    private var deviceMaxY: Int = 0
    // 显示尺寸
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0

    // 当前触摸追踪状态
    private var currentX: Int = -1
    private var currentY: Int = -1
    private var touchDown: Boolean = false

    /**
     * 启动触摸事件监听。
     * @param onTouch 触摸抬起时的回调（包含显示像素坐标）
     */
    suspend fun start(onTouch: (TouchEvent) -> Unit) {
        if (monitorJob != null) return
        callback = onTouch

        // 获取显示尺寸
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        AppLogger.d(TAG, "Display size: ${displayWidth}x${displayHeight}")

        // 先查询触摸屏设备的坐标范围
        queryDeviceInfo()

        // 启动 getevent -lt 监听
        startGeteventProcess()
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        process?.destroy()
        process = null
        callback = null
        currentX = -1
        currentY = -1
        touchDown = false
    }

    /**
     * 查询触摸屏设备的 ABS_MT_POSITION_X/Y 最大值，用于坐标缩放。
     * 使用 `getevent -il` 输出设备信息。
     */
    private suspend fun queryDeviceInfo() = withContext(Dispatchers.IO) {
        try {
            val executor = ShellExecutorFactory.getExecutor(context, AndroidPermissionLevel.DEBUGGER)
            val result = executor.executeCommand("getevent -il 2>/dev/null | grep -A 4 'ABS_MT_POSITION'")
            if (result.success && result.stdout.isNotBlank()) {
                parseDeviceMaxValues(result.stdout)
            }

            // Fallback：如果没拿到设备信息，用 getevent -p 方式
            if (deviceMaxX == 0 || deviceMaxY == 0) {
                val result2 = executor.executeCommand("getevent -p 2>/dev/null | grep -B 1 -A 4 'ABS_MT_POSITION'")
                if (result2.success && result2.stdout.isNotBlank()) {
                    parseDeviceMaxValues(result2.stdout)
                }
            }

            // 最终 fallback：假设设备坐标等于显示像素（很多设备是 1:1）
            if (deviceMaxX == 0) deviceMaxX = displayWidth
            if (deviceMaxY == 0) deviceMaxY = displayHeight

            AppLogger.d(TAG, "Device touch range: max_x=$deviceMaxX, max_y=$deviceMaxY")
        } catch (e: Exception) {
            AppLogger.w(TAG, "查询触摸设备信息失败，使用 display 尺寸作为 fallback: ${e.message}")
            deviceMaxX = displayWidth
            deviceMaxY = displayHeight
        }
    }

    /**
     * 从 getevent -il / getevent -p 输出中解析 max 值。
     * 格式示例：
     *   ABS_MT_POSITION_X : value 0, min 0, max 1079, fuzz 0, flat 0, resolution 0
     *   ABS_MT_POSITION_Y : value 0, min 0, max 2339, fuzz 0, flat 0, resolution 0
     */
    private fun parseDeviceMaxValues(output: String) {
        val lines = output.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("ABS_MT_POSITION_X") && trimmed.contains("max")) {
                val maxMatch = Regex("max\\s+(\\d+)").find(trimmed)
                maxMatch?.groupValues?.get(1)?.toIntOrNull()?.let { deviceMaxX = it }
            } else if (trimmed.contains("ABS_MT_POSITION_Y") && trimmed.contains("max")) {
                val maxMatch = Regex("max\\s+(\\d+)").find(trimmed)
                maxMatch?.groupValues?.get(1)?.toIntOrNull()?.let { deviceMaxY = it }
            }
        }
    }

    /**
     * 启动 getevent -lt 进程，持续监听触摸事件。
     */
    private fun startGeteventProcess() {
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val executor = ShellExecutorFactory.getExecutor(context, AndroidPermissionLevel.DEBUGGER)
                process = executor.startProcess("getevent -lt")

                process?.stdout?.onEach { line ->
                    parseGeteventLine(line)
                }?.launchIn(this)

                process?.stderr?.onEach { line ->
                    AppLogger.v(TAG, "getevent stderr: $line")
                }?.launchIn(this)

                process?.waitFor()
                AppLogger.d(TAG, "getevent process exited")
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                AppLogger.e(TAG, "getevent 监听失败", e)
            }
        }
    }

    /**
     * 解析 getevent -lt 的一行输出。
     *
     * 格式：
     * [  timestamp] /dev/input/eventN: TYPE         CODE                 VALUE
     *
     * 示例：
     * [    1234.567890] /dev/input/event2: EV_ABS       ABS_MT_POSITION_X    000002d0
     * [    1234.567890] /dev/input/event2: EV_ABS       ABS_MT_POSITION_Y    000004b0
     * [    1234.567890] /dev/input/event2: EV_KEY       BTN_TOUCH            DOWN
     * [    1234.567890] /dev/input/event2: EV_KEY       BTN_TOUCH            UP
     */
    private fun parseGeteventLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return

        when {
            trimmed.contains("ABS_MT_POSITION_X") -> {
                val hexValue = trimmed.substringAfterLast(" ").trim()
                val rawX = hexValue.toIntOrNull(16) ?: return
                currentX = rawX
            }
            trimmed.contains("ABS_MT_POSITION_Y") -> {
                val hexValue = trimmed.substringAfterLast(" ").trim()
                val rawY = hexValue.toIntOrNull(16) ?: return
                currentY = rawY
            }
            trimmed.contains("BTN_TOUCH") && trimmed.contains("DOWN") -> {
                touchDown = true
            }
            trimmed.contains("BTN_TOUCH") && trimmed.contains("UP") -> {
                if (touchDown && currentX >= 0 && currentY >= 0) {
                    // 设备坐标 → 显示像素坐标
                    val displayX = if (deviceMaxX > 0) (currentX.toLong() * displayWidth / deviceMaxX).toInt() else currentX
                    val displayY = if (deviceMaxY > 0) (currentY.toLong() * displayHeight / deviceMaxY).toInt() else currentY

                    AppLogger.d(TAG, "Touch UP: raw($currentX, $currentY) → display($displayX, $displayY)")

                    callback?.invoke(
                        TouchEvent(
                            x = displayX,
                            y = displayY,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                touchDown = false
                currentX = -1
                currentY = -1
            }
        }
    }
}
