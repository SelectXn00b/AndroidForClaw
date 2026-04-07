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
        val steps = mutableListOf<WizardStep>()
        steps.add(WizardStep(
            id = "provider_select",
            type = WizardStepType.PROVIDER_SELECT,
            title = "Select Provider",
            description = "Choose your AI model provider"
        ))
        steps.add(WizardStep(
            id = "api_key",
            type = WizardStepType.API_KEY_INPUT,
            title = "API Key",
            description = "Enter your provider API key"
        ))
        steps.add(WizardStep(
            id = "model_select",
            type = WizardStepType.MODEL_SELECT,
            title = "Select Model",
            description = "Choose a default model"
        ))
        steps.add(WizardStep(
            id = "verify",
            type = WizardStepType.VERIFICATION,
            title = "Verify",
            description = "Verify your configuration works",
            required = false
        ))
        steps.add(WizardStep(
            id = "done",
            type = WizardStepType.DONE,
            title = "Done",
            required = false
        ))
        return steps
    }

    fun isSetupRequired(config: OpenClawConfig): Boolean {
        // Setup is required if no models are configured
        return config.models == null
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
