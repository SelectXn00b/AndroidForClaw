package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/config-eval.ts
 */

fun isTruthy(value: Any?): Boolean = when (value) {
    null -> false
    is Boolean -> value
    is Number -> value.toDouble() != 0.0
    is String -> value.isNotBlank() && value != "false" && value != "0"
    else -> true
}

fun resolveConfigPath(config: Any?, pathStr: String): Any? {
    if (config == null) return null
    val parts = pathStr.split("/").filter { it.isNotEmpty() }
    var current: Any? = config
    for (part in parts) {
        current = when (current) {
            is Map<*, *> -> current[part]
            else -> return null
        }
    }
    return current
}

data class RuntimeRequires(
    val bins: List<String>? = null,
    val anyBins: List<String>? = null,
    val env: List<String>? = null,
    val config: List<String>? = null
)
