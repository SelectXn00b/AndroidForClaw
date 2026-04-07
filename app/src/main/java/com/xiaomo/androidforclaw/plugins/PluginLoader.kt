package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.pluginsdk.PluginManifest

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/loader.ts
 *
 * Discovers and loads plugin manifests from configured sources.
 */
object PluginLoader {

    /** In-memory list of known/bundled plugin manifests. Plugins can be added via [registerBundledManifest]. */
    private val bundledManifests = java.util.concurrent.CopyOnWriteArrayList<PluginManifest>()

    fun registerBundledManifest(manifest: PluginManifest) {
        bundledManifests.add(manifest)
    }

    suspend fun discoverPlugins(): List<PluginManifest> {
        // Return all bundled manifests; in the future this could also scan filesystem paths.
        return bundledManifests.toList()
    }

    suspend fun loadPlugin(manifest: PluginManifest): PluginInfo {
        val errors = validateManifest(manifest)
        if (errors.isNotEmpty()) {
            val info = PluginInfo(
                manifest = manifest,
                state = PluginState.ERROR,
                errorMessage = errors.joinToString("; ")
            )
            PluginRegistry.register(info)
            return info
        }
        val info = PluginInfo(
            manifest = manifest,
            state = PluginState.LOADED,
            loadedAt = System.currentTimeMillis()
        )
        PluginRegistry.register(info)
        return info
    }

    suspend fun unloadPlugin(pluginId: String): Boolean {
        val existing = PluginRegistry.get(pluginId) ?: return false
        PluginRegistry.unregister(pluginId)
        return true
    }

    fun validateManifest(manifest: PluginManifest): List<String> {
        val errors = mutableListOf<String>()
        if (manifest.id.isBlank()) errors.add("Plugin ID must not be blank")
        if (manifest.name.isBlank()) errors.add("Plugin name must not be blank")
        if (manifest.version.isBlank()) errors.add("Plugin version must not be blank")
        return errors
    }
}
