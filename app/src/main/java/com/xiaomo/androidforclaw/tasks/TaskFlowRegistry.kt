package com.xiaomo.androidforclaw.tasks

import com.xiaomo.androidforclaw.flows.FlowContribution
import com.xiaomo.androidforclaw.flows.FlowContributionKind
import com.xiaomo.androidforclaw.flows.FlowContributionSurface
import com.xiaomo.androidforclaw.flows.FlowOption

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/flow-registry.ts
 *
 * Bridges task definitions into the flow contribution system
 * so that tasks appear in setup wizards and health surfaces.
 */
object TaskFlowRegistry {

    private const val FLOW_PREFIX = "task:"

    fun contributeTaskFlows(registeredTasks: List<String>): List<FlowContribution> {
        return registeredTasks.map { taskName ->
            FlowContribution(
                id = "$FLOW_PREFIX$taskName",
                kind = FlowContributionKind.CORE,
                surface = FlowContributionSurface.HEALTH,
                option = FlowOption(
                    value = "$FLOW_PREFIX$taskName",
                    label = taskName.replaceFirstChar { it.uppercase() }
                )
            )
        }
    }

    fun resolveTaskFromFlowOption(optionValue: String): String? {
        return if (optionValue.startsWith(FLOW_PREFIX)) {
            optionValue.removePrefix(FLOW_PREFIX)
        } else null
    }
}
