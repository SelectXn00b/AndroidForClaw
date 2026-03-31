package com.xiaomo.feishu.tools.wiki

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuWikiToolsTest : FeishuToolTestBase() {

    private lateinit var spaceTool: FeishuWikiSpaceTool
    private lateinit var nodeTool: FeishuWikiSpaceNodeTool

    @Before
    fun setUp() {
        spaceTool = FeishuWikiSpaceTool(config, client)
        nodeTool = FeishuWikiSpaceNodeTool(config, client)
    }

    // ─── Wiki Space Tool ─────────────────────────────────────

    @Test
    fun `space tool name and enabled`() {
        assertEquals("feishu_wiki_space", spaceTool.name)
        assertTrue(spaceTool.isEnabled())

        val disabled = FeishuWikiSpaceTool(createDefaultConfig(enableWikiTools = false), client)
        assertFalse(disabled.isEnabled())
    }

    @Test
    fun `space missing action returns error`() = runTest {
        val result = spaceTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `space unknown action returns error`() = runTest {
        val result = spaceTool.execute(mapOf("action" to "delete"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown"))
    }

    @Test
    fun `space list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr(jsonObj("space_id" to "sp_123", "name" to "My Wiki")))
            addProperty("has_more", false)
        }
        mockGet("/open-apis/wiki/v2/spaces", data)

        val result = spaceTool.execute(mapOf("action" to "list"))
        assertTrue(result.success)
    }

    @Test
    fun `space list passes pagination params`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/wiki/v2/spaces", data)

        spaceTool.execute(mapOf("action" to "list", "page_size" to 20, "page_token" to "pt_abc"))
        coVerify { client.get(match { it.contains("page_size=20") && it.contains("page_token=pt_abc") }, any()) }
    }

    @Test
    fun `space get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("space", jsonObj("space_id" to "sp_123", "name" to "My Wiki"))
        }
        mockGet("/open-apis/wiki/v2/spaces/sp_123", data)

        val result = spaceTool.execute(mapOf("action" to "get", "space_id" to "sp_123"))
        assertTrue(result.success)
    }

    @Test
    fun `space get missing space_id returns error`() = runTest {
        val result = spaceTool.execute(mapOf("action" to "get"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("space_id"))
    }

    @Test
    fun `space create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("space", jsonObj("space_id" to "sp_new"))
        }
        mockPost("/open-apis/wiki/v2/spaces", data)

        val result = spaceTool.execute(mapOf("action" to "create", "name" to "New Wiki"))
        assertTrue(result.success)
    }

    @Test
    fun `space handles API failure`() = runTest {
        mockGetError("/open-apis/wiki/v2/spaces", "permission denied")

        val result = spaceTool.execute(mapOf("action" to "list"))
        assertFalse(result.success)
    }

    @Test
    fun `space tool definition`() {
        val def = spaceTool.getToolDefinition()
        assertEquals("feishu_wiki_space", def.function.name)
        assertTrue(def.function.parameters.required.contains("action"))
        val actionEnum = def.function.parameters.properties["action"]?.enum
        assertTrue(actionEnum!!.containsAll(listOf("list", "get", "create")))
    }

    // ─── Wiki Space Node Tool ────────────────────────────────

    @Test
    fun `node tool name and enabled`() {
        assertEquals("feishu_wiki_space_node", nodeTool.name)
        assertTrue(nodeTool.isEnabled())
    }

    @Test
    fun `node missing action returns error`() = runTest {
        val result = nodeTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `node list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr(jsonObj("node_token" to "nt_123")))
            addProperty("has_more", false)
        }
        mockGet("/open-apis/wiki/v2/spaces/sp_123/nodes", data)

        val result = nodeTool.execute(mapOf("action" to "list", "space_id" to "sp_123"))
        assertTrue(result.success)
    }

    @Test
    fun `node list missing space_id returns error`() = runTest {
        val result = nodeTool.execute(mapOf("action" to "list"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("space_id"))
    }

    @Test
    fun `node get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("node", jsonObj("node_token" to "nt_123", "obj_token" to "docXXX"))
        }
        mockGet("/open-apis/wiki/v2/spaces/get_node", data)

        val result = nodeTool.execute(mapOf("action" to "get", "token" to "nt_123"))
        assertTrue(result.success)
    }

    @Test
    fun `node get defaults obj_type to wiki`() = runTest {
        val data = JsonObject().apply {
            add("node", jsonObj("node_token" to "nt_123"))
        }
        mockGet("/open-apis/wiki/v2/spaces/get_node", data)

        nodeTool.execute(mapOf("action" to "get", "token" to "nt_123"))
        coVerify { client.get(match { it.contains("obj_type=wiki") }, any()) }
    }

    @Test
    fun `node get missing token returns error`() = runTest {
        val result = nodeTool.execute(mapOf("action" to "get"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("token"))
    }

    @Test
    fun `node create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("node", jsonObj("node_token" to "nt_new"))
        }
        mockPost("/open-apis/wiki/v2/spaces/sp_123/nodes", data)

        val result = nodeTool.execute(mapOf(
            "action" to "create",
            "space_id" to "sp_123",
            "obj_type" to "docx",
            "node_type" to "origin",
            "title" to "New Page"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `node move calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("node", jsonObj("node_token" to "nt_123"))
        }
        mockPost("/open-apis/wiki/v2/spaces/sp_123/nodes/nt_123/move", data)

        val result = nodeTool.execute(mapOf(
            "action" to "move",
            "space_id" to "sp_123",
            "node_token" to "nt_123",
            "target_parent_token" to "nt_parent"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `node copy calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("node", jsonObj("node_token" to "nt_copy"))
        }
        mockPost("/open-apis/wiki/v2/spaces/sp_123/nodes/nt_123/copy", data)

        val result = nodeTool.execute(mapOf(
            "action" to "copy",
            "space_id" to "sp_123",
            "node_token" to "nt_123",
            "title" to "Copy of Page"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `node handles API failure`() = runTest {
        mockGetError("/open-apis/wiki/v2/spaces/sp_123/nodes", "not found")

        val result = nodeTool.execute(mapOf("action" to "list", "space_id" to "sp_123"))
        assertFalse(result.success)
    }

    @Test
    fun `node tool definition`() {
        val def = nodeTool.getToolDefinition()
        assertEquals("feishu_wiki_space_node", def.function.name)
        val actionEnum = def.function.parameters.properties["action"]?.enum
        assertTrue(actionEnum!!.containsAll(listOf("list", "get", "create", "move", "copy")))
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns both tools`() {
        val agg = FeishuWikiTools(config, client)
        assertEquals(2, agg.getAllTools().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuWikiTools(createDefaultConfig(enableWikiTools = false), client)
        assertEquals(0, agg.getToolDefinitions().size)
    }
}
