/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有 — MCP Server 供外部 Agent 调用)
 */
package com.xiaomo.hermes.mcp

import android.util.Log
import com.xiaomo.hermes.accessibility.AccessibilityProxy
import com.xiaomo.hermes.util.PlaywrightStyleViewTree
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Server — 将手机的无障碍操控和截屏能力通过 MCP 协议暴露给外部 Agent。
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  ⚠️  此类是给【外部 Agent】使用的（Claude Desktop、Cursor 等）│
 * │     与 Hermes 自身功能无关。                          │
 * │     Hermes 通过 DeviceTool → AccessibilityProxy      │
 * │     直接调用，不走 MCP。                                      │
 * └──────────────────────────────────────────────────────────────┘
 *
 * Transport: Streamable HTTP (POST /mcp)
 * Protocol: JSON-RPC 2.0 (MCP spec)
 *
 * 暴露的 Tools:
 *   get_view_tree  — 获取 UI 树
 *   screenshot     — 截屏 (base64)
 *   tap            — 点击坐标
 *   long_press     — 长按坐标
 *   swipe          — 滑动手势
 *   input_text     — 输入文字
 *   press_home     — 按 Home 键
 *   press_back     — 按返回键
 *   get_current_app— 获取当前前台应用包名
 */
class ObserverMcpServer private constructor(port: Int) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ObserverMcpServer"
        private const val PROTOCOL_VERSION = "2024-11-05"
        const val DEFAULT_PORT = 8399

        @Volatile
        private var instance: ObserverMcpServer? = null

        fun getInstance(port: Int = DEFAULT_PORT): ObserverMcpServer {
            return instance ?: synchronized(this) {
                instance ?: ObserverMcpServer(port).also { instance = it }
            }
        }

        fun isRunning(): Boolean = instance?.isAlive == true

        fun stopServer() {
            instance?.stop()
            Log.i(TAG, "MCP Server stopped")
        }
    }

    // ── Ref store (last snapshot refs for tap-by-ref) ──────────
    @Volatile
    private var lastSnapshotRefs: Map<String, PlaywrightStyleViewTree.RefEntry> = emptyMap()

    private fun resolveRefCoords(ref: String): Pair<Int, Int>? {
        val entry = lastSnapshotRefs[ref] ?: return null
        return entry.node.point.x to entry.node.point.y
    }

    // ── Tool definitions ────────────────────────────────────────

    private val mcpTools = listOf(
        McpTool(
            name = "get_view_tree",
            description = "Get current screen UI tree in Playwright-style snapshot format. Returns role-based nodes with [ref=eN] identifiers. Use ref values with tap/long_press tools for precise element targeting.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "use_cache" to mapOf("type" to "boolean", "description" to "Use cached tree (default true)"),
                    "interactive_only" to mapOf("type" to "boolean", "description" to "Only return interactive elements like buttons, textboxes, etc. (default false)")
                )
            )
        ),
        McpTool(
            name = "screenshot",
            description = "截取当前屏幕，返回 base64 编码的 PNG 图片",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        ),
        McpTool(
            name = "tap",
            description = "Tap on screen. Use ref from get_view_tree (e.g. ref='e3') OR x,y coordinates.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "ref" to mapOf("type" to "string", "description" to "Element ref from get_view_tree snapshot (e.g. 'e3')"),
                    "x" to mapOf("type" to "integer", "description" to "X coordinate (used if ref not provided)"),
                    "y" to mapOf("type" to "integer", "description" to "Y coordinate (used if ref not provided)")
                )
            )
        ),
        McpTool(
            name = "long_press",
            description = "Long press on screen. Use ref from get_view_tree OR x,y coordinates.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "ref" to mapOf("type" to "string", "description" to "Element ref from get_view_tree snapshot (e.g. 'e3')"),
                    "x" to mapOf("type" to "integer", "description" to "X coordinate"),
                    "y" to mapOf("type" to "integer", "description" to "Y coordinate")
                )
            )
        ),
        McpTool(
            name = "swipe",
            description = "在屏幕上执行滑动手势",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "start_x" to mapOf("type" to "integer", "description" to "起始 X"),
                    "start_y" to mapOf("type" to "integer", "description" to "起始 Y"),
                    "end_x" to mapOf("type" to "integer", "description" to "结束 X"),
                    "end_y" to mapOf("type" to "integer", "description" to "结束 Y"),
                    "duration_ms" to mapOf("type" to "integer", "description" to "滑动时长(ms)，默认 300")
                ),
                "required" to listOf("start_x", "start_y", "end_x", "end_y")
            )
        ),
        McpTool(
            name = "input_text",
            description = "向当前焦点输入框输入文字",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "text" to mapOf("type" to "string", "description" to "要输入的文字")
                ),
                "required" to listOf("text")
            )
        ),
        McpTool(
            name = "press_home",
            description = "按 Home 键，返回主屏幕",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        ),
        McpTool(
            name = "press_back",
            description = "按返回键",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        ),
        McpTool(
            name = "get_current_app",
            description = "获取当前前台应用的包名",
            inputSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        ),
    )

    // ── HTTP routing ────────────────────────────────────────────

    override fun serve(session: IHTTPSession): Response {
        // CORS headers for all responses
        val corsHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type",
        )

        return when {
            session.method == Method.OPTIONS -> {
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").also { r ->
                    corsHeaders.forEach { (k, v) -> r.addHeader(k, v) }
                }
            }

            session.method == Method.GET && session.uri == "/health" -> {
                val body = JSONObject().put("status", "ok").toString()
                newFixedLengthResponse(Response.Status.OK, "application/json", body).also { r ->
                    corsHeaders.forEach { (k, v) -> r.addHeader(k, v) }
                }
            }

            session.method == Method.POST && session.uri == "/mcp" -> {
                handleMcp(session).also { r ->
                    corsHeaders.forEach { (k, v) -> r.addHeader(k, v) }
                }
            }

            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }
    }

    // ── MCP JSON-RPC dispatcher ─────────────────────────────────

    private fun handleMcp(session: IHTTPSession): Response {
        // Read POST body
        val bodyMap = HashMap<String, String>()
        try {
            session.parseBody(bodyMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse body", e)
            return jsonRpcErrorResponse(null, JsonRpcError.PARSE_ERROR, "Parse error: ${e.message}")
        }

        val body = bodyMap["postData"] ?: ""
        if (body.isBlank()) {
            return jsonRpcErrorResponse(null, JsonRpcError.INVALID_REQUEST, "Empty request body")
        }

        val request = try {
            JsonRpcRequest.fromJson(JSONObject(body))
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON-RPC request", e)
            return jsonRpcErrorResponse(null, JsonRpcError.PARSE_ERROR, "Invalid JSON: ${e.message}")
        }

        Log.d(TAG, "MCP request: method=${request.method}, id=${request.id}")

        return when (request.method) {
            "initialize" -> handleInitialize(request)
            "notifications/initialized" -> {
                // Client acknowledgement, no response needed but send empty OK
                newFixedLengthResponse(Response.Status.OK, "application/json", "")
            }
            "tools/list" -> handleToolsList(request)
            "tools/call" -> handleToolsCall(request)
            else -> jsonRpcErrorResponse(request.id, JsonRpcError.METHOD_NOT_FOUND, "Unknown method: ${request.method}")
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): Response {
        val result = McpInitializeResult(
            protocolVersion = PROTOCOL_VERSION,
            capabilities = mapOf("tools" to emptyMap<String, Any>()),
            serverInfo = McpInitializeResult.ServerInfo(
                name = "android-phone-observer",
                version = "1.0.0"
            )
        )
        return jsonRpcSuccessResponse(request.id, result.toJson())
    }

    private fun handleToolsList(request: JsonRpcRequest): Response {
        val toolsResult = McpToolsListResult(tools = mcpTools)
        return jsonRpcSuccessResponse(request.id, toolsResult.toJson())
    }

    private fun handleToolsCall(request: JsonRpcRequest): Response {
        val params = request.params ?: return jsonRpcErrorResponse(
            request.id, JsonRpcError.INVALID_PARAMS, "Missing params"
        )

        val toolName = params["name"] as? String ?: return jsonRpcErrorResponse(
            request.id, JsonRpcError.INVALID_PARAMS, "Missing tool name"
        )

        @Suppress("UNCHECKED_CAST")
        val args = params["arguments"] as? Map<String, Any?> ?: emptyMap()

        val callResult = try {
            executeTool(toolName, args)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $toolName", e)
            McpToolCallResult(
                content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Error: ${e.message}")),
                isError = true
            )
        }

        return jsonRpcSuccessResponse(request.id, callResult.toJson())
    }

    // ── Tool execution ──────────────────────────────────────────

    private fun executeTool(name: String, args: Map<String, Any?>): McpToolCallResult {
        if (!AccessibilityProxy.isServiceReady()) {
            return McpToolCallResult(
                content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Accessibility service not connected")),
                isError = true
            )
        }

        return when (name) {
            "get_view_tree" -> runBlocking {
                val useCache = args["use_cache"] as? Boolean ?: true
                val interactiveOnly = args["interactive_only"] as? Boolean ?: false
                val nodes = AccessibilityProxy.dumpViewTree(useCache)
                val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
                // Store refs for tap-by-ref
                lastSnapshotRefs = result.refs

                val output = buildString {
                    appendLine(result.snapshot)
                    appendLine()
                    appendLine("---")
                    appendLine("Refs: ${result.stats.refCount} | Interactive: ${result.stats.interactiveNodes} | Total nodes: ${result.stats.totalNodes}")
                    appendLine("Use [ref=eN] with tap/long_press tools to interact with elements.")
                }

                McpToolCallResult(
                    content = listOf(McpToolCallResult.ContentItem(type = "text", text = output))
                )
            }

            "screenshot" -> runBlocking {
                if (!AccessibilityProxy.isMediaProjectionGranted()) {
                    return@runBlocking McpToolCallResult(
                        content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Screen capture permission not granted")),
                        isError = true
                    )
                }
                val base64 = AccessibilityProxy.captureScreen()
                if (base64.isNotEmpty()) {
                    McpToolCallResult(
                        content = listOf(McpToolCallResult.ContentItem(type = "image", data = base64, mimeType = "image/png"))
                    )
                } else {
                    McpToolCallResult(
                        content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Screenshot failed")),
                        isError = true
                    )
                }
            }

            "tap" -> runBlocking {
                val ref = args["ref"] as? String
                val (x, y) = if (ref != null) {
                    resolveRefCoords(ref) ?: return@runBlocking McpToolCallResult(
                        content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Unknown ref: $ref. Run get_view_tree first to get valid refs.")),
                        isError = true
                    )
                } else {
                    val xArg = (args["x"] as? Number)?.toInt() ?: return@runBlocking paramError("x or ref")
                    val yArg = (args["y"] as? Number)?.toInt() ?: return@runBlocking paramError("y or ref")
                    xArg to yArg
                }
                val ok = AccessibilityProxy.tap(x, y)
                val refInfo = if (ref != null) " (ref=$ref)" else ""
                textResult(if (ok) "Tapped at ($x, $y)$refInfo" else "Tap failed at ($x, $y)$refInfo")
            }

            "long_press" -> runBlocking {
                val ref = args["ref"] as? String
                val (x, y) = if (ref != null) {
                    resolveRefCoords(ref) ?: return@runBlocking McpToolCallResult(
                        content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Unknown ref: $ref. Run get_view_tree first to get valid refs.")),
                        isError = true
                    )
                } else {
                    val xArg = (args["x"] as? Number)?.toInt() ?: return@runBlocking paramError("x or ref")
                    val yArg = (args["y"] as? Number)?.toInt() ?: return@runBlocking paramError("y or ref")
                    xArg to yArg
                }
                val ok = AccessibilityProxy.longPress(x, y)
                val refInfo = if (ref != null) " (ref=$ref)" else ""
                textResult(if (ok) "Long pressed at ($x, $y)$refInfo" else "Long press failed at ($x, $y)$refInfo")
            }

            "swipe" -> runBlocking {
                val sx = (args["start_x"] as? Number)?.toInt() ?: return@runBlocking paramError("start_x")
                val sy = (args["start_y"] as? Number)?.toInt() ?: return@runBlocking paramError("start_y")
                val ex = (args["end_x"] as? Number)?.toInt() ?: return@runBlocking paramError("end_x")
                val ey = (args["end_y"] as? Number)?.toInt() ?: return@runBlocking paramError("end_y")
                val dur = (args["duration_ms"] as? Number)?.toLong() ?: 300L
                val ok = AccessibilityProxy.swipe(sx, sy, ex, ey, dur)
                textResult(if (ok) "Swiped ($sx,$sy) → ($ex,$ey)" else "Swipe failed")
            }

            "input_text" -> {
                val text = args["text"] as? String ?: return paramError("text")
                val ok = AccessibilityProxy.inputText(text)
                textResult(if (ok) "Typed: $text" else "Input text failed (no focused field?)")
            }

            "press_home" -> {
                val ok = AccessibilityProxy.pressHome()
                textResult(if (ok) "Home pressed" else "Home press failed")
            }

            "press_back" -> {
                val ok = AccessibilityProxy.pressBack()
                textResult(if (ok) "Back pressed" else "Back press failed")
            }

            "get_current_app" -> runBlocking {
                val pkg = AccessibilityProxy.getCurrentPackageName()
                textResult(pkg)
            }

            else -> McpToolCallResult(
                content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Unknown tool: $name")),
                isError = true
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun textResult(text: String) = McpToolCallResult(
        content = listOf(McpToolCallResult.ContentItem(type = "text", text = text))
    )

    private fun paramError(param: String) = McpToolCallResult(
        content = listOf(McpToolCallResult.ContentItem(type = "text", text = "Missing required parameter: $param")),
        isError = true
    )

    private fun jsonRpcSuccessResponse(id: Any, result: JSONObject): Response {
        val json = JsonRpcResponse(id = id, result = result).toJson()
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun jsonRpcErrorResponse(id: Any?, code: Int, message: String): Response {
        val json = JsonRpcError(id = id, error = JsonRpcError.ErrorObject(code, message)).toJson()
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    override fun start() {
        super.start()
        Log.i(TAG, "MCP Server started on port $listeningPort")
    }
}
