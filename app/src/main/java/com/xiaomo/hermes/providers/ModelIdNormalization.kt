package com.xiaomo.hermes.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-id-normalization.ts
 *
 * Model ID normalization — dependency-free so config parsing and other
 * startup-only paths do not pull in provider discovery or plugin loading.
 */
object ModelIdNormalization {

    /**
     * Normalize Google model IDs to their current API-accepted forms.
     *
     * Google frequently renames preview models; this mapping keeps user configs
     * working across renames without manual edits.
     */
    fun normalizeGoogleModelId(id: String): String = when (id) {
        "gemini-3-pro" -> "gemini-3-pro-preview"
        "gemini-3-flash" -> "gemini-3-flash-preview"
        "gemini-3.1-pro" -> "gemini-3.1-pro-preview"
        "gemini-3.1-flash-lite" -> "gemini-3.1-flash-lite-preview"
        // Preserve compatibility with earlier OpenClaw docs/config that pointed at a
        // non-existent Gemini Flash preview ID. Google's current Flash text model is
        // `gemini-3-flash-preview`.
        "gemini-3.1-flash", "gemini-3.1-flash-preview" -> "gemini-3-flash-preview"
        else -> id
    }

    /**
     * Normalize xAI model IDs — map verbose experimental names to short canonical forms.
     */
    fun normalizeXaiModelId(id: String): String = when (id) {
        "grok-4.20-experimental-beta-0304-reasoning" -> "grok-4.20-reasoning"
        "grok-4.20-experimental-beta-0304-non-reasoning" -> "grok-4.20-non-reasoning"
        else -> id
    }

    /**
     * Apply all provider-specific normalizations based on the provider name.
     */
    fun normalizeModelId(provider: String, modelId: String): String {
        return when {
            provider.equals("google", ignoreCase = true) ||
            provider.equals("google-generative-ai", ignoreCase = true) -> normalizeGoogleModelId(modelId)

            provider.equals("xai", ignoreCase = true) ||
            provider.equals("x-ai", ignoreCase = true) -> normalizeXaiModelId(modelId)

            else -> modelId
        }
    }
}
