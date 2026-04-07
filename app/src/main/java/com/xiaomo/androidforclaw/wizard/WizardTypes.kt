package com.xiaomo.androidforclaw.wizard

/**
 * OpenClaw module: wizard
 * Source: OpenClaw/src/wizard/types.ts
 *
 * Adapted for Android: wizard data model drives ModelSetupActivity UI.
 */

enum class WizardSessionStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, SKIPPED }

enum class WizardStepType { PROVIDER_SELECT, MODEL_SELECT, API_KEY_INPUT, VERIFICATION, DONE }

data class WizardStep(
    val id: String,
    val type: WizardStepType,
    val title: String,
    val description: String? = null,
    val required: Boolean = true,
    val completedValue: String? = null
)

data class WizardSessionState(
    val status: WizardSessionStatus = WizardSessionStatus.NOT_STARTED,
    val currentStepIndex: Int = 0,
    val steps: List<WizardStep> = emptyList(),
    val completedStepIds: Set<String> = emptySet(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)
