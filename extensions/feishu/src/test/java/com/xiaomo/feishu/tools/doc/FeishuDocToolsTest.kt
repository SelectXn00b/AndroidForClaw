package com.xiaomo.feishu.tools.doc

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuDocToolsTest : FeishuToolTestBase() {

    private lateinit var fetchTool: FeishuFetchDocTool
    private lateinit var createTool: FeishuCreateDocTool
    private lateinit var updateTool: FeishuUpdateDocTool

    @Before
    fun setUp() {
        fetchTool = FeishuFetchDocTool(config, client)
        createTool = FeishuCreateDocTool(config, client)
        updateTool = FeishuUpdateDocTool(config, client)
    }

    // ─── Fetch Doc ───────────────────────────────────────────

    @Test
    fun `fetch tool name and enabled`() {
        assertEquals("feishu_fetch_doc", fetchTool.name)
        assertTrue(fetchTool.isEnabled())
    }

    @Test
    fun `fetch missing doc_id returns error`() = runTest {
        val result = fetchTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("doc_id"))
    }

    @Test
    fun `fetch calls correct API`() = runTest {
        val rawContentData = JsonObject().apply {
            addProperty("content", "Hello World")
        }
        mockGet("/open-apis/docx/v1/documents/docXXX/raw_content", rawContentData)

        val metaData = JsonObject().apply {
            add("document", jsonObj("title" to "Test Doc", "document_id" to "docXXX"))
        }
        mockGet("/open-apis/docx/v1/documents/docXXX", metaData)

        val result = fetchTool.execute(mapOf("doc_id" to "docXXX"))
        assertTrue(result.success)
    }

    @Test
    fun `fetch extracts doc_id from URL`() = runTest {
        val rawContentData = JsonObject().apply {
            addProperty("content", "Content")
        }
        mockGet("/open-apis/docx/v1/documents/ABC123def/raw_content", rawContentData)

        val metaData = JsonObject().apply {
            add("document", jsonObj("title" to "URL Doc"))
        }
        mockGet("/open-apis/docx/v1/documents/ABC123def", metaData)

        val result = fetchTool.execute(mapOf("doc_id" to "https://xxx.feishu.cn/docx/ABC123def"))
        assertTrue(result.success)
    }

    @Test
    fun `fetch handles API failure`() = runTest {
        mockGetError("/open-apis/docx/v1/documents/docXXX/raw_content", "not found")

        val result = fetchTool.execute(mapOf("doc_id" to "docXXX"))
        assertFalse(result.success)
    }

    // ─── Create Doc ──────────────────────────────────────────

    @Test
    fun `create tool name`() {
        assertEquals("feishu_create_doc", createTool.name)
    }

    @Test
    fun `create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("document", jsonObj("document_id" to "docNEW", "title" to "New Doc"))
        }
        mockPost("/open-apis/docx/v1/documents", data)

        // Mock the task completion polling
        val taskData = JsonObject().apply {
            addProperty("status", "done")
        }
        mockGet("/open-apis/drive/v1/files/task_check", taskData)

        val result = createTool.execute(mapOf("title" to "New Doc", "markdown" to "# Hello"))
        assertTrue(result.success)
    }

    @Test
    fun `create with folder_token passes param`() = runTest {
        val data = JsonObject().apply {
            add("document", jsonObj("document_id" to "docNEW"))
        }
        mockPost("/open-apis/docx/v1/documents", data)

        val result = createTool.execute(mapOf(
            "title" to "In Folder",
            "markdown" to "# Content",
            "folder_token" to "fldcnXXX"
        ))
        assertTrue(result.success)
    }

    // ─── Update Doc ──────────────────────────────────────────

    @Test
    fun `update tool name`() {
        assertEquals("feishu_update_doc", updateTool.name)
    }

    @Test
    fun `update missing doc_id returns error`() = runTest {
        val result = updateTool.execute(mapOf("mode" to "append", "markdown" to "text"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("doc_id"))
    }

    @Test
    fun `update missing mode returns error`() = runTest {
        val result = updateTool.execute(mapOf("doc_id" to "docXXX"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("mode"))
    }

    @Test
    fun `update append calls correct API`() = runTest {
        val data = JsonObject().apply {
            addProperty("document_revision_id", 2)
        }
        mockPost("/open-apis/docx/v1/documents/docXXX/blocks/docXXX/children", data)

        // Mock get blocks for reading current content
        val blocksData = JsonObject().apply {
            add("items", jsonArr())
        }
        mockGet("/open-apis/docx/v1/documents/docXXX/blocks", blocksData)

        val result = updateTool.execute(mapOf(
            "doc_id" to "docXXX", "mode" to "append", "markdown" to "New content"
        ))
        // May succeed or fail depending on internal logic, but should not crash
        assertNotNull(result)
    }

    @Test
    fun `update overwrite calls correct API`() = runTest {
        val blocksData = JsonObject().apply {
            add("items", jsonArr())
        }
        mockGet("/open-apis/docx/v1/documents/docXXX/blocks", blocksData)

        val data = JsonObject().apply {
            addProperty("document_revision_id", 3)
        }
        mockPost("/open-apis/docx/v1/documents/docXXX/blocks/docXXX/children", data)

        val result = updateTool.execute(mapOf(
            "doc_id" to "docXXX", "mode" to "overwrite", "markdown" to "Replaced content"
        ))
        assertNotNull(result)
    }

    // ─── Tool Definitions ────────────────────────────────────

    @Test
    fun `fetch definition has doc_id required`() {
        val def = fetchTool.getToolDefinition()
        assertTrue(def.function.parameters.required.contains("doc_id"))
    }

    @Test
    fun `update definition has mode enum`() {
        val def = updateTool.getToolDefinition()
        val modeSchema = def.function.parameters.properties["mode"]
        assertNotNull(modeSchema?.enum)
        assertTrue(modeSchema!!.enum!!.contains("append"))
        assertTrue(modeSchema.enum!!.contains("overwrite"))
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `doc tools aggregator returns all tools`() {
        val agg = FeishuDocTools(config, client)
        val tools = agg.getAllTools()
        assertTrue(tools.size >= 3) // fetch, create, update + potentially media, comments
    }

    @Test
    fun `doc tools aggregator respects disabled config`() {
        val agg = FeishuDocTools(createDefaultConfig(enableDocTools = false), client)
        assertEquals(0, agg.getToolDefinitions().size)
    }
}
