package com.xiaomo.androidforclaw.agent.context

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderModelAliasesTest {
    @Test
    fun contextBuilderSource_containsOpenClawModelAliases() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/context/ContextBuilder.kt").readText()
        assertTrue(source.contains("## Model Aliases"))
        assertTrue(source.contains("ClaudeOpus46: mify/ppio/pa/claude-opus-4-61"))
        assertTrue(source.contains("Codex: mify/azure_openai/gpt-5-codex"))
        assertTrue(source.contains("Gemini3: mify/vertex_ai/gemini-3-pro-preview"))
    }
}
