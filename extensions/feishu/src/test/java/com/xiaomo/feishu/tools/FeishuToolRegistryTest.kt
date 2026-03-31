package com.xiaomo.feishu.tools

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuToolRegistryTest : FeishuToolTestBase() {

    private lateinit var registry: FeishuToolRegistry

    @Before
    fun setUp() {
        registry = FeishuToolRegistry(config, client)
    }

    @Test
    fun `getAllTools returns all registered tools`() {
        val tools = registry.getAllTools()
        assertTrue("Should have at least 30 tools", tools.size >= 30)
    }

    @Test
    fun `getTool finds existing tool by name`() {
        assertNotNull(registry.getTool("feishu_get_user"))
        assertNotNull(registry.getTool("feishu_wiki_space"))
        assertNotNull(registry.getTool("feishu_drive_file"))
    }

    @Test
    fun `getTool returns null for unknown name`() {
        assertNull(registry.getTool("nonexistent_tool"))
    }

    @Test
    fun `execute returns error for unknown tool`() = runTest {
        val result = registry.execute("nonexistent_tool", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("not found"))
    }

    @Test
    fun `execute returns error for disabled tool`() = runTest {
        val disabledConfig = createDefaultConfig(enableCommonTools = false)
        val reg = FeishuToolRegistry(disabledConfig, client)

        val result = reg.execute("feishu_get_user", emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("disabled"))
    }

    @Test
    fun `getToolDefinitions excludes disabled tools`() {
        val disabledConfig = createDefaultConfig(enableCommonTools = false)
        val reg = FeishuToolRegistry(disabledConfig, client)

        val defs = reg.getToolDefinitions()
        val names = defs.map { it.function.name }
        assertFalse(names.contains("feishu_get_user"))
        assertFalse(names.contains("feishu_search_user"))
    }

    @Test
    fun `getToolDefinitions includes enabled tools`() {
        val defs = registry.getToolDefinitions()
        val names = defs.map { it.function.name }
        assertTrue(names.contains("feishu_get_user"))
        assertTrue(names.contains("feishu_wiki_space"))
        assertTrue(names.contains("feishu_drive_file"))
    }

    @Test
    fun `getStats returns correct counts`() {
        val stats = registry.getStats()
        assertTrue(stats.totalTools >= 30)
        assertTrue(stats.enabledTools > 0)
        assertEquals(2, stats.toolsByCategory["common"])
        assertEquals(2, stats.toolsByCategory["wiki"])
    }

    @Test
    fun `all tool names are unique`() {
        val tools = registry.getAllTools()
        val names = tools.map { it.name }
        assertEquals("Tool names should be unique", names.size, names.toSet().size)
    }

    @Test
    fun `all tool definitions have valid schema`() {
        val defs = registry.getToolDefinitions()
        for (def in defs) {
            assertNotNull("name", def.function.name)
            assertTrue("name not blank: ${def.function.name}", def.function.name.isNotBlank())
            assertNotNull("description", def.function.description)
            assertTrue("description not blank: ${def.function.name}", def.function.description.isNotBlank())
            assertNotNull("parameters", def.function.parameters)
            assertEquals("object", def.function.parameters.type)
        }
    }
}
