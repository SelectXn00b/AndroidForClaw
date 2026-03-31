package com.xiaomo.feishu.tools.bitable

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuBitableToolsTest : FeishuToolTestBase() {

    private lateinit var appTool: FeishuBitableAppTool
    private lateinit var tableTool: FeishuBitableAppTableTool
    private lateinit var fieldTool: FeishuBitableAppTableFieldTool
    private lateinit var recordTool: FeishuBitableAppTableRecordTool
    private lateinit var viewTool: FeishuBitableAppTableViewTool

    @Before
    fun setUp() {
        appTool = FeishuBitableAppTool(config, client)
        tableTool = FeishuBitableAppTableTool(config, client)
        fieldTool = FeishuBitableAppTableFieldTool(config, client)
        recordTool = FeishuBitableAppTableRecordTool(config, client)
        viewTool = FeishuBitableAppTableViewTool(config, client)
    }

    // ─── App Tool ────────────────────────────────────────────

    @Test
    fun `app tool name and enabled`() {
        assertEquals("feishu_bitable_app", appTool.name)
        assertTrue(appTool.isEnabled())
    }

    @Test
    fun `app missing action returns error`() = runTest {
        val result = appTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `app create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("app", jsonObj("app_token" to "bascnXXX"))
        }
        mockPost("/open-apis/bitable/v1/apps", data)

        val result = appTool.execute(mapOf("action" to "create", "name" to "Test Table"))
        assertTrue(result.success)
        coVerify { client.post(match { it.contains("/bitable/v1/apps") }, any(), any()) }
    }

    @Test
    fun `app create missing name returns error`() = runTest {
        val result = appTool.execute(mapOf("action" to "create"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("name"))
    }

    @Test
    fun `app get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("app", jsonObj("app_token" to "bascnXXX", "name" to "My Table"))
        }
        mockGet("/open-apis/bitable/v1/apps/bascnXXX", data)

        val result = appTool.execute(mapOf("action" to "get", "app_token" to "bascnXXX"))
        assertTrue(result.success)
    }

    @Test
    fun `app list calls drive API`() = runTest {
        val data = JsonObject().apply {
            add("files", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/drive/v1/files", data)

        val result = appTool.execute(mapOf("action" to "list"))
        assertTrue(result.success)
        coVerify { client.get(match { it.contains("/drive/v1/files") }, any()) }
    }

    @Test
    fun `app patch calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("app", jsonObj("name" to "Updated"))
        }
        mockPatch("/open-apis/bitable/v1/apps/bascnXXX", data)

        val result = appTool.execute(mapOf(
            "action" to "patch", "app_token" to "bascnXXX", "name" to "Updated"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `app copy calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("app", jsonObj("app_token" to "bascnYYY"))
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/copy", data)

        val result = appTool.execute(mapOf(
            "action" to "copy", "app_token" to "bascnXXX", "name" to "Copy"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `app handles API failure`() = runTest {
        mockPostError("/open-apis/bitable/v1/apps", "network error")

        val result = appTool.execute(mapOf("action" to "create", "name" to "Test"))
        assertFalse(result.success)
    }

    // ─── Table Tool ──────────────────────────────────────────

    @Test
    fun `table tool name`() {
        assertEquals("feishu_bitable_app_table", tableTool.name)
    }

    @Test
    fun `table create calls correct API`() = runTest {
        val data = JsonObject().apply {
            addProperty("table_id", "tblXXX")
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/tables", data)

        val result = tableTool.execute(mapOf(
            "action" to "create",
            "app_token" to "bascnXXX",
            "table" to mapOf("name" to "Sheet1")
        ))
        assertTrue(result.success)
    }

    @Test
    fun `table list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/bitable/v1/apps/bascnXXX/tables", data)

        val result = tableTool.execute(mapOf("action" to "list", "app_token" to "bascnXXX"))
        assertTrue(result.success)
    }

    @Test
    fun `table missing app_token returns error`() = runTest {
        val result = tableTool.execute(mapOf("action" to "list"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("app_token"))
    }

    // ─── Field Tool ──────────────────────────────────────────

    @Test
    fun `field tool name`() {
        assertEquals("feishu_bitable_app_table_field", fieldTool.name)
    }

    @Test
    fun `field create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("field", jsonObj("field_id" to "fldXXX"))
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/fields", data)

        val result = fieldTool.execute(mapOf(
            "action" to "create",
            "app_token" to "bascnXXX",
            "table_id" to "tblXXX",
            "field_name" to "Status",
            "type" to 1
        ))
        assertTrue(result.success)
    }

    @Test
    fun `field list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/fields", data)

        val result = fieldTool.execute(mapOf(
            "action" to "list", "app_token" to "bascnXXX", "table_id" to "tblXXX"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `field delete calls correct API`() = runTest {
        mockDelete("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/fields/fldXXX")

        val result = fieldTool.execute(mapOf(
            "action" to "delete",
            "app_token" to "bascnXXX",
            "table_id" to "tblXXX",
            "field_id" to "fldXXX"
        ))
        assertTrue(result.success)
    }

    // ─── Record Tool ─────────────────────────────────────────

    @Test
    fun `record tool name`() {
        assertEquals("feishu_bitable_app_table_record", recordTool.name)
    }

    @Test
    fun `record create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("record", jsonObj("record_id" to "recXXX"))
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/records", data)

        val result = recordTool.execute(mapOf(
            "action" to "create",
            "app_token" to "bascnXXX",
            "table_id" to "tblXXX",
            "fields" to mapOf("Name" to "Alice")
        ))
        assertTrue(result.success)
    }

    @Test
    fun `record list calls search API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
            addProperty("total", 0)
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/records/search", data)

        val result = recordTool.execute(mapOf(
            "action" to "list", "app_token" to "bascnXXX", "table_id" to "tblXXX"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `record batch_create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("records", jsonArr())
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/records/batch_create", data)

        val result = recordTool.execute(mapOf(
            "action" to "batch_create",
            "app_token" to "bascnXXX",
            "table_id" to "tblXXX",
            "records" to listOf(mapOf("fields" to mapOf("Name" to "Bob")))
        ))
        assertTrue(result.success)
    }

    @Test
    fun `record missing app_token returns error`() = runTest {
        val result = recordTool.execute(mapOf("action" to "list"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("app_token"))
    }

    // ─── View Tool ───────────────────────────────────────────

    @Test
    fun `view tool name`() {
        assertEquals("feishu_bitable_app_table_view", viewTool.name)
    }

    @Test
    fun `view create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("view", jsonObj("view_id" to "vewXXX"))
        }
        mockPost("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/views", data)

        val result = viewTool.execute(mapOf(
            "action" to "create",
            "app_token" to "bascnXXX",
            "table_id" to "tblXXX",
            "view_name" to "Grid View"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `view list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/views", data)

        val result = viewTool.execute(mapOf(
            "action" to "list", "app_token" to "bascnXXX", "table_id" to "tblXXX"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `view get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("view", jsonObj("view_id" to "vewXXX"))
        }
        mockGet("/open-apis/bitable/v1/apps/bascnXXX/tables/tblXXX/views/vewXXX", data)

        val result = viewTool.execute(mapOf(
            "action" to "get",
            "app_token" to "bascnXXX",
            "table_id" to "tblXXX",
            "view_id" to "vewXXX"
        ))
        assertTrue(result.success)
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns all 5 tools`() {
        val agg = FeishuBitableTools(config, client)
        assertEquals(5, agg.getAllTools().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuBitableTools(createDefaultConfig(enableBitableTools = false), client)
        assertEquals(5, agg.getAllTools().size)
        assertEquals(0, agg.getToolDefinitions().size)
    }

    @Test
    fun `all tools have valid definitions`() {
        val agg = FeishuBitableTools(config, client)
        for (tool in agg.getAllTools()) {
            val def = tool.getToolDefinition()
            assertTrue(def.function.name.startsWith("feishu_bitable_"))
            assertTrue(def.function.parameters.required.contains("action"))
        }
    }

    companion object {
        private const val TAG = "FeishuBitableToolsTest"
    }
}
