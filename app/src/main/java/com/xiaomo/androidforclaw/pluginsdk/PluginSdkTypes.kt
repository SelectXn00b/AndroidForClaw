package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw module: plugin-sdk
 * Source: OpenClaw/src/plugin-sdk/types.ts
 */

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val author: String? = null,
    val capabilities: List<String> = emptyList(),
    val entryPoint: String? = null,
    val configSchema: Map<String, Any?> = emptyMap()
)

data class PluginContext(
    val pluginId: String,
    val sessionKey: String,
    val config: Map<String, Any?> = emptyMap()
)

interface PluginLifecycleHook {
    suspend fun onLoad(context: PluginContext)
    suspend fun onUnload(context: PluginContext)
}
