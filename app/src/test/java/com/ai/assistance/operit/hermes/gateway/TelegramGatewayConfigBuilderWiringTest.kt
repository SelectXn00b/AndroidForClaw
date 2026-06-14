package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-009 (2026-06-14): 守住 [HermesGatewayConfigBuilder] 已加 `buildTelegram` 分支
 * 并接入主入口 `build()`。
 *
 * 对应 TC-GW-009-b / TC-GW-009-d（详见 docs/hermes-test-cases.md）。
 */
class TelegramGatewayConfigBuilderWiringTest {

    private val source: String by lazy { File(builderPath()).readText() }

    /**
     * TC-GW-009-b: `buildTelegram` 函数声明 + 在 `build()` 主入口被调用 + 函数体读取
     * `PLATFORM_TELEGRAM` / `"token"` / `"allowed_chat_ids"` 三个字面值。
     */
    @Test
    fun `TC-GW-009-b buildTelegram wired into build()`() {
        // 函数声明
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须含 `buildTelegram(` 函数声明 —— " +
                "对齐 buildFeishu / buildWeixin 同级模式。",
            Regex("""(?:private\s+)?(?:suspend\s+)?fun\s+buildTelegram\s*\(""").containsMatchIn(source)
        )
        // 在 build() 主入口被调用
        assertTrue(
            "HermesGatewayConfigBuilder.kt `build()` 主入口必须调 `buildTelegram(` —— " +
                "光声明不接入等于没接，必须挂到 platforms map。",
            source.contains("buildTelegram(")
        )
        // 函数体读取的字面值
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须读 `PLATFORM_TELEGRAM` 常量 —— " +
                "对齐 Feishu/Weixin 风格，避免硬编码 \"telegram\" 字符串。",
            source.contains("PLATFORM_TELEGRAM")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须含 `\"token\"` 字面值或 `SECRET_TELEGRAM_TOKEN` 常量引用 —— " +
                "Telegram bot token 字段名，对齐 PlatformConfig.token 字段。",
            source.contains("\"token\"") || source.contains("SECRET_TELEGRAM_TOKEN")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须含 `\"allowed_chat_ids\"` 字面值或 " +
                "`SECRET_TELEGRAM_ALLOWED_CHAT_IDS` 常量引用 —— Telegram chat 白名单字段名，" +
                "对齐 Python `gateway/run.py` platforms.telegram。",
            source.contains("\"allowed_chat_ids\"") || source.contains("SECRET_TELEGRAM_ALLOWED_CHAT_IDS")
        )
        // PlatformConfig 构造时 platform 字段为 Platform.TELEGRAM
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须含 `Platform.TELEGRAM` 引用 —— " +
                "buildTelegram 返回的 PlatformConfig.platform 必须是 TELEGRAM。",
            source.contains("Platform.TELEGRAM")
        )
    }

    /**
     * TC-GW-009-d (红线守卫): 既有 Feishu / Weixin 接线必须保留。
     */
    @Test
    fun `TC-GW-009-d Feishu and Weixin wiring untouched`() {
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍调 `buildFeishu(` —— R-GW-009 不应误删 Feishu 接线。",
            source.contains("buildFeishu(")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍调 `buildWeixin(` —— R-GW-009 不应误删 Weixin 接线。",
            source.contains("buildWeixin(")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍含 `Platform.FEISHU` 引用。",
            source.contains("Platform.FEISHU")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍含 `Platform.WEIXIN` 引用。",
            source.contains("Platform.WEIXIN")
        )
    }

    private fun builderPath(): String {
        val candidates = listOf(
            "app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayConfigBuilder.kt",
            "src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayConfigBuilder.kt"
        )
        return candidates.firstOrNull { File(it).exists() }
            ?: error("Cannot locate HermesGatewayConfigBuilder.kt — cwd=${File(".").absolutePath}")
    }
}
