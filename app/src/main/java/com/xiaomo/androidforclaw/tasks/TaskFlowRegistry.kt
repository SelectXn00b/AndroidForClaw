package com.xiaomo.androidforclaw.tasks

import com.xiaomo.androidforclaw.flows.FlowContribution

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/flow-registry.ts
 *
 * Bridges task definitions into the flow contribution system
 * so that tasks appear in setup wizards and health surfaces.
 */
object TaskFlowRegistry {

    fun contributeTaskFlows(registeredTasks: List<String>): List<FlowContribution> {
        TODO("Map registered task names to FlowContribution entries")
    }

    fun resolveTaskFromFlowOption(optionValue: String): String? {
        TODO("Reverse-lookup: flow option value → task name")
    }
}
