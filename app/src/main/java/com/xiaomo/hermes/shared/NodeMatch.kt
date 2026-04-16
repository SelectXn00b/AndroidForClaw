package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/node-match.ts + node-resolve.ts + node-list-parse.ts
 *
 * Node/device matching and resolution logic for multi-device setups.
 */

data class NodeMatchCandidate(
    val nodeId: String,
    val name: String,
    val aliases: List<String> = emptyList()
)

/**
 * Resolve a node ID from a user-provided query against a list of candidates.
 * Supports exact nodeId match, exact name match, and prefix/substring matching.
 * Returns null if ambiguous or no match.
 */
fun resolveNodeIdFromCandidates(
    query: String,
    candidates: List<NodeMatchCandidate>
): String? {
    if (query.isBlank() || candidates.isEmpty()) return null
    val normalized = query.trim().lowercase()

    // 1. Exact nodeId match
    candidates.find { it.nodeId.lowercase() == normalized }?.let { return it.nodeId }

    // 2. Exact name match
    val nameMatches = candidates.filter { it.name.lowercase() == normalized }
    if (nameMatches.size == 1) return nameMatches[0].nodeId

    // 3. Exact alias match
    val aliasMatches = candidates.filter { c ->
        c.aliases.any { it.lowercase() == normalized }
    }
    if (aliasMatches.size == 1) return aliasMatches[0].nodeId

    // 4. Prefix match on nodeId
    val prefixMatches = candidates.filter { it.nodeId.lowercase().startsWith(normalized) }
    if (prefixMatches.size == 1) return prefixMatches[0].nodeId

    // 5. Prefix match on name
    val namePrefixMatches = candidates.filter { it.name.lowercase().startsWith(normalized) }
    if (namePrefixMatches.size == 1) return namePrefixMatches[0].nodeId

    // 6. Substring match on name (last resort)
    val substringMatches = candidates.filter { normalized in it.name.lowercase() }
    if (substringMatches.size == 1) return substringMatches[0].nodeId

    // Ambiguous or no match
    return null
}

/**
 * Parse a node list response into paired nodes and pending requests.
 * Aligned with TS parseNodeList().
 */
fun parseNodeList(
    pairedRaw: List<Map<String, Any?>>?,
    pendingRaw: List<Map<String, Any?>>?
): PairingList {
    val paired = pairedRaw?.mapNotNull { m ->
        val nodeId = m["nodeId"] as? String ?: return@mapNotNull null
        val name = m["name"] as? String ?: return@mapNotNull null
        PairedNode(
            nodeId = nodeId,
            name = name,
            publicKey = m["publicKey"] as? String,
            pairedAtMs = (m["pairedAtMs"] as? Number)?.toLong() ?: 0,
            lastSeenMs = (m["lastSeenMs"] as? Number)?.toLong() ?: 0,
            role = m["role"] as? String ?: "device",
            scopes = (m["scopes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        )
    } ?: emptyList()

    val pending = pendingRaw?.mapNotNull { m ->
        val requestId = m["requestId"] as? String ?: return@mapNotNull null
        PendingRequest(
            requestId = requestId,
            deviceName = m["deviceName"] as? String ?: "",
            code = m["code"] as? String ?: "",
            createdAtMs = (m["createdAtMs"] as? Number)?.toLong() ?: 0,
            expiresAtMs = (m["expiresAtMs"] as? Number)?.toLong() ?: 0,
            status = m["status"] as? String ?: "pending"
        )
    } ?: emptyList()

    return PairingList(paired, pending)
}
