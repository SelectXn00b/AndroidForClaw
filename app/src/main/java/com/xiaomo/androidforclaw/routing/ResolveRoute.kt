package com.xiaomo.androidforclaw.routing

import com.xiaomo.androidforclaw.config.OpenClawConfig

data class RoutePeer(val kind: String, val id: String)

data class ResolveAgentRouteInput(
    val cfg: OpenClawConfig,
    val channel: String,
    val accountId: String? = null,
    val peer: RoutePeer? = null,
    val parentPeer: RoutePeer? = null,
    val guildId: String? = null,
    val teamId: String? = null,
    val memberRoleIds: List<String>? = null
)

data class ResolvedAgentRoute(
    val agentId: String,
    val channel: String,
    val accountId: String,
    val sessionKey: String,
    val mainSessionKey: String,
    val matchedBy: String
)

fun resolveAgentRoute(input: ResolveAgentRouteInput): ResolvedAgentRoute {
    val agentId = DEFAULT_AGENT_ID
    val accountId = normalizeAccountId(input.accountId)
    val sessionKey = if (input.peer != null) {
        buildAgentPeerSessionKey(agentId, input.peer.kind, input.peer.id, accountId)
    } else {
        buildAgentMainSessionKey(agentId)
    }
    return ResolvedAgentRoute(
        agentId = agentId,
        channel = input.channel,
        accountId = accountId,
        sessionKey = sessionKey,
        mainSessionKey = buildAgentMainSessionKey(agentId),
        matchedBy = "default"
    )
}
