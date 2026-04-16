package com.xiaomo.hermes.providers

import com.xiaomo.hermes.config.ModelApi
import com.xiaomo.hermes.config.ModelCompatConfig
import com.xiaomo.hermes.config.ModelDefinition
import com.xiaomo.hermes.config.ProviderConfig
import org.junit.Assert.*
import org.junit.Test

class ModelCompatTest {

    // ===== applyXaiModelCompat =====

    @Test
    fun `xai compat sets toolSchemaProfile`() {
        val model = makeModel()
        val result = ModelCompat.applyXaiModelCompat(model)
        assertEquals(ModelCompat.XAI_TOOL_SCHEMA_PROFILE, result.compat?.toolSchemaProfile)
    }

    @Test
    fun `xai compat sets nativeWebSearchTool`() {
        val model = makeModel()
        val result = ModelCompat.applyXaiModelCompat(model)
        assertTrue(result.compat?.nativeWebSearchTool == true)
    }

    @Test
    fun `xai compat sets toolCallArgumentsEncoding`() {
        val model = makeModel()
        val result = ModelCompat.applyXaiModelCompat(model)
        assertEquals(ModelCompat.HTML_ENTITY_TOOL_CALL_ARGUMENTS_ENCODING, result.compat?.toolCallArgumentsEncoding)
    }

    @Test
    fun `xai compat preserves existing compat fields`() {
        val model = makeModel(compat = ModelCompatConfig(supportsStore = true))
        val result = ModelCompat.applyXaiModelCompat(model)
        assertTrue(result.compat?.supportsStore == true)
        assertEquals(ModelCompat.XAI_TOOL_SCHEMA_PROFILE, result.compat?.toolSchemaProfile)
    }

    // ===== usesXaiToolSchemaProfile =====

    @Test
    fun `xai profile detected`() {
        assertTrue(ModelCompat.usesXaiToolSchemaProfile(ModelCompatConfig(toolSchemaProfile = "xai")))
    }

    @Test
    fun `null compat returns false`() {
        assertFalse(ModelCompat.usesXaiToolSchemaProfile(null))
    }

    @Test
    fun `other profile returns false`() {
        assertFalse(ModelCompat.usesXaiToolSchemaProfile(ModelCompatConfig(toolSchemaProfile = "other")))
    }

    // ===== hasNativeWebSearchTool =====

    @Test
    fun `native web search true`() {
        assertTrue(ModelCompat.hasNativeWebSearchTool(ModelCompatConfig(nativeWebSearchTool = true)))
    }

    @Test
    fun `native web search false`() {
        assertFalse(ModelCompat.hasNativeWebSearchTool(ModelCompatConfig(nativeWebSearchTool = false)))
    }

    @Test
    fun `native web search null compat`() {
        assertFalse(ModelCompat.hasNativeWebSearchTool(null))
    }

    // ===== resolveToolCallArgumentsEncoding =====

    @Test
    fun `resolves encoding when set`() {
        assertEquals(
            "html-entities",
            ModelCompat.resolveToolCallArgumentsEncoding(ModelCompatConfig(toolCallArgumentsEncoding = "html-entities"))
        )
    }

    @Test
    fun `null when not set`() {
        assertNull(ModelCompat.resolveToolCallArgumentsEncoding(ModelCompatConfig()))
    }

    @Test
    fun `null for null compat`() {
        assertNull(ModelCompat.resolveToolCallArgumentsEncoding(null))
    }

    // ===== normalizeModelCompat =====

    @Test
    fun `xai provider auto-applies xai compat`() {
        val provider = makeProvider(api = ModelApi.OPENAI_COMPLETIONS, baseUrl = "https://api.x.ai/v1")
        val model = makeModel()
        val (_, resultModel) = ModelCompat.normalizeModelCompat(provider, model, "xai")
        assertEquals(ModelCompat.XAI_TOOL_SCHEMA_PROFILE, resultModel.compat?.toolSchemaProfile)
    }

    @Test
    fun `x-ai provider also applies xai compat`() {
        val provider = makeProvider(api = ModelApi.OPENAI_COMPLETIONS, baseUrl = "https://api.x.ai/v1")
        val model = makeModel()
        val (_, resultModel) = ModelCompat.normalizeModelCompat(provider, model, "x-ai")
        assertTrue(resultModel.compat?.nativeWebSearchTool == true)
    }

    @Test
    fun `anthropic baseUrl strips trailing v1`() {
        val provider = makeProvider(api = ModelApi.ANTHROPIC_MESSAGES, baseUrl = "https://api.anthropic.com/v1")
        val model = makeModel(api = ModelApi.ANTHROPIC_MESSAGES)
        val (resultProvider, _) = ModelCompat.normalizeModelCompat(provider, model, "anthropic")
        assertEquals("https://api.anthropic.com", resultProvider.baseUrl)
    }

    @Test
    fun `anthropic baseUrl without v1 unchanged`() {
        val provider = makeProvider(api = ModelApi.ANTHROPIC_MESSAGES, baseUrl = "https://api.anthropic.com")
        val model = makeModel(api = ModelApi.ANTHROPIC_MESSAGES)
        val (resultProvider, _) = ModelCompat.normalizeModelCompat(provider, model, "anthropic")
        assertEquals("https://api.anthropic.com", resultProvider.baseUrl)
    }

    @Test
    fun `non-native openai endpoint disables developer role`() {
        val provider = makeProvider(api = ModelApi.OPENAI_COMPLETIONS, baseUrl = "https://api.deepseek.com/v1")
        val model = makeModel()
        val (_, resultModel) = ModelCompat.normalizeModelCompat(provider, model, "deepseek")
        assertFalse(resultModel.compat?.supportsDeveloperRole == true)
        assertFalse(resultModel.compat?.supportsUsageInStreaming == true)
        assertFalse(resultModel.compat?.supportsStrictMode == true)
    }

    @Test
    fun `native openai endpoint does not force disable`() {
        val provider = makeProvider(api = ModelApi.OPENAI_COMPLETIONS, baseUrl = "https://api.openai.com/v1")
        val model = makeModel()
        val (_, resultModel) = ModelCompat.normalizeModelCompat(provider, model, "openai")
        // Native endpoint: no forced compat changes for developerRole etc.
        // Model should remain unchanged (no compat patch applied for native OpenAI)
        assertNull(resultModel.compat)
    }

    @Test
    fun `applyModelCompatPatch merges without overwriting nulls`() {
        val model = makeModel(compat = ModelCompatConfig(supportsStore = true, supportsDeveloperRole = true))
        val patch = ModelCompatConfig(toolSchemaProfile = "xai")
        val result = ModelCompat.applyModelCompatPatch(model, patch)
        assertTrue(result.compat?.supportsStore == true)
        assertTrue(result.compat?.supportsDeveloperRole == true)
        assertEquals("xai", result.compat?.toolSchemaProfile)
    }

    // ===== Helpers =====

    private fun makeModel(
        id: String = "test-model",
        api: String? = null,
        compat: ModelCompatConfig? = null
    ) = ModelDefinition(
        id = id,
        name = id,
        api = api,
        compat = compat
    )

    private fun makeProvider(
        api: String = ModelApi.OPENAI_COMPLETIONS,
        baseUrl: String = "https://api.openai.com/v1"
    ) = ProviderConfig(
        baseUrl = baseUrl,
        api = api
    )
}
