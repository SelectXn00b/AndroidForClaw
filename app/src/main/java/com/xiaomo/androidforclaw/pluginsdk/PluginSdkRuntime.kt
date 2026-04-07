package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw module: plugin-sdk
 * Source: OpenClaw/src/plugin-sdk/runtime.ts
 *
 * SDK entry point for plugin authors to register capabilities.
 */
object PluginSdkRuntime {

    private val hooks = java.util.concurrent.ConcurrentHashMap<String, PluginLifecycleHook>()

    fun registerPlugin(manifest: PluginManifest, hook: PluginLifecycleHook? = null) {
        require(manifest.id.isNotBlank()) { "Plugin ID must not be blank" }
        require(manifest.name.isNotBlank()) { "Plugin name must not be blank" }

        val info = com.xiaomo.androidforclaw.plugins.PluginInfo(
            manifest = manifest,
            state = com.xiaomo.androidforclaw.plugins.PluginState.LOADED,
            loadedAt = System.currentTimeMillis()
        )
        com.xiaomo.androidforclaw.plugins.PluginRegistry.register(info)

        if (hook != null) {
            hooks[manifest.id] = hook
            // Fire onLoad asynchronously is not possible from a sync function,
            // so we store the hook for later invocation by the caller.
        }
    }

    fun unregisterPlugin(pluginId: String) {
        hooks.remove(pluginId)
        com.xiaomo.androidforclaw.plugins.PluginRegistry.unregister(pluginId)
    }

    fun isPluginRegistered(pluginId: String): Boolean {
        return com.xiaomo.androidforclaw.plugins.PluginRegistry.isRegistered(pluginId)
    }

    fun getPluginManifest(pluginId: String): PluginManifest? {
        return com.xiaomo.androidforclaw.plugins.PluginRegistry.get(pluginId)?.manifest
    }
}
