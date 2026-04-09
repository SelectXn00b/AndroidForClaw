package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/device-auth.ts + device-auth-store.ts
 *
 * Device authentication types and normalization.
 */

data class DeviceAuthEntry(
    val token: String,
    val role: String,
    val scopes: List<String>,
    val updatedAtMs: Long
)

data class DeviceAuthStore(
    val version: Int = 1,
    val deviceId: String,
    val tokens: Map<String, DeviceAuthEntry>
)

fun normalizeDeviceAuthRole(role: String): String = role.trim()

fun normalizeDeviceAuthScopes(scopes: List<String>?): List<String> {
    if (scopes == null) return emptyList()
    val out = mutableSetOf<String>()
    for (scope in scopes) {
        val trimmed = scope.trim()
        if (trimmed.isNotEmpty()) out.add(trimmed)
    }
    // Scope hierarchy: admin implies write implies read
    if ("operator.admin" in out) {
        out.add("operator.read")
        out.add("operator.write")
    } else if ("operator.write" in out) {
        out.add("operator.read")
    }
    return out.sorted()
}
