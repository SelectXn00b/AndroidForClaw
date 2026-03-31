package com.xiaomo.feishu.tools.common

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuCommonToolsTest : FeishuToolTestBase() {

    private lateinit var getUserTool: FeishuGetUserTool
    private lateinit var searchUserTool: FeishuSearchUserTool

    @Before
    fun setUp() {
        getUserTool = FeishuGetUserTool(config, client)
        searchUserTool = FeishuSearchUserTool(config, client)
    }

    // ─── feishu_get_user ─────────────────────────────────────

    @Test
    fun `get_user tool name and enabled`() {
        assertEquals("feishu_get_user", getUserTool.name)
        assertTrue(getUserTool.isEnabled())

        val disabled = FeishuGetUserTool(createDefaultConfig(enableCommonTools = false), client)
        assertFalse(disabled.isEnabled())
    }

    @Test
    fun `get_user without user_id fetches current user`() = runTest {
        val userData = jsonObj("name" to "Test User", "open_id" to "ou_123")
        mockGet("/open-apis/authen/v1/user_info", userData)

        val result = getUserTool.execute(emptyMap())
        assertTrue(result.success)
        coVerify { client.get(match { it.contains("/authen/v1/user_info") }, any()) }
    }

    @Test
    fun `get_user with user_id fetches specified user`() = runTest {
        val innerData = JsonObject().apply {
            add("user", jsonObj("name" to "Other", "open_id" to "ou_456"))
        }
        mockGet("/open-apis/contact/v3/users/ou_456", innerData)

        val result = getUserTool.execute(mapOf("user_id" to "ou_456"))
        assertTrue(result.success)
        coVerify { client.get(match { it.contains("/contact/v3/users/ou_456") }, any()) }
    }

    @Test
    fun `get_user with user_id_type passes correct param`() = runTest {
        val innerData = JsonObject().apply {
            add("user", jsonObj("name" to "Other"))
        }
        mockGet("/open-apis/contact/v3/users/uid_123", innerData)

        getUserTool.execute(mapOf("user_id" to "uid_123", "user_id_type" to "user_id"))
        coVerify { client.get(match { it.contains("user_id_type=user_id") }, any()) }
    }

    @Test
    fun `get_user handles API error`() = runTest {
        mockGetError("/open-apis/authen/v1/user_info", "token expired")

        val result = getUserTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("token expired"))
    }

    @Test
    fun `get_user handles 41050 error`() = runTest {
        mockGetError("/open-apis/authen/v1/user_info", "error code 41050")

        val result = getUserTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("无权限"))
    }

    @Test
    fun `get_user specified user 41050 error`() = runTest {
        mockGetError("/open-apis/contact/v3/users/ou_789", "41050")

        val result = getUserTool.execute(mapOf("user_id" to "ou_789"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("无权限"))
    }

    // ─── feishu_search_user ──────────────────────────────────

    @Test
    fun `search_user tool name and enabled`() {
        assertEquals("feishu_search_user", searchUserTool.name)
        assertTrue(searchUserTool.isEnabled())
    }

    @Test
    fun `search_user missing query returns error`() = runTest {
        val result = searchUserTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("query"))
    }

    @Test
    fun `search_user with query calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("users", jsonArr(jsonObj("name" to "Alice")))
            addProperty("has_more", false)
        }
        mockGet("/open-apis/search/v1/user", data)

        val result = searchUserTool.execute(mapOf("query" to "alice"))
        assertTrue(result.success)
        coVerify { client.get(match { it.contains("query=alice") }, any()) }
    }

    @Test
    fun `search_user passes page_size and page_token`() = runTest {
        val data = JsonObject().apply {
            add("users", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/search/v1/user", data)

        searchUserTool.execute(mapOf(
            "query" to "test",
            "page_size" to 50,
            "page_token" to "pt_abc"
        ))
        coVerify { client.get(match { it.contains("page_size=50") && it.contains("page_token=pt_abc") }, any()) }
    }

    @Test
    fun `search_user handles API failure`() = runTest {
        mockGetError("/open-apis/search/v1/user", "network error")

        val result = searchUserTool.execute(mapOf("query" to "test"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("network error"))
    }

    // ─── Tool definitions ────────────────────────────────────

    @Test
    fun `get_user definition has correct schema`() {
        val def = getUserTool.getToolDefinition()
        assertEquals("feishu_get_user", def.function.name)
        assertTrue(def.function.parameters.properties.containsKey("user_id"))
        assertTrue(def.function.parameters.properties.containsKey("user_id_type"))
        assertTrue(def.function.parameters.required.isEmpty())
    }

    @Test
    fun `search_user definition requires query`() {
        val def = searchUserTool.getToolDefinition()
        assertEquals("feishu_search_user", def.function.name)
        assertTrue(def.function.parameters.required.contains("query"))
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns both tools`() {
        val agg = FeishuCommonTools(config, client)
        assertEquals(2, agg.getAllTools().size)
        assertEquals(2, agg.getToolDefinitions().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuCommonTools(createDefaultConfig(enableCommonTools = false), client)
        assertEquals(2, agg.getAllTools().size)
        assertEquals(0, agg.getToolDefinitions().size)
    }
}
