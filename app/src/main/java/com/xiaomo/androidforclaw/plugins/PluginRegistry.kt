package com.xiaomo.androidforclaw.plugins

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/registry.ts
 *
 * Central registry for all loaded plugins.
 */
object PluginRegistry {

    private val plugins = mutableMapOf<String, PluginInfo>()

    fun register(info: PluginInfo) {
        plugins[info.manifest.id] = info
    }

    fun unregister(pluginId: String): PluginInfo? = plugins.remove(pluginId)

    fun get(pluginId: String): PluginInfo? = plugins[pluginId]

    fun listAll(): List<PluginInfo> = plugins.values.toList()

    fun listByCapability(capability: PluginCapability): List<PluginInfo> =
        plugins.values.filter { capability in it.capabilities }

    fun listActive(): List<PluginInfo> =
        plugins.values.filter { it.state == PluginState.ACTIVE }

    fun isRegistered(pluginId: String): Boolean = pluginId in plugins

    fun clear() {
        plugins.clear()
    }
}
