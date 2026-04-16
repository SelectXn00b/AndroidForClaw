package com.xiaomo.hermes.providers

import com.xiaomo.hermes.config.ModelApi
import com.xiaomo.hermes.config.ModelCompatConfig
import com.xiaomo.hermes.config.ModelDefinition
import com.xiaomo.hermes.config.ProviderConfig

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-compat.ts
 *
 * Model compatibility patching — applies provider-specific compat flags
 * so the request builder can adapt behavior per endpoint.
 */
object ModelCompat {

    const val XAI_TOOL_SCHEMA_PROFILE = "xai"
    const val HTML_ENTITY_TOOL_CALL_ARGUMENTS_ENCODING = "html-entities"

    /**
     * Merge a compat patch into a model's existing compat config.
     * Returns a new ModelDefinition if anything changed; the original otherwise.
     */
    fun applyModelCompatPatch(model: ModelDefinition, patch: ModelCompatConfig): ModelDefinition {
        val existing = model.compat
        val merged = mergeCompat(existing, patch)
        // If the existing compat already covers all patch values, skip copy
        if (existing != null && merged == existing) return model
        return model.copy(compat = merged)
    }

    /**
     * Apply xAI-specific compat flags.
     */
    fun applyXaiModelCompat(model: ModelDefinition): ModelDefinition {
        return applyModelCompatPatch(model, ModelCompatConfig(
            toolSchemaProfile = XAI_TOOL_SCHEMA_PROFILE,
            nativeWebSearchTool = true,
            toolCallArgumentsEncoding = HTML_ENTITY_TOOL_CALL_ARGUMENTS_ENCODING
        ))
    }

    /**
     * Check if a model uses the xAI tool schema profile.
     */
    fun usesXaiToolSchemaProfile(compat: ModelCompatConfig?): Boolean {
        return compat?.toolSchemaProfile == XAI_TOOL_SCHEMA_PROFILE
    }

    /**
     * Check if a model has native web search tool support.
     */
    fun hasNativeWebSearchTool(compat: ModelCompatConfig?): Boolean {
        return compat?.nativeWebSearchTool == true
    }

    /**
     * Resolve tool call arguments encoding for a model.
     */
    fun resolveToolCallArgumentsEncoding(compat: ModelCompatConfig?): String? {
        return compat?.toolCallArgumentsEncoding
    }

    /**
     * Returns true only for endpoints that are confirmed to be native OpenAI
     * infrastructure and therefore accept the `developer` message role.
     * All other openai-completions backends (proxies, Qwen, GLM, DeepSeek, etc.)
     * only support the standard `system` role.
     */
    private fun isOpenAINativeEndpoint(baseUrl: String): Boolean {
        return try {
            val host = java.net.URL(baseUrl).host.lowercase()
            host == "api.openai.com"
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Normalize Anthropic baseUrl: strip trailing /v1 that users may have
     * included in their config. The API adapter appends /v1/messages itself.
     */
    private fun normalizeAnthropicBaseUrl(baseUrl: String): String {
        return baseUrl.replace(Regex("/v1/?$"), "")
    }

    /**
     * Apply all applicable model compat normalizations for the given provider + model.
     *
     * - Anthropic: strip trailing /v1 from baseUrl
     * - xAI providers: apply xAI tool compat
     * - Non-native OpenAI-completions endpoints: default off developerRole / usageInStreaming / strictMode
     */
    fun normalizeModelCompat(
        provider: ProviderConfig,
        model: ModelDefinition,
        providerName: String
    ): Pair<ProviderConfig, ModelDefinition> {
        var p = provider
        var m = model
        val api = model.api ?: provider.api

        // Anthropic: strip trailing /v1
        if (api == ModelApi.ANTHROPIC_MESSAGES && provider.baseUrl.isNotEmpty()) {
            val normalized = normalizeAnthropicBaseUrl(provider.baseUrl)
            if (normalized != provider.baseUrl) {
                p = p.copy(baseUrl = normalized)
            }
        }

        // xAI providers: apply xAI tool compat
        if (providerName.equals("xai", ignoreCase = true) ||
            providerName.equals("x-ai", ignoreCase = true)) {
            m = applyXaiModelCompat(m)
        }

        // Non-native OpenAI-completions endpoints: default off unsupported features
        if (api == ModelApi.OPENAI_COMPLETIONS && provider.baseUrl.isNotEmpty()) {
            val needsForce = !isOpenAINativeEndpoint(provider.baseUrl)
            if (needsForce) {
                val compat = m.compat
                val alreadyConfigured = compat != null &&
                    compat.supportsDeveloperRole != null &&
                    compat.supportsUsageInStreaming != null &&
                    compat.supportsStrictMode != null
                if (!alreadyConfigured) {
                    val forcedDeveloperRole = compat?.supportsDeveloperRole == true
                    val hasStreamingOverride = compat?.supportsUsageInStreaming != null
                    val targetStrictMode = compat?.supportsStrictMode ?: false
                    val patch = ModelCompatConfig(
                        supportsDeveloperRole = if (forcedDeveloperRole) true else false,
                        supportsUsageInStreaming = if (hasStreamingOverride) compat?.supportsUsageInStreaming else false,
                        supportsStrictMode = targetStrictMode
                    )
                    m = applyModelCompatPatch(m, patch)
                }
            }
        }

        return Pair(p, m)
    }

    /**
     * Merge two compat configs, with patch values overriding base values.
     * null patch values do not override existing base values.
     */
    private fun mergeCompat(base: ModelCompatConfig?, patch: ModelCompatConfig): ModelCompatConfig {
        if (base == null) return patch
        return ModelCompatConfig(
            supportsStore = patch.supportsStore ?: base.supportsStore,
            supportsReasoningEffort = patch.supportsReasoningEffort ?: base.supportsReasoningEffort,
            maxTokensField = patch.maxTokensField ?: base.maxTokensField,
            thinkingFormat = patch.thinkingFormat ?: base.thinkingFormat,
            requiresToolResultName = patch.requiresToolResultName ?: base.requiresToolResultName,
            requiresAssistantAfterToolResult = patch.requiresAssistantAfterToolResult ?: base.requiresAssistantAfterToolResult,
            toolSchemaProfile = patch.toolSchemaProfile ?: base.toolSchemaProfile,
            nativeWebSearchTool = patch.nativeWebSearchTool ?: base.nativeWebSearchTool,
            toolCallArgumentsEncoding = patch.toolCallArgumentsEncoding ?: base.toolCallArgumentsEncoding,
            supportsDeveloperRole = patch.supportsDeveloperRole ?: base.supportsDeveloperRole,
            supportsUsageInStreaming = patch.supportsUsageInStreaming ?: base.supportsUsageInStreaming,
            supportsStrictMode = patch.supportsStrictMode ?: base.supportsStrictMode
        )
    }
}
