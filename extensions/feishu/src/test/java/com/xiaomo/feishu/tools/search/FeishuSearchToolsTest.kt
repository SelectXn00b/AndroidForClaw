package com.xiaomo.feishu.tools.search

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuSearchToolsTest : FeishuToolTestBase() {

    private lateinit var tool: FeishuSearchDocWikiTool

    @Before
    fun setUp() {
        tool = FeishuSearchDocWikiTool(config, client)
    }

    @Test
    fun `tool name and enabled`() {
        assertEquals("feishu_search_doc_wiki", tool.name)
        assertTrue(tool.isEnabled())

        val disabled = FeishuSearchDocWikiTool(createDefaultConfig(enableSearchTools = false), client)
        assertFalse(disabled.isEnabled())
    }

    @Test
    fun `search with query calls correct API`() = runTest {
        val data = JsonObject().apply {
            addProperty("total", 1)
            addProperty("has_more", false)
            add("res_units", jsonArr(jsonObj("title" to "Test Doc")))
        }
        mockPost("/open-apis/search/v2/doc_wiki/search", data)

        val result = tool.execute(mapOf("query" to "test keyword"))
        assertTrue(result.success)
        coVerify { client.post(match { it.contains("search/v2/doc_wiki/search") }, any(), any()) }
    }

    @Test
    fun `search without query defaults to empty string`() = runTest {
        val data = JsonObject().apply {
            addProperty("total", 0)
            addProperty("has_more", false)
            add("res_units", jsonArr())
        }
        mockPost("/open-apis/search/v2/doc_wiki/search", data)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
    }

    @Test
    fun `unknown action returns error`() = runTest {
        val result = tool.execute(mapOf("action" to "delete"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown action"))
    }

    @Test
    fun `search passes page_size and page_token`() = runTest {
        val data = JsonObject().apply {
            addProperty("total", 0)
            addProperty("has_more", false)
            add("res_units", jsonArr())
        }
        mockPost("/open-apis/search/v2/doc_wiki/search", data)

        val bodySlot = slot<Any>()
        tool.execute(mapOf("query" to "test", "page_size" to 20, "page_token" to "pt_abc"))
        coVerify { client.post(any(), capture(bodySlot), any()) }

        @Suppress("UNCHECKED_CAST")
        val body = bodySlot.captured as Map<String, Any?>
        assertEquals(20, body["page_size"])
        assertEquals("pt_abc", body["page_token"])
    }

    @Test
    fun `search with filter applies to both doc_filter and wiki_filter`() = runTest {
        val data = JsonObject().apply {
            addProperty("total", 0)
            addProperty("has_more", false)
            add("res_units", jsonArr())
        }
        mockPost("/open-apis/search/v2/doc_wiki/search", data)

        val bodySlot = slot<Any>()
        tool.execute(mapOf(
            "query" to "test",
            "filter" to mapOf(
                "doc_types" to listOf("DOC", "DOCX"),
                "only_title" to true
            )
        ))
        coVerify { client.post(any(), capture(bodySlot), any()) }

        @Suppress("UNCHECKED_CAST")
        val body = bodySlot.captured as Map<String, Any?>
        assertNotNull(body["doc_filter"])
        assertNotNull(body["wiki_filter"])
    }

    @Test
    fun `search without filter sends empty filters`() = runTest {
        val data = JsonObject().apply {
            addProperty("total", 0)
            addProperty("has_more", false)
            add("res_units", jsonArr())
        }
        mockPost("/open-apis/search/v2/doc_wiki/search", data)

        val bodySlot = slot<Any>()
        tool.execute(mapOf("query" to "test"))
        coVerify { client.post(any(), capture(bodySlot), any()) }

        @Suppress("UNCHECKED_CAST")
        val body = bodySlot.captured as Map<String, Any?>
        assertNotNull(body["doc_filter"])
        assertNotNull(body["wiki_filter"])
    }

    @Test
    fun `search handles API failure`() = runTest {
        mockPostError("/open-apis/search/v2/doc_wiki/search", "search failed")

        val result = tool.execute(mapOf("query" to "test"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("search failed"))
    }

    @Test
    fun `tool definition has correct schema`() {
        val def = tool.getToolDefinition()
        assertEquals("feishu_search_doc_wiki", def.function.name)
        assertTrue(def.function.parameters.properties.containsKey("query"))
        assertTrue(def.function.parameters.properties.containsKey("filter"))
        assertTrue(def.function.parameters.properties.containsKey("page_size"))
        assertTrue(def.function.parameters.properties.containsKey("page_token"))
    }

    @Test
    fun `aggregator returns tool`() {
        val agg = FeishuSearchTools(config, client)
        assertEquals(1, agg.getAllTools().size)
        assertEquals(1, agg.getToolDefinitions().size)
    }
}
