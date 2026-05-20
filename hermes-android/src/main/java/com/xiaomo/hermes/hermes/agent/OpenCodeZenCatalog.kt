package com.xiaomo.hermes.hermes.agent

// 无 Python 上游：OpenCode Zen public-key 兜底属 Android-only 部署策略。
// 借鉴 sst/opencode TS 上游 packages/opencode/src/provider/provider.ts:160-182
// 的 apiKey="public" + cost.input==0 过滤语义。
// deep_align: ignore — Android-only feature, not part of 1:1 Python translation.
//
// Tied to: R-AGENT-002, TC-AGENT-200-{e,f,g}

/**
 * OpenCode Zen catalog: public-key fallback path that surfaces only the
 * free, tool-capable models from models.dev's `opencode` provider entry.
 *
 * Wired by app/OpenCodeZenDefaults to seed the default model config when
 * a fresh install has no provider key configured (replaces the deleted
 * BuiltInKeyProvider OpenRouter key path).
 */
object OpenCodeZenCatalog {

    /** ApiProviderType enum value (string-by-name in DataStore). */
    const val PROVIDER_ID: String = "opencode-zen"

    /** models.dev provider id (Hermes maps "opencode-zen" → "opencode"). */
    const val MODELS_DEV_PROVIDER_ID: String = "opencode"

    /**
     * Literal public key — this is a public, advertised value.
     * Not a secret; do not encrypt or obfuscate it.
     */
    const val PUBLIC_API_KEY: String = "public"

    /** OpenCode Zen Chat Completions endpoint. */
    const val DEFAULT_ENDPOINT: String = "https://opencode.ai/zen/v1/chat/completions"

    /**
     * Last-resort fallback model id used when the catalog is completely
     * unavailable (no network, no disk cache, no bundled snapshot).
     */
    const val BASELINE_FREE_MODEL: String = "qwen/qwen3-coder"

    /**
     * Filter the `opencode` provider's models down to free + tool-capable
     * + non-noise entries, sorted by release_date descending (newest first),
     * then by id for stable ordering.
     *
     * TC-AGENT-200-e: filters by cost.input==0 + tool_call==true + !NOISE_PATTERN
     */
    @Suppress("UNCHECKED_CAST")
    fun listFreeModels(catalog: Map<String, Any>): List<ModelInfo> {
        val provider = catalog[MODELS_DEV_PROVIDER_ID] as? Map<String, Any?> ?: return emptyList()
        val models = provider["models"] as? Map<String, Any?> ?: return emptyList()
        val out = mutableListOf<ModelInfo>()
        for ((id, raw) in models) {
            val m = raw as? Map<String, Any?> ?: continue
            val info = _parseModelInfo(id, m, MODELS_DEV_PROVIDER_ID)
            if (info.costInput != 0.0) continue
            if (!info.toolCall) continue
            if (ModelsDev.NOISE_PATTERN.containsMatchIn(id)) continue
            out += info
        }
        return out.sortedWith(
            compareByDescending<ModelInfo> { it.releaseDate.ifBlank { "0000-00-00" } }
                .thenBy { it.id }
        )
    }

    /**
     * Pick the default free model: latest release_date among free+tool-capable.
     * Falls back to BASELINE_FREE_MODEL when the catalog yields nothing.
     *
     * TC-AGENT-200-f: picks latest by release_date.
     * TC-AGENT-200-g: returns BASELINE_FREE_MODEL when catalog empty.
     */
    fun selectDefaultFreeModel(catalog: Map<String, Any>): String =
        listFreeModels(catalog).firstOrNull()?.id ?: BASELINE_FREE_MODEL
}
