package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/gateway-bind-url.ts + gateway-method-policy.ts +
 *         session-types.ts + operator-scope-compat.ts + node-list-types.ts
 *
 * Gateway and session related types.
 */

// --- gateway-bind-url.ts ---

data class GatewayBindUrl(
    val scheme: String = "http",
    val host: String = "0.0.0.0",
    val port: Int = 0
) {
    fun toUrl(): String = "$scheme://$host:$port"
}

fun parseGatewayBindUrl(urlStr: String): GatewayBindUrl? {
    return try {
        val url = java.net.URL(urlStr)
        GatewayBindUrl(
            scheme = url.protocol,
            host = url.host,
            port = url.port
        )
    } catch (_: Exception) {
        null
    }
}

// --- gateway-method-policy.ts ---

enum class GatewayMethodAccess {
    PUBLIC,       // No authentication required
    DEVICE_AUTH,  // Requires device auth token
    OPERATOR,     // Requires operator scope
    ADMIN         // Requires admin scope
}

data class GatewayMethodPolicy(
    val access: GatewayMethodAccess = GatewayMethodAccess.DEVICE_AUTH,
    val requireScopes: List<String> = emptyList()
)

// --- session-types.ts ---

data class SessionInfo(
    val sessionId: String,
    val agentId: String? = null,
    val label: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastActiveMs: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

// --- operator-scope-compat.ts ---

object OperatorScopeCompat {
    private val SCOPE_HIERARCHY = mapOf(
        "operator.admin" to setOf("operator.admin", "operator.write", "operator.read"),
        "operator.write" to setOf("operator.write", "operator.read"),
        "operator.read" to setOf("operator.read")
    )

    /** Check if the given scopes grant access to the required scope. */
    fun hasScope(grantedScopes: Collection<String>, requiredScope: String): Boolean {
        for (granted in grantedScopes) {
            val expanded = SCOPE_HIERARCHY[granted] ?: setOf(granted)
            if (requiredScope in expanded) return true
        }
        return false
    }

    /** Expand scopes to include all implied scopes. */
    fun expandScopes(scopes: Collection<String>): Set<String> {
        val result = mutableSetOf<String>()
        for (scope in scopes) {
            val expanded = SCOPE_HIERARCHY[scope] ?: setOf(scope)
            result.addAll(expanded)
        }
        return result
    }
}

// --- node-list-types.ts ---

data class PairedNode(
    val nodeId: String,
    val name: String,
    val publicKey: String? = null,
    val pairedAtMs: Long = 0,
    val lastSeenMs: Long = 0,
    val role: String = "device",
    val scopes: List<String> = emptyList()
)

data class PendingRequest(
    val requestId: String,
    val deviceName: String,
    val code: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val status: String = "pending"
)

data class PairingList(
    val paired: List<PairedNode> = emptyList(),
    val pending: List<PendingRequest> = emptyList()
)
