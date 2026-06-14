package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * R-GW-010 (2026-06-14): Telegram bot HTTP/SOCKS 代理 URL 解析行为单测。
 *
 * 测试 Telegram.kt 内的纯函数 helper [parseProxyUrl] / [resolveProxyFromConfigAndEnv]。
 *
 * 对应 TC-GW-010-e / TC-GW-010-f / TC-GW-010-g（详见 docs/hermes-test-cases.md）。
 */
class TelegramProxyParseTest {

    // ── TC-GW-010-e: 合法 URL 解析 ─────────────────────────────────────

    /** TC-GW-010-e: socks5://host:port → Proxy.Type.SOCKS。 */
    @Test
    fun `TC-GW-010-e socks5 url`() {
        val proxy = parseProxyUrl("socks5://127.0.0.1:1080")
        assertNotNull("socks5 URL 必须解析成功", proxy)
        assertEquals(Proxy.Type.SOCKS, proxy!!.type())
        val addr = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", addr.hostString)
        assertEquals(1080, addr.port)
    }

    /** TC-GW-010-e: socks://host:port 也接受（无 5 后缀）。 */
    @Test
    fun `TC-GW-010-e socks url without version`() {
        val proxy = parseProxyUrl("socks://10.0.0.1:1080")
        assertNotNull("socks:// 也应解析为 SOCKS proxy", proxy)
        assertEquals(Proxy.Type.SOCKS, proxy!!.type())
    }

    /** TC-GW-010-e: http://host:port → Proxy.Type.HTTP。 */
    @Test
    fun `TC-GW-010-e http url`() {
        val proxy = parseProxyUrl("http://10.0.2.2:8118")
        assertNotNull("http URL 必须解析成功", proxy)
        assertEquals(Proxy.Type.HTTP, proxy!!.type())
        val addr = proxy.address() as InetSocketAddress
        assertEquals("10.0.2.2", addr.hostString)
        assertEquals(8118, addr.port)
    }

    /** TC-GW-010-e: https://host:port → Proxy.Type.HTTP（OkHttp 没有 HTTPS proxy type）。 */
    @Test
    fun `TC-GW-010-e https url normalized to HTTP type`() {
        val proxy = parseProxyUrl("https://proxy.example.com:443")
        assertNotNull("https URL 必须解析成功", proxy)
        // OkHttp / java.net.Proxy 没有独立的 HTTPS 类型；HTTPS 代理走 HTTP CONNECT，统一用 HTTP type。
        assertEquals(Proxy.Type.HTTP, proxy!!.type())
    }

    // ── TC-GW-010-f: 非法 / 空 / 不支持 scheme ──────────────────────────

    @Test
    fun `TC-GW-010-f empty string`() {
        assertNull(parseProxyUrl(""))
    }

    @Test
    fun `TC-GW-010-f whitespace`() {
        assertNull(parseProxyUrl("   "))
    }

    @Test
    fun `TC-GW-010-f garbage non-url`() {
        // 非法格式 → null（不抛异常）
        val result = parseProxyUrl("not-a-url")
        assertNull("非法 URL 必须返回 null 而不是抛异常", result)
    }

    @Test
    fun `TC-GW-010-f missing port`() {
        // 没端口 → null
        assertNull(parseProxyUrl("socks5://127.0.0.1"))
    }

    @Test
    fun `TC-GW-010-f unsupported scheme`() {
        assertNull(parseProxyUrl("ftp://example.com:21"))
    }

    @Test
    fun `TC-GW-010-f socks5h fallback`() {
        // socks5h:// 表示远端 DNS resolution（仅 SOCKS5 协议层）。OkHttp 原生不支持 rdns。
        // 实现可选：返回 null（拒绝）或回退为 Proxy.Type.SOCKS（本地 DNS）。
        // 测试只要求"不崩溃"，两种行为都可以接受。
        val result = parseProxyUrl("socks5h://127.0.0.1:1080")
        if (result != null) {
            assertEquals(
                "如果 socks5h 被回退为 SOCKS，type 必须是 SOCKS",
                Proxy.Type.SOCKS, result.type()
            )
        }
        // null 也接受——表示拒绝该 scheme
    }

    // ── TC-GW-010-g: env fallback 优先级 ────────────────────────────────

    @Test
    fun `TC-GW-010-g config wins over env`() {
        val resolver = ProxyResolverForTest(
            configUrl = "socks5://127.0.0.1:1080",
            envValues = mapOf("TELEGRAM_PROXY" to "http://shouldnt-be-used:8080"),
        )
        val proxy = resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env)
        assertNotNull(proxy)
        assertEquals(Proxy.Type.SOCKS, proxy!!.type())
        assertEquals(1080, (proxy.address() as InetSocketAddress).port)
    }

    @Test
    fun `TC-GW-010-g empty config falls back to TELEGRAM_PROXY`() {
        val resolver = ProxyResolverForTest(
            configUrl = "",
            envValues = mapOf("TELEGRAM_PROXY" to "http://127.0.0.1:8080"),
        )
        val proxy = resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env)
        assertNotNull(proxy)
        assertEquals(Proxy.Type.HTTP, proxy!!.type())
        assertEquals(8080, (proxy.address() as InetSocketAddress).port)
    }

    @Test
    fun `TC-GW-010-g HTTPS_PROXY used if TELEGRAM_PROXY missing`() {
        val resolver = ProxyResolverForTest(
            configUrl = "",
            envValues = mapOf("HTTPS_PROXY" to "http://127.0.0.1:9999"),
        )
        val proxy = resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env)
        assertNotNull(proxy)
        assertEquals(9999, (proxy!!.address() as InetSocketAddress).port)
    }

    @Test
    fun `TC-GW-010-g HTTP_PROXY used if HTTPS missing`() {
        val resolver = ProxyResolverForTest(
            configUrl = "",
            envValues = mapOf("HTTP_PROXY" to "http://127.0.0.1:7777"),
        )
        val proxy = resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env)
        assertNotNull(proxy)
        assertEquals(7777, (proxy!!.address() as InetSocketAddress).port)
    }

    @Test
    fun `TC-GW-010-g ALL_PROXY used as last resort`() {
        val resolver = ProxyResolverForTest(
            configUrl = "",
            envValues = mapOf("ALL_PROXY" to "socks5://127.0.0.1:1234"),
        )
        val proxy = resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env)
        assertNotNull(proxy)
        assertEquals(Proxy.Type.SOCKS, proxy!!.type())
        assertEquals(1234, (proxy.address() as InetSocketAddress).port)
    }

    @Test
    fun `TC-GW-010-g all empty returns null`() {
        val resolver = ProxyResolverForTest(configUrl = "", envValues = emptyMap())
        assertNull(resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env))
    }

    @Test
    fun `TC-GW-010-g priority TELEGRAM_PROXY over HTTPS over HTTP over ALL`() {
        val all = mapOf(
            "TELEGRAM_PROXY" to "http://1.1.1.1:1111",
            "HTTPS_PROXY" to "http://2.2.2.2:2222",
            "HTTP_PROXY" to "http://3.3.3.3:3333",
            "ALL_PROXY" to "http://4.4.4.4:4444",
        )
        val resolver = ProxyResolverForTest(configUrl = "", envValues = all)
        val proxy = resolveProxyFromConfigAndEnv(resolver.configUrl, resolver::env)
        assertNotNull(proxy)
        assertEquals(1111, (proxy!!.address() as InetSocketAddress).port)
    }

    // ── helper ─────────────────────────────────────────────────────────

    private class ProxyResolverForTest(
        val configUrl: String,
        private val envValues: Map<String, String>,
    ) {
        fun env(name: String): String? = envValues[name]
    }
}
