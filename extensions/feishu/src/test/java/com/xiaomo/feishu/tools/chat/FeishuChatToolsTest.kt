package com.xiaomo.feishu.tools.chat

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuChatToolsTest : FeishuToolTestBase() {

    private lateinit var chatTool: FeishuChatTool
    private lateinit var membersTool: FeishuChatMembersTool

    @Before
    fun setUp() {
        chatTool = FeishuChatTool(config, client)
        membersTool = FeishuChatMembersTool(config, client)
    }

    // ─── Chat Tool ───────────────────────────────────────────

    @Test
    fun `chat tool name and enabled`() {
        assertEquals("feishu_chat", chatTool.name)
        assertTrue(chatTool.isEnabled())

        val disabled = FeishuChatTool(createDefaultConfig(enableChatTools = false), client)
        assertFalse(disabled.isEnabled())
    }

    @Test
    fun `chat missing action returns error`() = runTest {
        val result = chatTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `chat unknown action returns error`() = runTest {
        val result = chatTool.execute(mapOf("action" to "delete"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Unknown"))
    }

    @Test
    fun `chat search calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr(jsonObj("chat_id" to "oc_123", "name" to "Test Group")))
            addProperty("has_more", false)
        }
        mockGet("/open-apis/im/v1/chats/search", data)

        val result = chatTool.execute(mapOf("action" to "search", "query" to "Test"))
        assertTrue(result.success)
        coVerify { client.get(match { it.contains("query=Test") }, any()) }
    }

    @Test
    fun `chat get calls correct API with custom header`() = runTest {
        val data = JsonObject().apply {
            addProperty("chat_id", "oc_123")
            addProperty("name", "Test Group")
        }
        mockGet("/open-apis/im/v1/chats/oc_123", data)

        val result = chatTool.execute(mapOf("action" to "get", "chat_id" to "oc_123"))
        assertTrue(result.success)
        coVerify {
            client.get(
                match { it.contains("/chats/oc_123") },
                match { it?.get("X-Chat-Custom-Header") == "enable_chat_list_security_check" }
            )
        }
    }

    @Test
    fun `chat get missing chat_id returns error`() = runTest {
        val result = chatTool.execute(mapOf("action" to "get"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("chat_id"))
    }

    @Test
    fun `chat search handles API failure`() = runTest {
        mockGetError("/open-apis/im/v1/chats/search", "rate limited")

        val result = chatTool.execute(mapOf("action" to "search", "query" to "test"))
        assertFalse(result.success)
    }

    @Test
    fun `chat tool definition`() {
        val def = chatTool.getToolDefinition()
        assertEquals("feishu_chat", def.function.name)
        assertTrue(def.function.parameters.required.contains("action"))
        val actionEnum = def.function.parameters.properties["action"]?.enum
        assertTrue(actionEnum!!.containsAll(listOf("search", "get")))
    }

    // ─── Chat Members Tool ───────────────────────────────────

    @Test
    fun `members tool name`() {
        assertEquals("feishu_chat_members", membersTool.name)
    }

    @Test
    fun `members missing chat_id returns error`() = runTest {
        val result = membersTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("chat_id"))
    }

    @Test
    fun `members calls correct API with custom header`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr(jsonObj("member_id" to "ou_123", "name" to "Alice")))
            addProperty("has_more", false)
        }
        mockGet("/open-apis/im/v1/chats/oc_123/members", data)

        val result = membersTool.execute(mapOf("chat_id" to "oc_123"))
        assertTrue(result.success)
        coVerify {
            client.get(
                match { it.contains("/chats/oc_123/members") },
                match { it?.get("X-Chat-Custom-Header") == "enable_chat_list_security_check" }
            )
        }
    }

    @Test
    fun `members handles API failure`() = runTest {
        mockGetError("/open-apis/im/v1/chats/oc_123/members", "forbidden")

        val result = membersTool.execute(mapOf("chat_id" to "oc_123"))
        assertFalse(result.success)
    }

    @Test
    fun `members tool definition`() {
        val def = membersTool.getToolDefinition()
        assertEquals("feishu_chat_members", def.function.name)
        assertTrue(def.function.parameters.required.contains("chat_id"))
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns both tools`() {
        val agg = FeishuChatTools(config, client)
        assertEquals(2, agg.getAllTools().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuChatTools(createDefaultConfig(enableChatTools = false), client)
        assertEquals(0, agg.getToolDefinitions().size)
    }
}
