package com.xiaomo.feishu.tools.drive

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuDriveToolsTest : FeishuToolTestBase() {

    private lateinit var tool: FeishuDriveFileTool

    @Before
    fun setUp() {
        tool = FeishuDriveFileTool(config, client)
    }

    @Test
    fun `tool name and enabled`() {
        assertEquals("feishu_drive_file", tool.name)
        assertTrue(tool.isEnabled())

        val disabled = FeishuDriveFileTool(createDefaultConfig(enableDriveTools = false), client)
        assertFalse(disabled.isEnabled())
    }

    @Test
    fun `missing action returns error`() = runTest {
        val result = tool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `unknown action returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "rename"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown"))
    }

    // ─── List ────────────────────────────────────────────────

    @Test
    fun `list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("files", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/drive/v1/files", data)

        val result = tool.execute(mapOf("action" to "list"))
        assertTrue(result.success)
    }

    @Test
    fun `list with folder_token passes param`() = runTest {
        val data = JsonObject().apply {
            add("files", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/drive/v1/files", data)

        tool.execute(mapOf("action" to "list", "folder_token" to "fldcnXXX"))
        coVerify { client.get(match { it.contains("folder_token=fldcnXXX") }, any()) }
    }

    // ─── Get Meta ────────────────────────────────────────────

    @Test
    fun `get_meta calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("metas", jsonArr(jsonObj("token" to "docXXX", "title" to "Test")))
        }
        mockPost("/open-apis/drive/v1/metas/batch_query", data)

        val result = tool.execute(mapOf(
            "action" to "get_meta",
            "request_docs" to listOf(mapOf("doc_token" to "docXXX", "doc_type" to "docx"))
        ))
        assertTrue(result.success)
    }

    @Test
    fun `get_meta missing request_docs returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "get_meta"))
        // Validation errors are returned as success with error in data
        assertTrue(result.success)
        assertTrue(result.data.toString().contains("request_docs"))
    }

    // ─── Copy ────────────────────────────────────────────────

    @Test
    fun `copy calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("file", jsonObj("token" to "docNEW"))
        }
        mockPost("/open-apis/drive/v1/files/docXXX/copy", data)

        val result = tool.execute(mapOf(
            "action" to "copy",
            "file_token" to "docXXX",
            "name" to "Copy of Doc",
            "type" to "docx"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `copy missing file_token returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "copy", "name" to "Copy"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("file_token"))
    }

    // ─── Move ────────────────────────────────────────────────

    @Test
    fun `move calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("file", jsonObj("token" to "docXXX"))
        }
        mockPost("/open-apis/drive/v1/files/docXXX/move", data)

        val result = tool.execute(mapOf(
            "action" to "move",
            "file_token" to "docXXX",
            "type" to "docx",
            "folder_token" to "fldcnYYY"
        ))
        assertTrue(result.success)
    }

    // ─── Delete ──────────────────────────────────────────────

    @Test
    fun `delete calls correct API`() = runTest {
        mockDelete("/open-apis/drive/v1/files/docXXX")

        val result = tool.execute(mapOf(
            "action" to "delete",
            "file_token" to "docXXX",
            "type" to "docx"
        ))
        assertTrue(result.success)
    }

    // ─── Download ────────────────────────────────────────────

    @Test
    fun `download missing file_token returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "download"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("file_token"))
    }

    // ─── Upload ──────────────────────────────────────────────

    @Test
    fun `upload missing file_path and base64 returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "upload"))
        // Validation errors are returned as success with error in data
        assertTrue(result.success)
        assertTrue(result.data.toString().contains("file_path"))
    }

    // ─── API Failure ─────────────────────────────────────────

    @Test
    fun `list handles API failure`() = runTest {
        mockGetError("/open-apis/drive/v1/files", "network error")

        val result = tool.execute(mapOf("action" to "list"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("network error"))
    }

    // ─── Tool Definition ─────────────────────────────────────

    @Test
    fun `tool definition has correct schema`() {
        val def = tool.getToolDefinition()
        assertEquals("feishu_drive_file", def.function.name)
        assertTrue(def.function.parameters.required.contains("action"))
        val actionEnum = def.function.parameters.properties["action"]?.enum
        assertTrue(actionEnum!!.containsAll(listOf("list", "get_meta", "copy", "move", "delete", "upload", "download")))
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns tool`() {
        val agg = FeishuDriveTools(config, client)
        assertEquals(1, agg.getAllTools().size)
    }
}
