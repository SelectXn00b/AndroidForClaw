package com.xiaomo.feishu.tools.task

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuTaskToolsTest : FeishuToolTestBase() {

    private lateinit var taskTool: FeishuTaskTaskTool
    private lateinit var tasklistTool: FeishuTaskTasklistTool
    private lateinit var subtaskTool: FeishuTaskSubtaskTool
    private lateinit var commentTool: FeishuTaskCommentTool

    @Before
    fun setUp() {
        taskTool = FeishuTaskTaskTool(config, client)
        tasklistTool = FeishuTaskTasklistTool(config, client)
        subtaskTool = FeishuTaskSubtaskTool(config, client)
        commentTool = FeishuTaskCommentTool(config, client)
    }

    // ─── Task Tool ───────────────────────────────────────────

    @Test
    fun `task tool name and enabled`() {
        assertEquals("feishu_task_task", taskTool.name)
        assertTrue(taskTool.isEnabled())
    }

    @Test
    fun `task missing action returns error`() = runTest {
        val result = taskTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `task create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("task", jsonObj("guid" to "task_123"))
        }
        mockPost("/open-apis/task/v2/tasks", data)

        val result = taskTool.execute(mapOf(
            "action" to "create", "summary" to "Test task"
        ))
        assertTrue(result.success)
        coVerify { client.post(match { it.contains("/task/v2/tasks") }, any(), any()) }
    }

    @Test
    fun `task create missing summary returns error`() = runTest {
        val result = taskTool.execute(mapOf("action" to "create"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("summary"))
    }

    @Test
    fun `task get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("task", jsonObj("guid" to "task_123", "summary" to "Test"))
        }
        mockGet("/open-apis/task/v2/tasks/task_123", data)

        val result = taskTool.execute(mapOf("action" to "get", "task_guid" to "task_123"))
        assertTrue(result.success)
    }

    @Test
    fun `task list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/task/v2/tasks", data)

        val result = taskTool.execute(mapOf("action" to "list"))
        assertTrue(result.success)
    }

    @Test
    fun `task patch calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("task", jsonObj("guid" to "task_123"))
        }
        mockPatch("/open-apis/task/v2/tasks/task_123", data)

        val result = taskTool.execute(mapOf(
            "action" to "patch", "task_guid" to "task_123", "summary" to "Updated"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `task handles API failure`() = runTest {
        mockPostError("/open-apis/task/v2/tasks", "permission denied")

        val result = taskTool.execute(mapOf("action" to "create", "summary" to "Test"))
        assertFalse(result.success)
    }

    // ─── Tasklist Tool ───────────────────────────────────────

    @Test
    fun `tasklist tool name`() {
        assertEquals("feishu_task_tasklist", tasklistTool.name)
    }

    @Test
    fun `tasklist create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("tasklist", jsonObj("guid" to "tl_123"))
        }
        mockPost("/open-apis/task/v2/tasklists", data)

        val result = tasklistTool.execute(mapOf("action" to "create", "name" to "My List"))
        assertTrue(result.success)
    }

    @Test
    fun `tasklist get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("tasklist", jsonObj("guid" to "tl_123"))
        }
        mockGet("/open-apis/task/v2/tasklists/tl_123", data)

        val result = tasklistTool.execute(mapOf("action" to "get", "tasklist_guid" to "tl_123"))
        assertTrue(result.success)
    }

    @Test
    fun `tasklist list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/task/v2/tasklists", data)

        val result = tasklistTool.execute(mapOf("action" to "list"))
        assertTrue(result.success)
    }

    @Test
    fun `tasklist tasks calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/task/v2/tasklists/tl_123/tasks", data)

        val result = tasklistTool.execute(mapOf("action" to "tasks", "tasklist_guid" to "tl_123"))
        assertTrue(result.success)
    }

    @Test
    fun `tasklist add_members calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("tasklist", jsonObj("guid" to "tl_123"))
        }
        mockPost("/open-apis/task/v2/tasklists/tl_123/add_members", data)

        val result = tasklistTool.execute(mapOf(
            "action" to "add_members",
            "tasklist_guid" to "tl_123",
            "members" to listOf(mapOf("id" to "ou_123"))
        ))
        assertTrue(result.success)
    }

    // ─── Subtask Tool ────────────────────────────────────────

    @Test
    fun `subtask tool name`() {
        assertEquals("feishu_task_subtask", subtaskTool.name)
    }

    @Test
    fun `subtask create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("subtask", jsonObj("guid" to "st_123"))
        }
        mockPost("/open-apis/task/v2/tasks/task_123/subtasks", data)

        val result = subtaskTool.execute(mapOf(
            "action" to "create", "task_guid" to "task_123", "summary" to "Sub task"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `subtask list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/task/v2/tasks/task_123/subtasks", data)

        val result = subtaskTool.execute(mapOf("action" to "list", "task_guid" to "task_123"))
        assertTrue(result.success)
    }

    // ─── Comment Tool ────────────────────────────────────────

    @Test
    fun `comment tool name`() {
        assertEquals("feishu_task_comment", commentTool.name)
    }

    @Test
    fun `comment create calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("comment", jsonObj("id" to "cm_123"))
        }
        mockPost("/open-apis/task/v2/comments", data)

        val result = commentTool.execute(mapOf(
            "action" to "create",
            "task_guid" to "task_123",
            "content" to "Nice work!"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `comment list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/task/v2/comments", data)

        val result = commentTool.execute(mapOf(
            "action" to "list",
            "task_guid" to "task_123"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `comment get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("comment", jsonObj("id" to "cm_123"))
        }
        mockGet("/open-apis/task/v2/comments/cm_123", data)

        val result = commentTool.execute(mapOf("action" to "get", "comment_id" to "cm_123"))
        assertTrue(result.success)
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns all 4 tools`() {
        val agg = FeishuTaskTools(config, client)
        assertEquals(4, agg.getAllTools().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuTaskTools(createDefaultConfig(enableTaskTools = false), client)
        assertEquals(0, agg.getToolDefinitions().size)
    }
}
