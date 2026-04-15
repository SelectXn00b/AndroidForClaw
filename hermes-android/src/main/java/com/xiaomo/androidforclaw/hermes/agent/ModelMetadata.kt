package com.xiaomo.androidforclaw.hermes.agent

/**
 * Model Metadata - 模型能力元数据
 * 1:1 对齐 hermes/agent/model_metadata.py
 *
 * 探测/缓存模型的 context length、max output tokens 等
 */
class ModelMetadata {

    /** 模型元数据缓存 */
    private val cache: MutableMap<String, ModelInfo> = mutableMapOf()

    data class ModelInfo(
        val modelId: String,
        val contextLength: Int,
        val maxOutputTokens: Int,
        val provider: String,
        val supportsStreaming: Boolean = true,
        val supportsTools: Boolean = true
    )

    /**
     * 获取模型信息（优先从缓存读取）
     */
    fun getModelInfo(modelId: String): ModelInfo? {
        return cache[modelId] ?: fetchModelInfo(modelId)
    }

    /**
     * 获取模型的 context length
     */
    fun getContextLength(modelId: String): Int {
        return getModelInfo(modelId)?.contextLength ?: DEFAULT_CONTEXT_LENGTH
    }

    /**
     * 获取模型的 max output tokens
     */
    fun getMaxOutputTokens(modelId: String): Int {
        return getModelInfo(modelId)?.maxOutputTokens ?: DEFAULT_MAX_OUTPUT_TOKENS
    }

    /**
     * 从 API 获取模型信息
     */
    private fun fetchModelInfo(modelId: String): ModelInfo? {
        // TODO: 从 provider API 获取模型能力
        // 对应 Python model_metadata.py 的 fetch_model_info
        return null
    }

    /**
     * 注册已知模型信息
     */
    fun registerModel(info: ModelInfo) {
        cache[info.modelId] = info
    }

    /**
     * 列出所有已知模型
     */
    fun listModels(): List<ModelInfo> {
        return cache.values.toList()
    }

    companion object {
        const val DEFAULT_CONTEXT_LENGTH = 128_000
        const val DEFAULT_MAX_OUTPUT_TOKENS = 16_384

        /** 常见模型的默认配置 */
        val KNOWN_MODELS = mapOf(
            "claude-opus-4-6" to ModelInfo("claude-opus-4-6", 200_000, 32_768, "anthropic"),
            "claude-sonnet-4-20250514" to ModelInfo("claude-sonnet-4-20250514", 200_000, 16_384, "anthropic"),
            "gpt-4o" to ModelInfo("gpt-4o", 128_000, 16_384, "openai"),
            "gpt-5.1-codex-max" to ModelInfo("gpt-5.1-codex-max", 256_000, 32_768, "openai"),
            "gemini-2.5-pro" to ModelInfo("gemini-2.5-pro", 1_000_000, 65_536, "google"),
            "qwen3-8b" to ModelInfo("qwen3-8b", 32_768, 8_192, "local"), // RL training base
        )
    }


    // === Missing constants (auto-generated stubs) ===
    val _OLLAMA_TAG_PATTERN = Regex("")
    val _MODEL_CACHE_TTL = ""
    val _ENDPOINT_MODEL_CACHE_TTL = ""
    val CONTEXT_PROBE_TIERS = ""
    val DEFAULT_FALLBACK_CONTEXT = ""
    val MINIMUM_CONTEXT_LENGTH = ""
    val DEFAULT_CONTEXT_LENGTHS = ""
    val _CONTEXT_LENGTH_KEYS = emptySet<Any>()
    val _MAX_COMPLETION_KEYS = emptySet<Any>()
    val _LOCAL_HOSTS = ""
    val _CONTAINER_LOCAL_SUFFIXES = ""

    // === Missing methods (auto-generated stubs) ===
    private fun stripProviderPrefix(model: String): Unit {
    // Hermes: _strip_provider_prefix
}
}
