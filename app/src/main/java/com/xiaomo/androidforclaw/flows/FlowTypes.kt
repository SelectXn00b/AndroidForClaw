package com.xiaomo.androidforclaw.flows

/**
 * OpenClaw module: flows
 * Source: OpenClaw/src/flows/types.ts
 */

data class FlowDocsLink(val path: String, val label: String? = null)

enum class FlowContributionKind { CHANNEL, CORE, PROVIDER, SEARCH }
enum class FlowContributionSurface { AUTH_CHOICE, HEALTH, MODEL_PICKER, SETUP }

data class FlowOptionGroup(val id: String, val label: String, val hint: String? = null)

data class FlowOption(
    val value: String,
    val label: String,
    val hint: String? = null,
    val group: FlowOptionGroup? = null,
    val docs: FlowDocsLink? = null
)

data class FlowContribution(
    val id: String,
    val kind: FlowContributionKind,
    val surface: FlowContributionSurface,
    val option: FlowOption,
    val source: String? = null
)

fun mergeFlowContributions(
    primary: List<FlowContribution>,
    fallbacks: List<FlowContribution>? = null
): List<FlowContribution> {
    val primaryIds = primary.map { it.id }.toSet()
    val merged = primary.toMutableList()
    fallbacks?.filter { it.id !in primaryIds }?.let { merged.addAll(it) }
    return merged
}

fun sortFlowContributionsByLabel(contributions: List<FlowContribution>): List<FlowContribution> =
    contributions.sortedBy { it.option.label.lowercase() }
