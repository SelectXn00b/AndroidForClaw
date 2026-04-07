package com.xiaomo.androidforclaw.flows

/**
 * OpenClaw module: flows
 * Source: OpenClaw/src/flows/
 *
 * Interactive setup flows and wizard orchestration for channel configuration,
 * provider selection, model picking, health checks, and search setup.
 */

object FlowRegistry {
    private val contributions = mutableListOf<FlowContribution>()

    fun registerFlowContribution(contribution: FlowContribution) {
        contributions.add(contribution)
    }

    fun listFlowContributions(surface: FlowContributionSurface? = null): List<FlowContribution> =
        if (surface != null) contributions.filter { it.surface == surface }
        else contributions.toList()

    fun clear() { contributions.clear() }
}
