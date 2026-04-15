package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * MCP (Model Context Protocol) Client Support.
 * Connects to external MCP servers via HTTP, discovers tools, and calls them.
 * Ported from mcp_tool.py
 */
object McpTool {

    private const val TAG = "Mcptool"
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class McpServerConfig(
        val command: String? = null,
        val args: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val url: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val timeout: Int = 120,
        val connectTimeout: Int = 60,
    )

    data class McpToolDef(
        val name: String,
        val description: String = "",
        val inputSchema: Map<String, Any> = emptyMap(),
    )

    data class McpCallResult(
        val content: String = "",
        val isError: Boolean = false,
        val error: String? = null,
    )

    private val _servers = ConcurrentHashMap<String, McpServerConfig>()
    private val _tools = ConcurrentHashMap<String, Pair<String, McpToolDef>>() // toolName -> (serverName, def)
    private val _httpClients = ConcurrentHashMap<String, OkHttpClient>()

    /**
     * Register an MCP server configuration.
     */
    fun registerServer(name: String, config: McpServerConfig) {
        _servers[name] = config
        if (!config.url.isNullOrEmpty()) {
            _httpClients[name] = OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(config.timeout.toLong(), TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Unregister an MCP server.
     */
    fun unregisterServer(name: String) {
        _servers.remove(name)
        _httpClients.remove(name)
        _tools.entries.removeIf { it.value.first == name }
    }

    /**
     * Discover tools from an HTTP MCP server.
     */
    fun discoverTools(serverName: String): List<McpToolDef> {
        val config = _servers[serverName] ?: return emptyList()
        val url = config.url ?: return emptyList()

        return try {
            val client = _httpClients[serverName] ?: OkHttpClient()
            val payload = gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "tools/list",
            ))

            val requestBuilder = Request.Builder()
                .url("$url/mcp")
                .post(payload.toRequestBody(JSON))
                .header("Content-Type", "application/json")

            for ((k, v) in config.headers) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to list tools from $serverName: ${response.code}")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
                val result = json["result"] as? Map<String, Any> ?: return emptyList()
                val toolsList = result["tools"] as? List<Map<String, Any>> ?: return emptyList()

                toolsList.map { tool ->
                    val name = tool["name"] as? String ?: "unknown"
                    val desc = tool["description"] as? String ?: ""
                    val schema = (tool["inputSchema"] as? Map<String, Any>) ?: emptyMap()
                    val def = McpToolDef(name, desc, schema)
                    _tools[name] = serverName to def
                    def
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover tools from $serverName: ${e.message}")
            emptyList()
        }
    }

    /**
     * Call an MCP tool.
     */
    fun callTool(toolName: String, arguments: Map<String, Any> = emptyMap()): McpCallResult {
        val (serverName, _) = _tools[toolName]
            ?: return McpCallResult(isError = true, error = "Tool '$toolName' not found")

        val config = _servers[serverName]
            ?: return McpCallResult(isError = true, error = "Server '$serverName' not configured")

        val url = config.url
            ?: return McpCallResult(isError = true, error = "Server '$serverName' has no HTTP endpoint")

        return try {
            val client = _httpClients[serverName] ?: OkHttpClient()
            val payload = gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to System.currentTimeMillis(),
                "method" to "tools/call",
                "params" to mapOf("name" to toolName, "arguments" to arguments),
            ))

            val requestBuilder = Request.Builder()
                .url("$url/mcp")
                .post(payload.toRequestBody(JSON))
                .header("Content-Type", "application/json")

            for ((k, v) in config.headers) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return McpCallResult(isError = true, error = "HTTP ${response.code}: ${body.take(500)}")
                }

                val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
                val error = json["error"] as? Map<String, Any>
                if (error != null) {
                    return McpCallResult(isError = true, error = stripCredentials(error["message"] as? String ?: "Unknown error"))
                }

                val result = json["result"] as? Map<String, Any> ?: return McpCallResult(isError = true, error = "No result")
                val contentList = result["content"] as? List<Map<String, Any>> ?: emptyList()

                val textContent = contentList
                    .filter { it["type"] == "text" }
                    .joinToString("\n") { it["text"] as? String ?: "" }

                McpCallResult(content = textContent, isError = false)
            }
        } catch (e: Exception) {
            McpCallResult(isError = true, error = "Call failed: ${stripCredentials(e.message)}")
        }
    }

    /**
     * Get all discovered tools.
     */
    fun getDiscoveredTools(): Map<String, McpToolDef> = _tools.mapValues { it.value.second }

    /**
     * Get all registered servers.
     */
    fun getServers(): Map<String, McpServerConfig> = _servers.toMap()

    /**
     * Strip credentials from error messages.
     */
    private fun stripCredentials(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        return message
            .replace(Regex("ghp_[A-Za-z0-9_]{1,255}"), "[CREDENTIAL_REDACTED]")
            .replace(Regex("sk-[A-Za-z0-9_]{1,255}"), "[CREDENTIAL_REDACTED]")
            .replace(Regex("Bearer\\s+\\S+"), "Bearer [CREDENTIAL_REDACTED]")
            .replace(Regex("(token|key|password|secret)=[^\\s&,;\"']{1,255}", RegexOption.IGNORE_CASE), "$1=[CREDENTIAL_REDACTED]")
    }


    // === Missing constants (auto-generated stubs) ===
    val _MCP_AVAILABLE = false
    val _MCP_HTTP_AVAILABLE = false
    val _MCP_SAMPLING_TYPES = emptySet<Any>()
    val _MCP_NOTIFICATION_TYPES = emptySet<Any>()
    val _MCP_MESSAGE_HANDLER_SUPPORTED = ""
    val _DEFAULT_TOOL_TIMEOUT = 0
    val _DEFAULT_CONNECT_TIMEOUT = 0
    val _MAX_RECONNECT_RETRIES = 0
    val _MAX_INITIAL_CONNECT_RETRIES = 0
    val _MAX_BACKOFF_SECONDS = 0
    val _SAFE_ENV_KEYS = emptySet<Any>()
    val _CREDENTIAL_PATTERN = Regex("")
    val _UTILITY_CAPABILITY_METHODS = emptySet<Any>()

    // === Missing methods (auto-generated stubs) ===
    private fun checkMessageHandlerSupport(): Unit {
    // Hermes: _check_message_handler_support
}

    /** Sliding-window rate limiter.  Returns True if request is allowed. */
    fun _checkRateLimit(): Boolean {
        return false
    }
    /** Config override > server hint > None (use default). */
    fun _resolveModel(preferences: Any?): String? {
        return null
    }
    /** Extract text from a ToolResultContent block. */
    fun _extractToolResultText(block: Any?): String {
        return ""
    }
    /** Convert MCP SamplingMessages to OpenAI format. */
    fun _convertMessages(params: Any?): List<Any?> {
        return emptyList()
    }
    /** Return ErrorData (MCP spec) or raise as fallback. */
    fun _error(message: String, code: Int = -1): Any? {
        return null
    }
    /** Build a CreateMessageResultWithTools from an LLM tool_calls response. */
    fun _buildToolUseResult(choice: Any?, response: Any?): Any? {
        return null
    }
    /** Build a CreateMessageResult from a normal text response. */
    fun _buildTextResult(choice: Any?, response: Any?): Any? {
        return null
    }
    /** Return kwargs to pass to ClientSession for sampling support. */
    fun sessionKwargs(): Any? {
        return null
    }
    /** Check if this server uses HTTP transport. */
    fun _isHttp(): Boolean {
        return false
    }
    /** Build a ``message_handler`` callback for ``ClientSession``. */
    fun _makeMessageHandler(): Any? {
        return null
    }
    /** Re-fetch tools from the server and update the registry. */
    suspend fun _refreshTools(): Any? {
        return null
    }
    /** Run the server using stdio transport. */
    suspend fun _runStdio(config: Any?): Any? {
        return null
    }
    /** Run the server using HTTP/StreamableHTTP transport. */
    suspend fun _runHttp(config: Any?): Any? {
        return null
    }
    /** Discover tools from the connected session. */
    suspend fun _discoverTools(): Any? {
        return null
    }
    /** Long-lived coroutine: connect, discover tools, wait, disconnect. */
    suspend fun run(config: Any?): Any? {
        return null
    }
    /** Create the background Task and wait until ready (or failed). */
    suspend fun start(config: Any?): Any? {
        return null
    }
    /** Signal the Task to exit and wait for clean resource teardown. */
    suspend fun shutdown(): Any? {
        return null
    }

}
