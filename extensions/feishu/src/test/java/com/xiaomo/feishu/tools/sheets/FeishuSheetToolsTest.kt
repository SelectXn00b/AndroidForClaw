package com.xiaomo.feishu.tools.sheets

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuSheetToolsTest : FeishuToolTestBase() {

    private lateinit var tool: FeishuSheetTool

    @Before
    fun setUp() {
        tool = FeishuSheetTool(config, client)
    }

    @Test
    fun `tool name and enabled`() {
        assertEquals("feishu_sheet", tool.name)
        assertTrue(tool.isEnabled())

        val disabled = FeishuSheetTool(createDefaultConfig(enableSheetTools = false), client)
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
        val result = tool.execute(mapOf("action" to "delete"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown"))
    }

    @Test
    fun `info calls correct API`() = runTest {
        // info calls GET /open-apis/sheets/v3/spreadsheets/:id then GET sheets/query
        val spreadsheetData = JsonObject().apply {
            add("spreadsheet", jsonObj("title" to "My Sheet", "spreadsheet_token" to "shtcnXXX"))
        }
        mockGet("/open-apis/sheets/v3/spreadsheets/shtcnXXX", spreadsheetData)

        val sheetsData = JsonObject().apply {
            add("sheets", jsonArr(jsonObj("sheet_id" to "s1", "title" to "Sheet1")))
        }
        mockGet("/open-apis/sheets/v3/spreadsheets/shtcnXXX/sheets/query", sheetsData)

        val result = tool.execute(mapOf("action" to "info", "spreadsheet_token" to "shtcnXXX"))
        assertTrue(result.success)
    }

    @Test
    fun `info missing spreadsheet_token returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "info"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("spreadsheet_token"))
    }

    @Test
    fun `read calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("valueRange", JsonObject().apply {
                add("values", JsonArray())
            })
        }
        mockGet("/open-apis/sheets/v2/spreadsheets/shtcnXXX/values/", data)

        val result = tool.execute(mapOf(
            "action" to "read",
            "spreadsheet_token" to "shtcnXXX",
            "sheet_id" to "s1",
            "range" to "A1:C10"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `write calls correct API`() = runTest {
        val data = JsonObject().apply {
            addProperty("updatedCells", 6)
        }
        mockPut("/open-apis/sheets/v2/spreadsheets/shtcnXXX/values", data)

        val result = tool.execute(mapOf(
            "action" to "write",
            "spreadsheet_token" to "shtcnXXX",
            "sheet_id" to "s1",
            "range" to "A1:B2",
            "values" to listOf(listOf("a", "b"), listOf("c", "d"))
        ))
        assertTrue(result.success)
    }

    @Test
    fun `append calls correct API`() = runTest {
        val data = JsonObject().apply {
            addProperty("updatedCells", 3)
        }
        mockPost("/open-apis/sheets/v2/spreadsheets/shtcnXXX/values_append", data)

        val result = tool.execute(mapOf(
            "action" to "append",
            "spreadsheet_token" to "shtcnXXX",
            "sheet_id" to "s1",
            "values" to listOf(listOf("new", "row"))
        ))
        assertTrue(result.success)
    }

    @Test
    fun `find calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("find_result", JsonObject().apply {
                add("matched_cells", jsonArr())
            })
        }
        mockPost("/open-apis/sheets/v3/spreadsheets/shtcnXXX/sheets/s1/find", data)

        val result = tool.execute(mapOf(
            "action" to "find",
            "spreadsheet_token" to "shtcnXXX",
            "sheet_id" to "s1",
            "find" to "keyword"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("spreadsheet", jsonObj("spreadsheet_token" to "shtcnNEW", "title" to "New"))
        }
        mockPost("/open-apis/sheets/v3/spreadsheets", data)

        val result = tool.execute(mapOf("action" to "create", "title" to "New Sheet"))
        assertTrue(result.success)
    }

    @Test
    fun `info handles API failure`() = runTest {
        mockGetError("/open-apis/sheets/v3/spreadsheets/shtcnXXX", "not found")

        val result = tool.execute(mapOf("action" to "info", "spreadsheet_token" to "shtcnXXX"))
        assertFalse(result.success)
    }

    @Test
    fun `tool definition has correct schema`() {
        val def = tool.getToolDefinition()
        assertEquals("feishu_sheet", def.function.name)
        assertTrue(def.function.parameters.required.contains("action"))
        val actionEnum = def.function.parameters.properties["action"]?.enum
        assertTrue(actionEnum!!.contains("info"))
        assertTrue(actionEnum.contains("read"))
        assertTrue(actionEnum.contains("write"))
        assertTrue(actionEnum.contains("create"))
    }

    @Test
    fun `aggregator returns tool`() {
        val agg = FeishuSheetTools(config, client)
        assertEquals(1, agg.getAllTools().size)
    }
}
