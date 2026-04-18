package com.xiaomo.hermes.hermes.bridge

/**
 * PlatformToolDelegateImpl — 实现 hermes PlatformToolDelegate 接口
 *
 * 桥接 hermes 工具到 app 已有的 Tool 实现。
 * 在 HermesAgentLoop.init 中调用 setPlatformDelegate() 注入。
 */

import android.content.Context
import com.xiaomo.hermes.agent.tools.AndroidToolRegistry
import com.xiaomo.hermes.agent.tools.ToolRegistry as AppToolRegistry
import com.xiaomo.hermes.hermes.PlatformToolDelegate
import com.xiaomo.hermes.logging.Log
import com.google.gson.Gson

class PlatformToolDelegateImpl(
    private val context: Context,
    private val appToolRegistry: AppToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry
) : PlatformToolDelegate {

    companion object {
        private const val TAG = "PlatformToolDelegate"
    }

    private val gson = Gson()

    // ── Config ──────────────────────────────────────────────────────

    override suspend fun configGet(key: String): String {
        return delegateToAppTool("config_get", mapOf("path" to key))
    }

    override suspend fun configSet(key: String, value: String): String {
        return delegateToAppTool("config_set", mapOf("path" to key, "value" to value))
    }

    // ── Sessions ────────────────────────────────────────────────────

    override suspend fun sessionsOp(action: String, args: Map<String, Any>): String {
        val toolName = when (action) {
            "list" -> "sessions_list"
            "send" -> "sessions_send"
            "spawn" -> "sessions_spawn"
            "history" -> "sessions_history"
            "yield" -> "sessions_yield"
            "status" -> "session_status"
            "subagents" -> "subagents"
            else -> return gson.toJson(mapOf("error" to "Unknown session action: $action"))
        }

        // 转换 hermes 的 snake_case 参数名到 app 的参数名
        val mappedArgs = args.toMutableMap<String, Any?>()
        // hermes 用 session_key, app 用 sessionKey
        args["session_key"]?.let {
            mappedArgs["sessionKey"] = it
            mappedArgs.remove("session_key")
        }
        args["active_minutes"]?.let {
            mappedArgs["activeMinutes"] = it
            mappedArgs.remove("active_minutes")
        }

        return delegateToAppTool(toolName, mappedArgs)
    }

    // ── Message ─────────────────────────────────────────────────────

    override suspend fun messageSend(args: Map<String, Any>): String {
        @Suppress("UNCHECKED_CAST")
        return delegateToAppTool("message", args as Map<String, Any?>)
    }

    // ── Android ─────────────────────────────────────────────────────

    override suspend fun androidOp(action: String, args: Map<String, Any>): String {
        val toolName = when (action) {
            "start_activity" -> "start_activity"
            "device" -> "device"
            "canvas" -> "canvas"
            "termux_bridge" -> "exec"  // TermuxBridgeTool 注册名为 exec（自动路由到 Termux）
            "tasker" -> "tasker"
            else -> return gson.toJson(mapOf("error" to "Unknown android action: $action"))
        }
        @Suppress("UNCHECKED_CAST")
        return delegateToAppTool(toolName, args as Map<String, Any?>)
    }

    // ── Skills ──────────────────────────────────────────────────────

    override suspend fun skillsOp(action: String, args: Map<String, Any>): String {
        // hermes 注册名: clawhub_search / clawhub_install
        // app 实际工具名: skills_search / skills_install
        val toolName = when (action) {
            "search" -> "skills_search"
            "install" -> "skills_install"
            else -> return gson.toJson(mapOf("error" to "Unknown skills action: $action"))
        }
        @Suppress("UNCHECKED_CAST")
        return delegateToAppTool(toolName, args as Map<String, Any?>)
    }

    // ── 内部委托 ────────────────────────────────────────────────────

    /**
     * 先查 appToolRegistry（universal），再查 androidToolRegistry（platform）
     */
    private suspend fun delegateToAppTool(name: String, args: Map<String, Any?>): String {
        return try {
            val result = if (appToolRegistry.contains(name)) {
                appToolRegistry.execute(name, args)
            } else if (androidToolRegistry.contains(name)) {
                androidToolRegistry.execute(name, args)
            } else {
                Log.w(TAG, "Tool not found in app registries: $name")
                return gson.toJson(mapOf("error" to "Tool $name not registered in app module"))
            }

            // ToolResult → JSON string
            if (result.success) {
                result.content
            } else {
                gson.toJson(mapOf("error" to result.content))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool $name failed: ${e.message}", e)
            gson.toJson(mapOf("error" to "${e.message}"))
        }
    }
}
