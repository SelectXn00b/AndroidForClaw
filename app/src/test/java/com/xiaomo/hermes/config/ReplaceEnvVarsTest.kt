package com.xiaomo.hermes.config

import org.junit.Assert.*
import org.junit.Test

/**
 * 验证 replaceEnvVars 对未解析环境变量的处理。
 *
 * Bug 修复前: 如果 openclaw.json 中有 "apiKey": "${MOONSHOT_API_KEY}"
 * 而 Android 上没有这个环境变量，replaceEnvVars 会保留字面量
 * → apiKey 变成 "${MOONSHOT_API_KEY}" → Bearer token 发送失败 → 401。
 *
 * 修复后: 未解析的已知 provider 环境变量会被替换为 null，
 * 触发清晰的 "API Key 未配置" 提示。
 */
class ReplaceEnvVarsTest {

    /**
     * 模拟 ConfigLoader.replaceEnvVars 的剥离逻辑，
     * 验证已知 provider 环境变量未解析时被正确替换为 null。
     */
    private fun simulateStripUnresolvedEnvVars(json: String, unresolvedVars: List<String>): String {
        var result = json
        for (varName in unresolvedVars) {
            val stripPattern = Regex("""("apiKey"\s*:\s*)"\$\{$varName\}"""")
            result = stripPattern.replace(result) { "${it.groupValues[1]}null" }
        }
        return result
    }

    @Test
    fun `unresolved MOONSHOT_API_KEY is stripped to null`() {
        val json = """{"apiKey": "${'$'}{MOONSHOT_API_KEY}"}"""
        val result = simulateStripUnresolvedEnvVars(json, listOf("MOONSHOT_API_KEY"))
        // After stripping: {"apiKey": null}
        assertTrue("apiKey should be JSON null", result.contains("\"apiKey\": null"))
        assertFalse("placeholder should be removed", result.contains("MOONSHOT_API_KEY"))
    }

    @Test
    fun `unresolved KIMI_API_KEY is stripped to null`() {
        val json = """{"apiKey": "${'$'}{KIMI_API_KEY}"}"""
        val result = simulateStripUnresolvedEnvVars(json, listOf("KIMI_API_KEY"))
        assertTrue(result.contains("\"apiKey\": null"))
    }

    @Test
    fun `resolved env var is preserved`() {
        val json = """{"apiKey": "sk-real-key-12345"}"""
        val result = simulateStripUnresolvedEnvVars(json, emptyList())
        assertTrue("real key should be preserved", result.contains("sk-real-key-12345"))
    }

    @Test
    fun `multiple providers with mixed resolution`() {
        val json = """
            {
                "models": {
                    "providers": {
                        "openrouter": {
                            "baseUrl": "https://openrouter.ai/api/v1",
                            "apiKey": "sk-or-v1-real-key"
                        },
                        "moonshot": {
                            "baseUrl": "https://api.moonshot.ai/v1",
                            "apiKey": "${'$'}{MOONSHOT_API_KEY}"
                        },
                        "deepseek": {
                            "baseUrl": "https://api.deepseek.com/v1",
                            "apiKey": "${'$'}{DEEPSEEK_API_KEY}"
                        }
                    }
                }
            }
        """.trimIndent()

        val result = simulateStripUnresolvedEnvVars(json, listOf("MOONSHOT_API_KEY", "DEEPSEEK_API_KEY"))

        // OpenRouter key preserved
        assertTrue(result.contains("sk-or-v1-real-key"))
        // Moonshot and DeepSeek stripped to null
        assertFalse(result.contains("MOONSHOT_API_KEY"))
        assertFalse(result.contains("DEEPSEEK_API_KEY"))
        assertTrue(result.contains("\"apiKey\": null"))
    }

    @Test
    fun `env var with whitespace around colon is handled`() {
        val json = """{"apiKey" : "${'$'}{MOONSHOT_API_KEY}"}"""
        val result = simulateStripUnresolvedEnvVars(json, listOf("MOONSHOT_API_KEY"))
        assertTrue("apiKey should be null even with whitespace", result.contains("\"apiKey\" : null"))
    }

    @Test
    fun `non-apiKey fields with env vars are not stripped`() {
        val json = """{"baseUrl": "${'$'}{CUSTOM_BASE_URL}", "apiKey": "${'$'}{MOONSHOT_API_KEY}"}"""
        val result = simulateStripUnresolvedEnvVars(json, listOf("MOONSHOT_API_KEY"))
        // baseUrl env var should be preserved (only apiKey is stripped)
        assertTrue("baseUrl placeholder preserved", result.contains("CUSTOM_BASE_URL"))
        assertTrue("apiKey is null", result.contains("\"apiKey\": null"))
    }
}
