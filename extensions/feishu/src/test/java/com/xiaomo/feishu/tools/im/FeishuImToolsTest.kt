package com.xiaomo.feishu.tools.im

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuImToolsTest : FeishuToolTestBase() {

    private lateinit var messageTool: FeishuImUserMessageTool
    private lateinit var getMessagesTool: FeishuImUserGetMessagesTool
    private lateinit var getThreadMessagesTool: FeishuImUserGetThreadMessagesTool
    private lateinit var searchMessagesTool: FeishuImUserSearchMessagesTool
    private lateinit var fetchResourceTool: FeishuImUserFetchResourceTool
    private lateinit var botImageTool: FeishuImBotImageTool

    @Before
    fun setUp() {
        messageTool = FeishuImUserMessageTool(config, client)
        getMessagesTool = FeishuImUserGetMessagesTool(config, client)
        getThreadMessagesTool = FeishuImUserGetThreadMessagesTool(config, client)
        searchMessagesTool = FeishuImUserSearchMessagesTool(config, client)
        fetchResourceTool = FeishuImUserFetchResourceTool(config, client)
        botImageTool = FeishuImBotImageTool(config, client)
    }

    // ─── User Message Tool ───────────────────────────────────

    @Test
    fun `message tool name and enabled`() {
        assertEquals("feishu_im_user_message", messageTool.name)
        assertTrue(messageTool.isEnabled())
    }

    @Test
    fun `message missing action returns error`() = runTest {
        val result = messageTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `message send to chat_id calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("message", jsonObj("message_id" to "om_123"))
        }
        mockPost("/open-apis/im/v1/messages", data)

        val result = messageTool.execute(mapOf(
            "action" to "send",
            "receive_id" to "oc_123",
            "receive_id_type" to "chat_id",
            "content" to "Hello!",
            "msg_type" to "text"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `message send missing receive_id returns error`() = runTest {
        val result = messageTool.execute(mapOf("action" to "send"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("receive_id"))
    }

    @Test
    fun `message reply calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("message", jsonObj("message_id" to "om_456"))
        }
        mockPost("/open-apis/im/v1/messages/om_123/reply", data)

        val result = messageTool.execute(mapOf(
            "action" to "reply",
            "message_id" to "om_123",
            "content" to "Got it",
            "msg_type" to "text"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `message handles API failure`() = runTest {
        mockPostError("/open-apis/im/v1/messages", "rate limited")

        val result = messageTool.execute(mapOf(
            "action" to "send",
            "receive_id" to "oc_123",
            "receive_id_type" to "chat_id",
            "content" to "test",
            "msg_type" to "text"
        ))
        assertFalse(result.success)
    }

    // ─── Get Messages Tool ───────────────────────────────────

    @Test
    fun `get_messages tool name`() {
        assertEquals("feishu_im_user_get_messages", getMessagesTool.name)
    }

    @Test
    fun `get_messages missing chat_id and open_id returns error`() = runTest {
        val result = getMessagesTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("required") || result.error!!.contains("chat_id") || result.error!!.contains("open_id"))
    }

    @Test
    fun `get_messages calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/im/v1/messages", data)

        val result = getMessagesTool.execute(mapOf("chat_id" to "oc_123"))
        assertTrue(result.success)
    }

    // ─── Get Thread Messages Tool ────────────────────────────

    @Test
    fun `get_thread_messages tool name`() {
        assertEquals("feishu_im_user_get_thread_messages", getThreadMessagesTool.name)
    }

    @Test
    fun `get_thread_messages missing thread_id returns error`() = runTest {
        val result = getThreadMessagesTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("thread_id"))
    }

    @Test
    fun `get_thread_messages calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/im/v1/messages", data)

        val result = getThreadMessagesTool.execute(mapOf("thread_id" to "ot_123"))
        assertTrue(result.success)
        coVerify { client.get(match { it.contains("container_id_type=thread") }, any()) }
    }

    // ─── Search Messages Tool ────────────────────────────────

    @Test
    fun `search_messages tool name`() {
        assertEquals("feishu_im_user_search_messages", searchMessagesTool.name)
    }

    @Test
    fun `search_messages calls correct API`() = runTest {
        // Search API returns message IDs — mock broadly since URL has query params
        val searchData = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockPost("/open-apis/search/v2/message", searchData)

        val result = searchMessagesTool.execute(mapOf("query" to "hello"))
        assertTrue(result.success)
    }

    // ─── Fetch Resource Tool ─────────────────────────────────

    @Test
    fun `fetch_resource tool name`() {
        assertEquals("feishu_im_user_fetch_resource", fetchResourceTool.name)
    }

    @Test
    fun `fetch_resource missing message_id returns error`() = runTest {
        val result = fetchResourceTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("message_id"))
    }

    // ─── Bot Image Tool ──────────────────────────────────────

    @Test
    fun `bot_image tool name`() {
        assertEquals("feishu_im_bot_image", botImageTool.name)
    }

    @Test
    fun `bot_image missing message_id returns error`() = runTest {
        val result = botImageTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("message_id"))
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns all 6 tools`() {
        val agg = FeishuImTools(config, client)
        assertEquals(6, agg.getAllTools().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuImTools(createDefaultConfig(enableImTools = false), client)
        assertEquals(0, agg.getToolDefinitions().size)
    }

    @Test
    fun `all tools have valid definitions`() {
        val agg = FeishuImTools(config, client)
        for (tool in agg.getAllTools()) {
            val def = tool.getToolDefinition()
            assertTrue(def.function.name.startsWith("feishu_im_"))
            assertTrue(def.function.description.isNotBlank())
        }
    }
}
