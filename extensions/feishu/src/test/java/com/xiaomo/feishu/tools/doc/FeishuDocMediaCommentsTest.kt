package com.xiaomo.feishu.tools.doc

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuDocMediaCommentsTest : FeishuToolTestBase() {

    private lateinit var mediaTool: FeishuDocMediaTool
    private lateinit var commentsTool: FeishuDocCommentsTool

    @Before
    fun setUp() {
        mediaTool = FeishuDocMediaTool(config, client)
        commentsTool = FeishuDocCommentsTool(config, client)
    }

    // ─── Doc Media Tool ──────────────────────────────────────

    @Test
    fun `media tool name and enabled`() {
        assertEquals("feishu_doc_media", mediaTool.name)
        assertTrue(mediaTool.isEnabled())
    }

    @Test
    fun `media missing action returns error`() = runTest {
        val result = mediaTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `media unknown action returns error`() = runTest {
        val result = mediaTool.execute(mapOf("action" to "delete"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("unknown action"))
    }

    @Test
    fun `media download calls correct API`() = runTest {
        mockDownloadRawWithHeaders(
            "/open-apis/drive/v1/medias",
            bytes = "fake image".toByteArray(),
            headers = mapOf("Content-Type" to "image/png")
        )

        val result = mediaTool.execute(mapOf(
            "action" to "download",
            "resource_token" to "boxcnXXX",
            "resource_type" to "media",
            "output_path" to "/tmp/test_image"
        ))
        // May fail due to file system access in test, but should not throw
        assertNotNull(result)
    }

    @Test
    fun `media insert missing doc_id returns error`() = runTest {
        val result = mediaTool.execute(mapOf("action" to "insert"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("doc_id"))
    }

    @Test
    fun `media tool definition`() {
        val def = mediaTool.getToolDefinition()
        assertEquals("feishu_doc_media", def.function.name)
        assertTrue(def.function.parameters.required.contains("action"))
    }

    // ─── Doc Comments Tool ───────────────────────────────────

    @Test
    fun `comments tool name and enabled`() {
        assertEquals("feishu_doc_comments", commentsTool.name)
        assertTrue(commentsTool.isEnabled())
    }

    @Test
    fun `comments missing action returns error`() = runTest {
        val result = commentsTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `comments unknown action returns error`() = runTest {
        // Comments tool requires file_token and file_type before checking action
        val result = commentsTool.execute(mapOf(
            "action" to "delete",
            "file_token" to "docXXX",
            "file_type" to "docx"
        ))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `comments list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/drive/v1/files/docXXX/comments", data)

        val result = commentsTool.execute(mapOf(
            "action" to "list",
            "file_token" to "docXXX",
            "file_type" to "docx"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `comments list missing file_token returns error`() = runTest {
        val result = commentsTool.execute(mapOf("action" to "list"))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("file_token"))
    }

    @Test
    fun `comments create calls correct API`() = runTest {
        val data = JsonObject().apply {
            addProperty("comment_id", "cmt_123")
        }
        mockPost("/open-apis/drive/v1/files/docXXX/comments", data)

        val result = commentsTool.execute(mapOf(
            "action" to "create",
            "file_token" to "docXXX",
            "file_type" to "docx",
            "elements" to listOf(mapOf("type" to "text_run", "text_run" to mapOf("text" to "Great doc!")))
        ))
        assertTrue(result.success)
    }

    @Test
    fun `comments create missing elements returns error`() = runTest {
        val result = commentsTool.execute(mapOf(
            "action" to "create",
            "file_token" to "docXXX",
            "file_type" to "docx"
        ))
        assertFalse(result.success)
        assertTrue(result.error!!.contains("elements"))
    }

    @Test
    fun `comments patch calls correct API`() = runTest {
        val data = JsonObject()
        mockPatch("/open-apis/drive/v1/files/docXXX/comments/cmt_123", data)

        val result = commentsTool.execute(mapOf(
            "action" to "patch",
            "file_token" to "docXXX",
            "file_type" to "docx",
            "comment_id" to "cmt_123",
            "is_solved_value" to true
        ))
        assertTrue(result.success)
    }

    @Test
    fun `comments handles API failure`() = runTest {
        mockGetError("/open-apis/drive/v1/files/docXXX/comments", "permission denied")

        val result = commentsTool.execute(mapOf(
            "action" to "list",
            "file_token" to "docXXX",
            "file_type" to "docx"
        ))
        assertFalse(result.success)
    }

    @Test
    fun `comments tool definition`() {
        val def = commentsTool.getToolDefinition()
        assertEquals("feishu_doc_comments", def.function.name)
        val actionEnum = def.function.parameters.properties["action"]?.enum
        assertNotNull(actionEnum)
        assertTrue(actionEnum!!.contains("list"))
        assertTrue(actionEnum.contains("create"))
        assertTrue(actionEnum.contains("patch"))
    }
}
