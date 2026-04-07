package com.xiaomo.androidforclaw.wizard

/**
 * OpenClaw module: wizard
 * Source: OpenClaw/src/wizard/session.ts
 *
 * Adapted for Android: drives ModelSetupActivity step-by-step UI.
 */
object WizardSession {

    private var state = WizardSessionState()

    fun getState(): WizardSessionState = state

    fun start(steps: List<WizardStep>) {
        state = WizardSessionState(
            status = WizardSessionStatus.IN_PROGRESS,
            steps = steps,
            startedAt = System.currentTimeMillis()
        )
    }

    fun currentStep(): WizardStep? {
        if (state.status != WizardSessionStatus.IN_PROGRESS) return null
        return state.steps.getOrNull(state.currentStepIndex)
    }

    fun completeStep(stepId: String, value: String? = null) {
        val updatedSteps = state.steps.map {
            if (it.id == stepId) it.copy(completedValue = value) else it
        }
        val newCompleted = state.completedStepIds + stepId
        val nextIndex = state.currentStepIndex + 1
        state = if (nextIndex >= state.steps.size) {
            WizardSetup.markSetupComplete(state.copy(steps = updatedSteps, completedStepIds = newCompleted))
        } else {
            state.copy(steps = updatedSteps, completedStepIds = newCompleted, currentStepIndex = nextIndex)
        }
    }

    fun skip() {
        state = WizardSetup.markSetupSkipped(state)
    }

    fun reset() {
        state = WizardSessionState()
    }
}
