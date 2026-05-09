package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DelegateToolTest {

    @Before
    fun setUp() {
        _activeDelegateContext = null
    }

    @After
    fun tearDown() {
        _activeDelegateContext = null
    }

    @Test
    fun `DELEGATE_BLOCKED_TOOLS has four tool names`() {
        assertEquals(4, DELEGATE_BLOCKED_TOOLS.size)
        assertTrue("delegate_task" in DELEGATE_BLOCKED_TOOLS)
        assertTrue("delegate_status" in DELEGATE_BLOCKED_TOOLS)
        assertTrue("delegate_cancel" in DELEGATE_BLOCKED_TOOLS)
        assertTrue("spawn_agent" in DELEGATE_BLOCKED_TOOLS)
    }

    @Test
    fun `_EXCLUDED_TOOLSET_NAMES covers expected categories`() {
        assertTrue("debugging" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("safe" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("delegation" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("moa" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("rl" in _EXCLUDED_TOOLSET_NAMES)
        assertEquals(5, _EXCLUDED_TOOLSET_NAMES.size)
    }

    @Test
    fun `_SUBAGENT_TOOLSETS matches DEFAULT_TOOLSETS`() {
        assertEquals(listOf("terminal", "file", "web"), _SUBAGENT_TOOLSETS)
        assertEquals(_SUBAGENT_TOOLSETS, DEFAULT_TOOLSETS)
    }

    @Test
    fun `_TOOLSET_LIST_STR is single-quoted comma-joined`() {
        assertEquals("'terminal', 'file', 'web'", _TOOLSET_LIST_STR)
    }

    @Test
    fun `module constants match Python defaults`() {
        assertEquals(3, _DEFAULT_MAX_CONCURRENT_CHILDREN)
        assertEquals(2, MAX_DEPTH)
        assertEquals(50, DEFAULT_MAX_ITERATIONS)
        assertEquals(30, _HEARTBEAT_INTERVAL)
    }

    @Test
    fun `DELEGATE_TASK_SCHEMA has expected shape`() {
        assertEquals("object", DELEGATE_TASK_SCHEMA["type"])
        @Suppress("UNCHECKED_CAST")
        val props = DELEGATE_TASK_SCHEMA["properties"] as Map<String, Any>
        assertTrue("goal" in props)
        assertTrue("toolsets" in props)
        @Suppress("UNCHECKED_CAST")
        val required = DELEGATE_TASK_SCHEMA["required"] as List<String>
        assertEquals(listOf("goal"), required)
    }

    // TC-TOOL-DelegateTool: checkDelegateRequirements returns true (delegation is available)
    @Test
    fun `checkDelegateRequirements returns true`() {
        assertTrue(checkDelegateRequirements())
    }

    @Test
    fun `_getMaxConcurrentChildren returns default when env unset`() {
        val n = _getMaxConcurrentChildren()
        assertTrue(n >= 1)
    }

    @Test
    fun `_buildChildSystemPrompt includes goal`() {
        val prompt = _buildChildSystemPrompt(goal = "Solve X")
        assertTrue(prompt.contains("YOUR TASK:"))
        assertTrue(prompt.contains("Solve X"))
        assertTrue(prompt.contains("focused subagent"))
    }

    @Test
    fun `_buildChildSystemPrompt includes context when provided`() {
        val prompt = _buildChildSystemPrompt(goal = "X", context = "Background info here")
        assertTrue(prompt.contains("CONTEXT:"))
        assertTrue(prompt.contains("Background info here"))
    }

    @Test
    fun `_buildChildSystemPrompt includes workspace hint when provided`() {
        val prompt = _buildChildSystemPrompt(goal = "X", workspaceHint = "/tmp/work")
        assertTrue(prompt.contains("WORKSPACE PATH:"))
        assertTrue(prompt.contains("/tmp/work"))
    }

    @Test
    fun `_buildChildSystemPrompt omits workspace when null`() {
        val prompt = _buildChildSystemPrompt(goal = "X")
        assertTrue(!prompt.contains("WORKSPACE PATH:"))
    }

    @Test
    fun `_stripBlockedTools drops delegation and memory toolset names`() {
        // Python behavior: filters by toolset names {"delegation", "clarify", "memory", "code_execution"}
        val input = listOf("terminal", "file", "delegation", "memory", "web", "clarify", "code_execution")
        val result = _stripBlockedTools(input)
        assertEquals(listOf("terminal", "file", "web"), result)
    }

    @Test
    fun `_stripBlockedTools preserves order and passes through unknown names`() {
        val input = listOf("custom1", "custom2")
        assertEquals(input, _stripBlockedTools(input))
    }

    @Test
    fun `_stripBlockedTools on empty list returns empty`() {
        assertEquals(emptyList<String>(), _stripBlockedTools(emptyList()))
    }

    @Test
    fun `_buildChildProgressCallback returns null on Android`() {
        assertNull(_buildChildProgressCallback(
            taskIndex = 0,
            goal = "x",
            parentAgent = null,
        ))
    }

    @Test
    fun `_buildChildAgent returns null when no DelegateContext`() {
        // No _activeDelegateContext set → returns null
        _activeDelegateContext = null
        assertNull(_buildChildAgent(goal = "x"))
    }

    @Test
    fun `_buildChildAgent returns null when depth exceeds MAX_DEPTH`() {
        _activeDelegateContext = DelegateContext(
            server = FakeServer(),
            toolDispatcher = FakeDispatcher(),
            toolSchemas = emptyList(),
            validToolNames = setOf("terminal"),
            depth = MAX_DEPTH,  // at limit
            taskId = "test",
        )
        assertNull(_buildChildAgent(goal = "x"))
    }

    @Test
    fun `_buildChildAgent returns loop when context is valid`() {
        _activeDelegateContext = DelegateContext(
            server = FakeServer(),
            toolDispatcher = FakeDispatcher(),
            toolSchemas = listOf(
                mapOf("function" to mapOf("name" to "terminal", "parameters" to emptyMap<String, Any>())),
                mapOf("function" to mapOf("name" to "delegate_task", "parameters" to emptyMap<String, Any>())),
                mapOf("function" to mapOf("name" to "spawn_agent", "parameters" to emptyMap<String, Any>())),
            ),
            validToolNames = setOf("terminal", "delegate_task", "spawn_agent", "file"),
            depth = 0,
            taskId = "parent-1",
        )
        val child = _buildChildAgent(goal = "do something", maxIterations = 10)
        assertNotNull(child)
        // Child should NOT have delegate_task or spawn_agent in validToolNames
        assertTrue("terminal" in child!!.validToolNames)
        assertTrue("delegate_task" !in child.validToolNames)
        assertTrue("spawn_agent" !in child.validToolNames)
        assertEquals(10, child.maxTurns)
        assertTrue(child.taskId.contains("sub"))
    }

    // TC-TOOL-DelegateTool: delegateTask without context returns error
    @Test
    fun `delegateTask without DelegateContext returns error`() = runBlocking {
        _activeDelegateContext = null
        val result = delegateTask(goal = "test")
        assertTrue(result.contains("requires a parent agent context"))
    }

    // TC-TOOL-DelegateTool: delegateTask with empty goal returns error
    @Test
    fun `delegateTask rejects empty goal`() = runBlocking {
        _activeDelegateContext = DelegateContext(
            server = FakeServer(),
            toolDispatcher = FakeDispatcher(),
            toolSchemas = emptyList(),
            validToolNames = emptySet(),
            depth = 0,
            taskId = "t",
        )
        val result = delegateTask(goal = "   ")
        assertTrue(result.contains("'goal'") || result.contains("Provide either"))
    }

    // TC-TOOL-DelegateTool: delegateTask respects depth limit
    @Test
    fun `delegateTask at MAX_DEPTH returns depth error`() = runBlocking {
        _activeDelegateContext = DelegateContext(
            server = FakeServer(),
            toolDispatcher = FakeDispatcher(),
            toolSchemas = emptyList(),
            validToolNames = emptySet(),
            depth = MAX_DEPTH,
            taskId = "t",
        )
        val result = delegateTask(goal = "real goal")
        assertTrue(result.contains("depth limit"))
    }

    @Test
    fun `_resolveChildCredentialPool returns null on Android`() {
        assertNull(_resolveChildCredentialPool(null, null))
        assertNull(_resolveChildCredentialPool("openrouter", Any()))
    }

    @Test
    fun `_resolveDelegationCredentials returns empty map on Android`() {
        // When cfg has no provider/base_url override, returns a map with null
        // values indicating "inherit from parent" (matches Python behavior).
        val result = _resolveDelegationCredentials(emptyMap(), null)
        assertNull(result["provider"])
        assertNull(result["base_url"])
        assertNull(result["api_key"])
        assertNull(result["api_mode"])
    }

    // ── Fakes for testing ────────────────────────────────────────────────────

    private class FakeServer : com.xiaomo.hermes.hermes.ChatCompletionServer {
        override suspend fun chatCompletion(
            messages: List<Map<String, Any?>>,
            tools: List<Map<String, Any?>>?,
            temperature: Double,
            maxTokens: Int?,
            extraBody: Map<String, Any?>?
        ): com.xiaomo.hermes.hermes.ChatCompletionResponse {
            // Return a simple response with no tool calls (agent finishes)
            return com.xiaomo.hermes.hermes.ChatCompletionResponse(
                choices = listOf(
                    com.xiaomo.hermes.hermes.Choice(
                        message = com.xiaomo.hermes.hermes.AssistantMessage(
                            content = "Task completed successfully.",
                            toolCalls = null,
                        )
                    )
                )
            )
        }
    }

    private class FakeDispatcher : com.xiaomo.hermes.hermes.ToolDispatcher {
        override suspend fun dispatch(
            toolName: String,
            args: Map<String, Any?>,
            taskId: String,
            userTask: String?
        ): String = """{"result": "ok"}"""
    }
}
