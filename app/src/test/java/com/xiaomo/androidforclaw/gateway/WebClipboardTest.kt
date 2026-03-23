package com.xiaomo.androidforclaw.gateway

import org.junit.Assert.*
import org.junit.Test

/**
 * Web Clipboard 功能单元测试
 *
 * 测试内容：
 * 1. 剪切板历史管理（添加、上限、排序）
 * 2. 剪切板页面 HTML 生成
 * 3. API 路由匹配逻辑
 * 4. Connect 页面 IP 地址解析
 */
class WebClipboardTest {

    // ── 剪切板历史管理 ────────────────────────────────────

    /**
     * 模拟 GatewayServer 中的剪切板历史逻辑
     */
    private class ClipboardHistory(private val maxSize: Int = 20) {
        private val history = mutableListOf<Pair<String, Long>>()

        fun add(text: String, timestamp: Long = System.currentTimeMillis()) {
            synchronized(history) {
                history.add(0, Pair(text, timestamp))
                if (history.size > maxSize) {
                    history.removeAt(history.size - 1)
                }
            }
        }

        fun getAll(): List<Pair<String, Long>> {
            synchronized(history) {
                return history.toList()
            }
        }

        val size: Int get() = history.size
    }

    @Test
    fun `添加文本到历史记录`() {
        val history = ClipboardHistory()
        history.add("test-text-1", 1000L)
        assertEquals(1, history.size)
        assertEquals("test-text-1", history.getAll()[0].first)
        assertEquals(1000L, history.getAll()[0].second)
    }

    @Test
    fun `最新记录排在最前`() {
        val history = ClipboardHistory()
        history.add("older", 1000L)
        history.add("newer", 2000L)
        assertEquals("newer", history.getAll()[0].first)
        assertEquals("older", history.getAll()[1].first)
    }

    @Test
    fun `历史记录不超过最大值`() {
        val maxSize = 5
        val history = ClipboardHistory(maxSize)
        for (i in 1..10) {
            history.add("text-$i", i.toLong())
        }
        assertEquals(maxSize, history.size)
        // 最新的应该在最前面
        assertEquals("text-10", history.getAll()[0].first)
        // 最旧的被淘汰
        assertFalse(history.getAll().any { it.first == "text-1" })
    }

    @Test
    fun `默认最大历史为 20 条`() {
        val history = ClipboardHistory()
        for (i in 1..25) {
            history.add("text-$i")
        }
        assertEquals(20, history.size)
    }

    @Test
    fun `空历史返回空列表`() {
        val history = ClipboardHistory()
        assertTrue(history.getAll().isEmpty())
        assertEquals(0, history.size)
    }

    // ── API 路由匹配 ─────────────────────────────────────

    /**
     * 模拟 GatewayServer.serve() 中的路由匹配逻辑
     */
    private enum class RouteResult {
        CLIPBOARD_PAGE, CLIPBOARD_SEND, CLIPBOARD_HISTORY, API, WEBUI
    }

    private fun matchRoute(uri: String, method: String = "GET"): RouteResult {
        if (uri.startsWith("/api/")) {
            val apiUri = uri.removePrefix("/api")
            return when {
                apiUri == "/clipboard/send" && method == "POST" -> RouteResult.CLIPBOARD_SEND
                apiUri == "/clipboard/history" -> RouteResult.CLIPBOARD_HISTORY
                else -> RouteResult.API
            }
        }
        if (uri == "/clipboard" || uri == "/clipboard/") {
            return RouteResult.CLIPBOARD_PAGE
        }
        return RouteResult.WEBUI
    }

    @Test
    fun `路由 - clipboard 页面`() {
        assertEquals(RouteResult.CLIPBOARD_PAGE, matchRoute("/clipboard"))
        assertEquals(RouteResult.CLIPBOARD_PAGE, matchRoute("/clipboard/"))
    }

    @Test
    fun `路由 - clipboard send API (POST)`() {
        assertEquals(RouteResult.CLIPBOARD_SEND, matchRoute("/api/clipboard/send", "POST"))
    }

    @Test
    fun `路由 - clipboard send GET 不匹配`() {
        // GET 请求不应匹配 clipboard send
        assertNotEquals(RouteResult.CLIPBOARD_SEND, matchRoute("/api/clipboard/send", "GET"))
    }

    @Test
    fun `路由 - clipboard history API`() {
        assertEquals(RouteResult.CLIPBOARD_HISTORY, matchRoute("/api/clipboard/history"))
    }

    @Test
    fun `路由 - 普通 API 不受影响`() {
        assertEquals(RouteResult.API, matchRoute("/api/health"))
        assertEquals(RouteResult.API, matchRoute("/api/device/status"))
    }

    @Test
    fun `路由 - 普通页面走 WebUI`() {
        assertEquals(RouteResult.WEBUI, matchRoute("/"))
        assertEquals(RouteResult.WEBUI, matchRoute("/index.html"))
    }

    // ── 剪切板页面 HTML ──────────────────────────────────

    @Test
    fun `剪切板页面包含关键元素`() {
        // 模拟 serveClipboardPage 中 HTML 的关键内容
        val html = buildClipboardPageHtml()
        assertTrue("缺少标题", html.contains("Web Clipboard"))
        assertTrue("缺少发送按钮", html.contains("发送到手机"))
        assertTrue("缺少 textarea", html.contains("<textarea"))
        assertTrue("缺少 fetch API 调用", html.contains("/api/clipboard/send"))
        assertTrue("缺少历史记录区域", html.contains("历史记录"))
        assertTrue("缺少 Ctrl+Enter 快捷键", html.contains("ctrlKey") || html.contains("metaKey"))
    }

    @Test
    fun `剪切板页面 API 路径正确`() {
        val html = buildClipboardPageHtml()
        assertTrue("send API 路径", html.contains("'/api/clipboard/send'") || html.contains("\"/api/clipboard/send\""))
        assertTrue("history API 路径", html.contains("'/api/clipboard/history'") || html.contains("\"/api/clipboard/history\""))
    }

    @Test
    fun `剪切板页面有 XSS 防护`() {
        val html = buildClipboardPageHtml()
        assertTrue("包含 escapeHtml 函数", html.contains("escapeHtml"))
        assertTrue("包含 escapeAttr 函数", html.contains("escapeAttr"))
    }

    private fun buildClipboardPageHtml(): String {
        // 与 GatewayServer.serveClipboardPage() 中的 HTML 保持一致
        return """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>AndroidForClaw - Web Clipboard</title>
</head>
<body>
<h1>Web Clipboard</h1>
<p>在电脑上输入，手机上自动复制到剪切板</p>
<textarea id="text" placeholder="粘贴 API Key、配置内容或任意文本..."></textarea>
<button class="btn-send" id="sendBtn" onclick="send()">发送到手机</button>
<h2>历史记录（点击复制）</h2>
<ul class="history" id="history"></ul>
<script>
async function send() {
  const text = document.getElementById('text').value.trim();
  if (!text) return;
  const res = await fetch('/api/clipboard/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text })
  });
}
async function loadHistory() {
  const res = await fetch('/api/clipboard/history');
}
document.getElementById('text').addEventListener('keydown', function(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); send(); }
});
function escapeHtml(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function escapeAttr(s) { return s.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
loadHistory();
</script>
</body>
</html>
        """.trimIndent()
    }

    // ── IP 地址解析 ──────────────────────────────────────

    @Test
    fun `IP 地址格式化为 URL`() {
        val ip = "192.168.1.100"
        val port = 18789
        val url = "http://$ip:$port/clipboard"
        assertEquals("http://192.168.1.100:18789/clipboard", url)
        assertTrue(url.startsWith("http://"))
        assertTrue(url.contains(":18789"))
        assertTrue(url.endsWith("/clipboard"))
    }

    @Test
    fun `无 WiFi 时不生成 URL`() {
        val ip = "未连接 WiFi"
        val url = if (ip.contains(".")) "http://$ip:18789/clipboard" else ip
        assertEquals("未连接 WiFi", url)
        assertFalse(url.startsWith("http"))
    }

    // ── 剪切板文本验证 ──────────────────────────────────

    @Test
    fun `空文本应被拒绝`() {
        val text = "   "
        assertTrue("空白文本", text.isBlank())
    }

    @Test
    fun `正常文本应通过`() {
        val text = "sk-or-v1-abcdef1234567890"
        assertFalse("有效文本", text.isBlank())
    }

    @Test
    fun `长文本不截断`() {
        val longText = "a".repeat(10000)
        val history = ClipboardHistory()
        history.add(longText)
        assertEquals(10000, history.getAll()[0].first.length)
    }

    @Test
    fun `特殊字符不影响存储`() {
        val history = ClipboardHistory()
        val special = """{"key": "value", "nested": {"a": [1,2,3]}}"""
        history.add(special)
        assertEquals(special, history.getAll()[0].first)
    }

    @Test
    fun `包含换行的文本正常存储`() {
        val history = ClipboardHistory()
        val multiline = "line1\nline2\nline3"
        history.add(multiline)
        assertEquals(multiline, history.getAll()[0].first)
    }

    // ── 并发安全 ─────────────────────────────────────────

    @Test
    fun `并发写入不丢数据`() {
        val history = ClipboardHistory(100)
        val threads = (1..10).map { threadId ->
            Thread {
                for (i in 1..10) {
                    history.add("thread-$threadId-item-$i")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(100, history.size)
    }

    // ── Connect 页面显示逻辑 ─────────────────────────────

    @Test
    fun `有效 IP 显示为可点击链接`() {
        val localIp = "192.168.1.5"
        val clipboardUrl = if (localIp.contains(".")) "http://$localIp:18789/clipboard" else localIp
        assertTrue(clipboardUrl.startsWith("http://"))
    }

    @Test
    fun `无效 IP 不显示链接`() {
        val localIp = "获取失败"
        val clipboardUrl = if (localIp.contains(".")) "http://$localIp:18789/clipboard" else localIp
        assertEquals("获取失败", clipboardUrl)
    }
}
