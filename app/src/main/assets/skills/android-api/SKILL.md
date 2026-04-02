---
name: android-api
description: Android 系统 API 直调工具。当用户要求设置闹钟、定时器、剪贴板、手电筒、音量、亮度、电池查询、启动 App、系统设置等操作时触发。替代 UI 自动化，直接调用系统 API 完成操作。
---

# Android API Skill

直接调用 Android 系统 API 操作设备功能，比 UI 自动化更可靠、更高效。

## 用法

单一工具 `android_api`，通过 `action` 参数路由到不同操作。

## 支持的操作

### 闹钟/定时器
- `set_alarm` — 设置闹钟。参数: `hour`(0-23), `minute`(0-59), `message`(标签，可选)
- `set_timer` — 设置倒计时。参数: `seconds`, `message`(标签，可选)

```
android_api(action="set_alarm", hour=7, minute=30, message="起床")
android_api(action="set_timer", seconds=1800, message="煮饭")
```

### 剪贴板
- `get_clipboard` — 读取当前剪贴板内容
- `set_clipboard` — 写入剪贴板。参数: `text`

```
android_api(action="get_clipboard")
android_api(action="set_clipboard", text="Hello World")
```

### 电池/存储
- `get_battery` — 获取电池电量和充电状态
- `get_storage` — 获取内外部存储空间

```
android_api(action="get_battery")
android_api(action="get_storage")
```

### 手电筒
- `flashlight` — 开关手电筒。参数: `on`(true/false)

```
android_api(action="flashlight", on=true)
```

### 音量
- `get_volume` — 获取所有音轨音量
- `set_volume` — 设置音量。参数: `stream`(music/call/ring/notification/alarm/system), `level`(0-100)

```
android_api(action="set_volume", stream="music", level=50)
```

### 亮度
- `set_brightness` — 设置屏幕亮度。参数: `level`(0-255) 或 `auto`(true)

```
android_api(action="set_brightness", level=128)
android_api(action="set_brightness", auto=true)
```

### 启动 App/Activity
- `start_app` — 启动应用。参数: `package`(包名)
- `start_activity` — 启动 Activity。参数: `action`(Intent action), `data?`(URI), `package?`

```
android_api(action="start_app", package="com.android.settings")
android_api(action="start_activity", action="android.intent.action.VIEW", data="https://example.com")
```

### 广播/设置
- `send_broadcast` — 发送广播。参数: `action`, `package?`
- `set_screen_timeout` — 设置屏幕超时。参数: `seconds`
- `open_settings` — 打开系统设置页。参数: `page`(wifi/bluetooth/battery/display/sound/storage/app/all)

```
android_api(action="open_settings", page="wifi")
android_api(action="set_screen_timeout", seconds=300)
```

## 最佳实践

1. **优先用 API，不走 UI 自动化**：设置闹钟、调音量、查电池等系统操作，直接 API 调用比截图+点按可靠 10 倍
2. **注意权限**：亮度调节需要 WRITE_SETTINGS 权限，首次使用会跳转授权页
3. **Intent fallback**：闹钟/定时器通过 Intent 调起系统时钟，实际交互由系统完成
4. **结合 device 工具**：API 搞不定的操作（如在某个 App 内点击），用 device(action="act") 互补
