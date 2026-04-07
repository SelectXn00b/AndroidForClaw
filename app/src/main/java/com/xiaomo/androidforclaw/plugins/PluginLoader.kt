package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.pluginsdk.PluginManifest

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/loader.ts
 *
 * Discovers and loads plugin manifests from configured sources.
 */
object PluginLoader {

    suspend fun discoverPlugins(): List<PluginManifest> {
        TODO("Scan plugin directories / bundled manifests, return discovered manifests")
    }

    suspend fun loadPlugin(manifest: PluginManifest): PluginInfo {
        TODO("Validate manifest, resolve entry point, initialize plugin, return PluginInfo")
    }

    suspend fun unloadPlugin(pluginId: String): Boolean {
        TODO("Tear down plugin, invoke onUnload hook, return success")
    }

    fun validateManifest(manifest: PluginManifest): List<String> {
        val errors = mutableListOf<String>()
        if (manifest.id.isBlank()) errors.add("Plugin ID must not be blank")
        if (manifest.name.isBlank()) errors.add("Plugin name must not be blank")
        if (manifest.version.isBlank()) errors.add("Plugin version must not be blank")
        return errors
    }
}
