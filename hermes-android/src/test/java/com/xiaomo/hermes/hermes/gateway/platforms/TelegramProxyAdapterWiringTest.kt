package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-010 (2026-06-14): 守住 [com.xiaomo.hermes.hermes.gateway.platforms.Telegram] adapter
 * 已加 proxy 解析路径——`config.extra("proxy_url")` + env fallback +
 * `OkHttpClient.Builder().proxy(...)` 注入。
 *
 * 对应 TC-GW-010-c（详见 docs/hermes-test-cases.md）。
 *
 * URL 解析行为单测见 [TelegramProxyParseTest]，本类只守源码 wiring。
 */
class TelegramProxyAdapterWiringTest {

    private val source: String by lazy { File(telegramPath()).readText() }

    /** TC-GW-010-c: Telegram.kt 含 proxy 解析 + OkHttp .proxy() 注入路径。 */
    @Test
    fun `TC-GW-010-c Telegram adapter resolves proxy from config and env`() {
        // config.extra 读 "proxy_url"
        assertTrue(
            "Telegram.kt 必须含 `\"proxy_url\"` 字面值 —— 从 config.extra 读用户在 app 配的代理 URL；" +
                "字段名严格对齐 Python 上游 schema。",
            source.contains("\"proxy_url\"")
        )
        // env fallback 链：4 个 env 变量名都要出现（即便顺序不同）
        listOf("TELEGRAM_PROXY", "HTTPS_PROXY", "HTTP_PROXY", "ALL_PROXY").forEach { envName ->
            assertTrue(
                "Telegram.kt 必须含 env 变量名字面值 `\"$envName\"` —— " +
                    "对齐 Python 上游 `gateway/platforms/base.py:151-170` 的 fallback 链。",
                source.contains("\"$envName\"")
            )
        }
        // java.net.Proxy 引用（OkHttp .proxy() 接受的类型）
        assertTrue(
            "Telegram.kt 必须含 `java.net.Proxy` 或 `Proxy.Type.SOCKS` / `Proxy.Type.HTTP` 引用 —— " +
                "解析后的 proxy URL 必须实际构造成 java.net.Proxy 实例并喂给 OkHttp。",
            source.contains("java.net.Proxy") ||
                source.contains("Proxy.Type.SOCKS") ||
                source.contains("Proxy.Type.HTTP")
        )
        // OkHttpClient.Builder().proxy(...) 调用路径——证明 proxy 真接入 wire
        assertTrue(
            "Telegram.kt 必须含 `.proxy(` 调用 —— OkHttpClient 构造路径必须真的把 proxy 喂给 OkHttp，" +
                "不能只解析了不接入。",
            source.contains(".proxy(")
        )
    }

    private fun telegramPath(): String {
        val candidates = listOf(
            "src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Telegram.kt",
            "hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Telegram.kt",
        )
        return candidates.firstOrNull { File(it).exists() }
            ?: error("Cannot locate Telegram.kt — cwd=${File(".").absolutePath}")
    }
}
