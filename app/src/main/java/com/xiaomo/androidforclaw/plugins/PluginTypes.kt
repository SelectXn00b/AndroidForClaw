package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.pluginsdk.PluginManifest

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/types.ts
 */

enum class PluginCapability {
    TOOL, COMMAND, PROVIDER, FLOW, CONTEXT_ENGINE, MEMORY, WEBHOOK
}

enum class PluginState { REGISTERED, LOADED, ACTIVE, ERROR, DISABLED }

data class PluginInfo(
    val manifest: PluginManifest,
    val state: PluginState = PluginState.REGISTERED,
    val capabilities: Set<PluginCapability> = emptySet(),
    val loadedAt: Long? = null,
    val errorMessage: String? = null
)

data class PluginLifecycle(
    val pluginId: String,
    val events: List<PluginLifecycleEvent> = emptyList()
)

data class PluginLifecycleEvent(
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String? = null
)
