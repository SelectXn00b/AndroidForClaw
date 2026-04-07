package com.xiaomo.androidforclaw.contextengine

import com.xiaomo.androidforclaw.config.OpenClawConfig

typealias ContextEngineFactory = suspend () -> ContextEngine

object ContextEngineRegistry {
    private val factories = mutableMapOf<String, ContextEngineFactory>()

    fun registerContextEngine(id: String, factory: ContextEngineFactory): Boolean {
        if (factories.containsKey(id)) return false
        factories[id] = factory
        return true
    }

    fun getContextEngineFactory(id: String): ContextEngineFactory? = factories[id]

    fun listContextEngineIds(): List<String> = factories.keys.toList()

    suspend fun resolveContextEngine(config: OpenClawConfig? = null): ContextEngine {
        val id = factories.keys.firstOrNull()
            ?: error("No context engines registered")
        return factories[id]!!.invoke()
    }
}
