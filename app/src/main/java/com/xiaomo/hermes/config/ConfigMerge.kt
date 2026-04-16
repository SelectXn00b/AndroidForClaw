package com.xiaomo.hermes.config

import com.xiaomo.hermes.logging.Log
import org.json.JSONObject

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/merge-config.ts
 *
 * Deep-merge two OpenClaw config JSONObjects.
 * Overlay values override base values. Null/missing overlay fields do not erase base values.
 * Maps (providers, etc.) are merged key-by-key. Arrays are replaced wholesale.
 */
object ConfigMerge {

    private const val TAG = "ConfigMerge"

    /**
     * Deep-merge two JSON configs. Returns a new JSONObject with base values
     * overridden by overlay values where present.
     */
    fun mergeJsonConfigs(base: JSONObject, overlay: JSONObject): JSONObject {
        val result = JSONObject(base.toString())
        mergeInto(result, overlay)
        return result
    }

    /**
     * Recursively merge overlay into target (mutates target).
     */
    private fun mergeInto(target: JSONObject, overlay: JSONObject) {
        for (key in overlay.keys()) {
            val overlayValue = overlay.get(key)
            if (overlayValue == JSONObject.NULL) {
                // Explicit null in overlay: skip (don't erase base)
                continue
            }
            if (overlayValue is JSONObject && target.has(key)) {
                val baseValue = target.opt(key)
                if (baseValue is JSONObject) {
                    // Deep merge objects
                    mergeInto(baseValue, overlayValue)
                    continue
                }
            }
            // For arrays and primitives: replace
            target.put(key, overlayValue)
        }
    }

    /**
     * Resolve model alias from the aliases map.
     * Returns the aliased model ID if found, otherwise the original ID.
     */
    fun resolveModelAlias(modelId: String, aliases: Map<String, String>?): String {
        if (aliases.isNullOrEmpty()) return modelId
        return aliases[modelId] ?: modelId
    }
}
