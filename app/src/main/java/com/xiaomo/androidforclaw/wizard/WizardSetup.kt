package com.xiaomo.androidforclaw.wizard

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * OpenClaw module: wizard
 * Source: OpenClaw/src/wizard/setup.ts
 *
 * Builds the default wizard step sequence from config.
 */
object WizardSetup {

    fun buildDefaultSteps(config: OpenClawConfig): List<WizardStep> {
        TODO("Inspect config to determine which steps are needed (provider selection, API key, etc.)")
    }

    fun isSetupRequired(config: OpenClawConfig): Boolean {
        TODO("Check if any provider is configured; if none, setup is required")
    }

    fun markSetupComplete(state: WizardSessionState): WizardSessionState {
        return state.copy(
            status = WizardSessionStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )
    }

    fun markSetupSkipped(state: WizardSessionState): WizardSessionState {
        return state.copy(
            status = WizardSessionStatus.SKIPPED,
            completedAt = System.currentTimeMillis()
        )
    }
}
