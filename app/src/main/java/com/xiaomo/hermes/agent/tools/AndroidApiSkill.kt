package com.xiaomo.hermes.agent.tools

/**
 * Android API Skill — 暴露 Android 系统 API 给 Agent
 *
 * OpenClaw Source Reference:
 * - Android 平台特有，无直接 OpenClaw 对应
 * - 对齐 OpenClaw tool 模式：单一工具 + action 参数路由
 *
 * 支持的操作：
 * - 闹钟/定时器：set_alarm, set_timer
 * - 剪贴板：get_clipboard, set_clipboard
 * - 电池/存储：get_battery, get_storage
 * - 手电筒：flashlight
 * - 音量：get_volume, set_volume
 * - 亮度：set_brightness
 * - 启动 App/Activity：start_app, start_activity
 * - 发广播：send_broadcast
 * - 屏幕超时：set_screen_timeout
 * - 设置页跳转：open_settings
 */



import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.hardware.camera2.CameraManager
import com.xiaomo.hermes.accessibility.ShizukuManager
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

class AndroidApiSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "AndroidApiSkill"
    }

    override val name = "android_api"
    override val description = """Android 系统 API 工具。直接调用系统 API 操作设备功能，无需 UI 自动化。
支持操作：
- set_alarm: 设置闹钟 (参数: hour, minute, message)
- set_timer: 设置倒计时 (参数: seconds, message)
- get_clipboard: 读取剪贴板
- set_clipboard: 写入剪贴板 (参数: text)
- get_battery: 获取电池状态
- get_storage: 获取存储空间
- flashlight: 开关手电筒 (参数: on=true/false)
- get_volume: 获取音量信息
- set_volume: 设置音量 (参数: stream, level 0-100)
- set_brightness: 设置屏幕亮度 (参数: level 0-255, auto=true/false)
- start_app: 启动 App (参数: package)
- start_activity: 启动 Activity (参数: action, data?, package?)
- send_broadcast: 发送广播 (参数: action, package?)
- set_screen_timeout: 设置屏幕超时 (参数: seconds)
- open_settings: 打开系统设置页 (参数: page)"""

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "操作类型",
                            enum = listOf(
                                "set_alarm", "set_timer",
                                "get_clipboard", "set_clipboard",
                                "get_battery", "get_storage",
                                "flashlight",
                                "get_volume", "set_volume",
                                "set_brightness",
                                "start_app", "start_activity",
                                "send_broadcast",
                                "set_screen_timeout",
                                "open_settings"
                            )
                        ),
                        "hour" to PropertySchema(type = "number", description = "小时 (0-23) for set_alarm"),
                        "minute" to PropertySchema(type = "number", description = "分钟 (0-59) for set_alarm"),
                        "seconds" to PropertySchema(type = "number", description = "秒数 for set_timer / set_screen_timeout"),
                        "message" to PropertySchema(type = "string", description = "闹钟/定时器标签"),
                        "text" to PropertySchema(type = "string", description = "剪贴板文本 for set_clipboard"),
                        "on" to PropertySchema(type = "boolean", description = "开关 for flashlight"),
                        "stream" to PropertySchema(type = "string", description = "音量类型: music/call/ring/notification/alarm/system", enum = listOf("music", "call", "ring", "notification", "alarm", "system")),
                        "level" to PropertySchema(type = "number", description = "音量级别 for set_volume (0-100) 或亮度级别 for set_brightness (0-255)"),
                        "auto" to PropertySchema(type = "boolean", description = "亮度自动调节 for set_brightness"),
                        "package" to PropertySchema(type = "string", description = "包名 for start_app / start_activity / send_broadcast"),
                        "action" to PropertySchema(type = "string", description = "Intent action for start_activity / send_broadcast"),
                        "data" to PropertySchema(type = "string", description = "Intent data URI for start_activity"),
                        "page" to PropertySchema(type = "string", description = "设置页面: wifi/bluetooth/battery/display/sound/storage/app/all", enum = listOf("wifi", "bluetooth", "battery", "display", "sound", "storage", "app", "all"))
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val action = args["action"] as? String ?: return SkillResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "set_alarm" -> setAlarm(args)
                "set_timer" -> setTimer(args)
                "get_clipboard" -> getClipboard()
                "set_clipboard" -> setClipboard(args)
                "get_battery" -> getBattery()
                "get_storage" -> getStorage()
                "flashlight" -> toggleFlashlight(args)
                "get_volume" -> getVolume()
                "set_volume" -> setVolume(args)
                "set_brightness" -> setBrightness(args)
                "start_app" -> startApp(args)
                "start_activity" -> startActivity(args)
                "send_broadcast" -> sendBroadcast(args)
                "set_screen_timeout" -> setScreenTimeout(args)
                "open_settings" -> openSettings(args)
                else -> SkillResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "android_api.$action failed", e)
            SkillResult.error("操作失败: ${e.message}")
        }
    }

    // ========== 闹钟/定时器 ==========

    private fun setAlarm(args: Map<String, Any?>): SkillResult {
        val hour = (args["hour"] as? Number)?.toInt() ?: return SkillResult.error("Missing 'hour'")
        val minute = (args["minute"] as? Number)?.toInt() ?: return SkillResult.error("Missing 'minute'")
        val message = args["message"] as? String ?: "AndroidClaw 闹钟"

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法设置闹钟")

        val extras = listOf(
            "--ei android.intent.extra.alarm.HOUR $hour",
            "--ei android.intent.extra.alarm.MINUTES $minute",
            "--es android.intent.extra.alarm.MESSAGE \"$message\""
        )
        val (output, code) = ShizukuManager.startActivityViaShell("android.intent.action.SET_ALARM", extras)
        return if (code == 0) {
            SkillResult.success("已设置闹钟: ${hour}时${minute}分 - $message")
        } else {
            SkillResult.error("设置闹钟失败: $output")
        }
    }

    private fun setTimer(args: Map<String, Any?>): SkillResult {
        val seconds = (args["seconds"] as? Number)?.toInt() ?: return SkillResult.error("Missing 'seconds'")
        val message = args["message"] as? String ?: "AndroidClaw 定时器"

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法设置定时器")

        val extras = listOf(
            "--ei android.intent.extra.alarm.LENGTH $seconds",
            "--es android.intent.extra.alarm.MESSAGE \"$message\"",
            "--ez android.intent.extra.alarm.SKIP_UI true"
        )
        val (output, code) = ShizukuManager.startActivityViaShell("android.intent.action.SET_TIMER", extras)
        return if (code == 0) {
            SkillResult.success("已设置定时器: ${seconds}秒 - $message")
        } else {
            SkillResult.error("设置定时器失败: $output")
        }
    }

    // ========== 剪贴板 ==========

    private fun getClipboard(): SkillResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return SkillResult.success("剪贴板为空")
        }
        val text = clip.getItemAt(0).text?.toString() ?: ""
        return SkillResult.success("剪贴板内容: $text")
    }

    private fun setClipboard(args: Map<String, Any?>): SkillResult {
        val text = args["text"] as? String ?: return SkillResult.error("Missing 'text'")
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AndroidClaw", text))
        return SkillResult.success("已复制到剪贴板: ${text.take(50)}${if (text.length > 50) "..." else ""}")
    }

    // ========== 电池 ==========

    private fun getBattery(): SkillResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)

        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            else -> "未知"
        }

        val chargingInfo = if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            " (电流: ${currentNow / 1000}mA, 平均: ${currentAvg / 1000}mA)"
        } else ""

        return SkillResult.success("电池: ${level}% | 状态: $statusStr$chargingInfo")
    }

    // ========== 存储 ==========

    private fun getStorage(): SkillResult {
        val internal = StatFs(Environment.getDataDirectory().path)
        val internalTotal = internal.totalBytes
        val internalFree = internal.availableBytes

        val result = StringBuilder()
        result.appendLine("内部存储:")
        result.appendLine("  总计: ${formatBytes(internalTotal)}")
        result.appendLine("  可用: ${formatBytes(internalFree)}")
        result.appendLine("  已用: ${formatBytes(internalTotal - internalFree)} (${(internalTotal - internalFree) * 100 / internalTotal}%)")

        // External storage if available
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val external = StatFs(Environment.getExternalStorageDirectory().path)
            result.appendLine("外部存储:")
            result.appendLine("  总计: ${formatBytes(external.totalBytes)}")
            result.appendLine("  可用: ${formatBytes(external.availableBytes)}")
        }

        return SkillResult.success(result.toString().trim())
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824} GB"
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    // ========== 手电筒 ==========

    private fun toggleFlashlight(args: Map<String, Any?>): SkillResult {
        val on = args["on"] as? Boolean ?: return SkillResult.error("Missing 'on' parameter (true/false)")
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cm.cameraIdList.firstOrNull() ?: return SkillResult.error("无可用摄像头")
            cm.setTorchMode(cameraId, on)
            return SkillResult.success(if (on) "手电筒已开启" else "手电筒已关闭")
        } catch (e: Exception) {
            return SkillResult.error("手电筒操作失败: ${e.message}")
        }
    }

    // ========== 音量 ==========

    private fun getVolume(): SkillResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streams = listOf(
            "music" to AudioManager.STREAM_MUSIC,
            "call" to AudioManager.STREAM_VOICE_CALL,
            "ring" to AudioManager.STREAM_RING,
            "notification" to AudioManager.STREAM_NOTIFICATION,
            "alarm" to AudioManager.STREAM_ALARM,
            "system" to AudioManager.STREAM_SYSTEM
        )
        val result = StringBuilder("音量信息:\n")
        for ((name, stream) in streams) {
            val current = am.getStreamVolume(stream)
            val max = am.getStreamMaxVolume(stream)
            val percent = if (max > 0) current * 100 / max else 0
            val mute = if (am.isStreamMute(stream)) " [静音]" else ""
            result.appendLine("  $name: $current/$max (${percent}%)$mute")
        }
        return SkillResult.success(result.toString().trim())
    }

    private fun setVolume(args: Map<String, Any?>): SkillResult {
        val streamName = args["stream"] as? String ?: "music"
        val level = (args["level"] as? Number)?.toInt() ?: return SkillResult.error("Missing 'level'")

        val stream = when (streamName) {
            "music" -> AudioManager.STREAM_MUSIC
            "call" -> AudioManager.STREAM_VOICE_CALL
            "ring" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "alarm" -> AudioManager.STREAM_ALARM
            "system" -> AudioManager.STREAM_SYSTEM
            else -> return SkillResult.error("Unknown stream: $streamName")
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream)
        val vol = (level * max / 100).coerceIn(0, max)
        am.setStreamVolume(stream, vol, 0)

        return SkillResult.success("已设置 $streamName 音量: $vol/$max (${level}%)")
    }

    // ========== 亮度 ==========

    private fun setBrightness(args: Map<String, Any?>): SkillResult {
        val auto = args["auto"] as? Boolean
        if (auto == true) {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            return SkillResult.success("已切换为自动亮度")
        }

        val level = (args["level"] as? Number)?.toInt()
            ?: return SkillResult.error("Missing 'level' (0-255) or 'auto' (true)")

        // Need to disable auto brightness first
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level.coerceIn(0, 255))

        return SkillResult.success("已设置亮度: $level/255")
    }

    // ========== 启动 App ==========

    private fun startApp(args: Map<String, Any?>): SkillResult {
        val packageName = args["package"] as? String ?: return SkillResult.error("Missing 'package'")

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法启动应用")

        val (output, code) = ShizukuManager.startAppViaShell(packageName)
        return if (code == 0) {
            SkillResult.success("已启动: $packageName")
        } else {
            SkillResult.error("启动应用失败: $output")
        }
    }

    private fun startActivity(args: Map<String, Any?>): SkillResult {
        val action = args["action"] as? String ?: return SkillResult.error("Missing 'action' (Intent action)")
        val data = args["data"] as? String
        val pkg = args["package"] as? String

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法启动 Activity")

        val extras = mutableListOf<String>()
        if (data != null) extras.add("-d \"$data\"")
        val (output, code) = ShizukuManager.startActivityViaShell(action, extras, pkg)
        return if (code == 0) {
            SkillResult.success("已启动 Activity: $action")
        } else {
            SkillResult.error("启动 Activity 失败: $output")
        }
    }

    // ========== 发广播 ==========

    private fun sendBroadcast(args: Map<String, Any?>): SkillResult {
        val action = args["action"] as? String ?: return SkillResult.error("Missing 'action'")
        val pkg = args["package"] as? String

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法发送广播")

        val cmd = buildString {
            append("am broadcast -a $action --user 0")
            if (pkg != null) append(" -p $pkg")
        }
        val (output, code) = ShizukuManager.exec(cmd)
        return if (code == 0) {
            SkillResult.success("已发送广播: $action")
        } else {
            SkillResult.error("发送广播失败: $output")
        }
    }

    // ========== 屏幕超时 ==========

    private fun setScreenTimeout(args: Map<String, Any?>): SkillResult {
        val seconds = (args["seconds"] as? Number)?.toInt() ?: return SkillResult.error("Missing 'seconds'")

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法设置屏幕超时")

        // 通过 Shizuku 写入系统设置（无需 WRITE_SETTINGS 权限）
        val (_, code) = ShizukuManager.exec("settings put system screen_off_timeout ${seconds * 1000}")
        return if (code == 0) {
            SkillResult.success("已设置屏幕超时: ${seconds}秒")
        } else {
            SkillResult.error("设置屏幕超时失败")
        }
    }

    // ========== 设置页跳转 ==========

    private fun openSettings(args: Map<String, Any?>): SkillResult {
        val page = args["page"] as? String ?: "all"
        val intentAction = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> "android.intent.action.POWER_USAGE_SUMMARY"
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "app" -> Settings.ACTION_APPLICATION_SETTINGS
            "all" -> Settings.ACTION_SETTINGS
            else -> return SkillResult.error("Unknown settings page: $page")
        }

        if (!ShizukuManager.isReady) return SkillResult.error("Shizuku 未就绪，无法打开设置页")

        val (output, code) = ShizukuManager.startActivityViaShell(intentAction)
        return if (code == 0) {
            SkillResult.success("已打开设置页面: $page")
        } else {
            SkillResult.error("打开设置页失败: $output")
        }
    }
}
