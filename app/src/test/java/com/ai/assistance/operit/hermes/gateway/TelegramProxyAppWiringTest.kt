package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-010 (2026-06-14): 守住 Telegram bot HTTP/SOCKS 代理 wiring 在 app 层（Preferences /
 * ConfigBuilder / CredentialsScreen）的接线。Telegram.kt adapter 侧的接线由 hermes-android 模块的
 * `TelegramProxyAdapterWiringTest` / `TelegramProxyParseTest` 守。
 *
 * 对应 TC-GW-010-a / TC-GW-010-b / TC-GW-010-d / TC-GW-010-h（app 部分，详见 docs/hermes-test-cases.md）。
 */
class TelegramProxyAppWiringTest {

    private val preferencesSource: String by lazy { File(preferencesPath()).readText() }
    private val builderSource: String by lazy { File(builderPath()).readText() }
    private val screenSource: String by lazy { File(screenPath()).readText() }

    /** TC-GW-010-a: Preferences 含 SECRET_TELEGRAM_PROXY_URL = "proxy_url"。 */
    @Test
    fun `TC-GW-010-a proxy_url constant exists`() {
        assertTrue(
            "HermesGatewayPreferences.kt 必须含 `SECRET_TELEGRAM_PROXY_URL = \"proxy_url\"` 常量声明 —— " +
                "对齐 Python 上游 schema 字面值（`platforms.telegram.proxy_url`），不要叫 `proxy`。",
            Regex("""\bSECRET_TELEGRAM_PROXY_URL\s*=\s*"proxy_url"""").containsMatchIn(preferencesSource)
        )
    }

    /** TC-GW-010-b: ConfigBuilder.buildTelegram 把 proxy_url 写入 extra。 */
    @Test
    fun `TC-GW-010-b ConfigBuilder wires proxy_url into extra`() {
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须含 `SECRET_TELEGRAM_PROXY_URL` 引用 —— " +
                "buildTelegram 必须从 prefs 读这个 key。",
            builderSource.contains("SECRET_TELEGRAM_PROXY_URL")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须含 `\"proxy_url\"` 字面值（写入 extra map 时 key 名）—— " +
                "Telegram.kt 侧用 `config.extra(\"proxy_url\", ...)` 读，必须严格一致。",
            builderSource.contains("\"proxy_url\"")
        )
    }

    /** TC-GW-010-d: Telegram UI 把 proxy_url 加进 TELEGRAM_FIELDS。 */
    @Test
    fun `TC-GW-010-d Telegram UI exposes proxy_url field`() {
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 的 TELEGRAM_FIELDS 必须含 `SECRET_TELEGRAM_PROXY_URL` 引用 —— " +
                "用户能在 app 凭证页输入代理 URL。",
            screenSource.contains("SECRET_TELEGRAM_PROXY_URL")
        )
    }

    /** TC-GW-010-h: 红线守卫——R-GW-009 字段 + Feishu/Weixin 接线不被误删。 */
    @Test
    fun `TC-GW-010-h preserves R-GW-009 and Feishu and Weixin wiring`() {
        // R-GW-009 字段还在
        assertTrue(
            "HermesGatewayPreferences.kt 必须仍含 `SECRET_TELEGRAM_TOKEN` —— R-GW-010 不应误删 R-GW-009 字段。",
            preferencesSource.contains("SECRET_TELEGRAM_TOKEN")
        )
        assertTrue(
            "HermesGatewayPreferences.kt 必须仍含 `SECRET_TELEGRAM_ALLOWED_CHAT_IDS`。",
            preferencesSource.contains("SECRET_TELEGRAM_ALLOWED_CHAT_IDS")
        )
        // ConfigBuilder 仍接 Feishu / Weixin
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍调 `buildFeishu(`。",
            builderSource.contains("buildFeishu(")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍调 `buildWeixin(`。",
            builderSource.contains("buildWeixin(")
        )
        assertTrue(
            "HermesGatewayConfigBuilder.kt 必须仍调 `buildTelegram(` —— R-GW-010 不应误删 R-GW-009 接线。",
            builderSource.contains("buildTelegram(")
        )
        // Screen 仍含 Feishu / Weixin / token / allowed_chat_ids
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须仍含 `PLATFORM_FEISHU`。",
            screenSource.contains("PLATFORM_FEISHU")
        )
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须仍含 `PLATFORM_WEIXIN`。",
            screenSource.contains("PLATFORM_WEIXIN")
        )
        assertTrue(
            "HermesGatewayCredentialsScreen.kt TELEGRAM_FIELDS 必须仍含 `SECRET_TELEGRAM_TOKEN`。",
            screenSource.contains("SECRET_TELEGRAM_TOKEN")
        )
        assertTrue(
            "HermesGatewayCredentialsScreen.kt TELEGRAM_FIELDS 必须仍含 `SECRET_TELEGRAM_ALLOWED_CHAT_IDS`。",
            screenSource.contains("SECRET_TELEGRAM_ALLOWED_CHAT_IDS")
        )
    }

    // ----- helpers -----

    private fun preferencesPath(): String = locate(
        "app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayPreferences.kt",
        "src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayPreferences.kt",
    )

    private fun builderPath(): String = locate(
        "app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayConfigBuilder.kt",
        "src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayConfigBuilder.kt",
    )

    private fun screenPath(): String = locate(
        "app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/HermesGatewayCredentialsScreen.kt",
        "src/main/java/com/ai/assistance/operit/ui/features/settings/screens/HermesGatewayCredentialsScreen.kt",
    )

    private fun locate(vararg candidates: String): String =
        candidates.firstOrNull { File(it).exists() }
            ?: error("Cannot locate file. cwd=${File(".").absolutePath} candidates=${candidates.toList()}")
}
