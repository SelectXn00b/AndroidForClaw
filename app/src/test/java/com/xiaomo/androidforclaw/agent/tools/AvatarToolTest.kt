package com.xiaomo.androidforclaw.agent.tools

import ai.openclaw.app.avatar.AvatarStateHolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AvatarToolTest {

    private val tool = AvatarTool()

    @Before
    fun reset() {
        AvatarStateHolder.setMouthOpen(0f)
        AvatarStateHolder.setPaused(false)
        AvatarStateHolder.clearParamOverrides()
    }

    // ════════ Tool metadata ════════

    @Test
    fun `tool name is body`() {
        assertEquals("body", tool.name)
    }

    @Test
    fun `tool definition has correct parameters`() {
        val def = tool.getToolDefinition()
        assertEquals("body", def.function.name)
        val props = def.function.parameters.properties
        assertTrue(props.containsKey("action"))
        assertTrue(props.containsKey("expression"))
        assertTrue(props.containsKey("params"))
        assertEquals(listOf("action"), def.function.parameters.required)
    }

    @Test
    fun `action parameter has enum values`() {
        val def = tool.getToolDefinition()
        val actionSchema = def.function.parameters.properties["action"]!!
        assertEquals(
            listOf("status", "start", "stop", "pose", "trigger", "reset"),
            actionSchema.enum
        )
    }

    // ════════ status action ════════

    @Test
    fun `status returns body state`() = runTest {
        val result = tool.execute(mapOf("action" to "status"))
        assertTrue(result.success)
        assertTrue(result.content.contains("Body:"))
    }

    // ════════ stop action ════════

    @Test
    fun `stop pauses avatar`() = runTest {
        val result = tool.execute(mapOf("action" to "stop"))
        assertTrue(result.success)
        assertTrue(AvatarStateHolder.paused.value)
    }

    // ════════ pose action ════════

    @Test
    fun `pose sets param overrides`() = runTest {
        val params = mapOf<String, Any?>(
            "ParamAngleX" to 15.0,
            "ParamCheek" to 1.0
        )
        val result = tool.execute(mapOf("action" to "pose", "params" to params))
        assertTrue(result.success)
        val overrides = AvatarStateHolder.paramOverrides.value
        assertEquals(15f, overrides["ParamAngleX"]!!, 0.001f)
        assertEquals(1f, overrides["ParamCheek"]!!, 0.001f)
    }

    @Test
    fun `pose with empty params clears overrides`() = runTest {
        AvatarStateHolder.setParamOverrides(mapOf("ParamAngleX" to 10f))
        val result = tool.execute(mapOf("action" to "pose", "params" to emptyMap<String, Any?>()))
        assertTrue(result.success)
        assertTrue(AvatarStateHolder.paramOverrides.value.isEmpty())
    }

    @Test
    fun `pose without params clears overrides`() = runTest {
        AvatarStateHolder.setParamOverrides(mapOf("ParamAngleX" to 10f))
        val result = tool.execute(mapOf("action" to "pose"))
        assertTrue(result.success)
        assertTrue(AvatarStateHolder.paramOverrides.value.isEmpty())
    }

    // ════════ trigger action ════════

    @Test
    fun `trigger fires expression`() = runTest {
        val result = tool.execute(mapOf("action" to "trigger", "expression" to "smile"))
        assertTrue(result.success)
        assertTrue(result.content.contains("smile"))
    }

    @Test
    fun `trigger without expression returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "trigger"))
        assertFalse(result.success)
        assertTrue(result.content.contains("expression"))
    }

    // ════════ reset action ════════

    @Test
    fun `reset clears paused and overrides`() = runTest {
        AvatarStateHolder.setPaused(true)
        AvatarStateHolder.setParamOverrides(mapOf("ParamAngleX" to 15f))
        val result = tool.execute(mapOf("action" to "reset"))
        assertTrue(result.success)
        assertFalse(AvatarStateHolder.paused.value)
        assertTrue(AvatarStateHolder.paramOverrides.value.isEmpty())
    }

    // ════════ Error cases ════════

    @Test
    fun `missing action returns error`() = runTest {
        val result = tool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.content.contains("action"))
    }

    @Test
    fun `unknown action returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "dance"))
        assertFalse(result.success)
        assertTrue(result.content.contains("Unknown action"))
    }
}
