package com.xiaomo.feishu.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.slot
import org.junit.After
import org.junit.Before

/**
 * 飞书工具单元测试基类
 * 提供 MockK client + 默认 config + JSON 辅助方法
 */
open class FeishuToolTestBase {

    protected lateinit var client: FeishuClient
    protected lateinit var config: FeishuConfig

    @Before
    open fun baseSetUp() {
        client = mockk(relaxed = true)
        config = createDefaultConfig()
        MockKAnnotations.init(this)
    }

    @After
    open fun baseTearDown() {
        clearAllMocks()
    }

    /**
     * 创建默认全部启用的 FeishuConfig
     */
    protected fun createDefaultConfig(
        enableDocTools: Boolean = true,
        enableWikiTools: Boolean = true,
        enableDriveTools: Boolean = true,
        enableBitableTools: Boolean = true,
        enableTaskTools: Boolean = true,
        enableChatTools: Boolean = true,
        enablePermTools: Boolean = false,
        enableUrgentTools: Boolean = true,
        enableSheetTools: Boolean = true,
        enableCalendarTools: Boolean = true,
        enableImTools: Boolean = true,
        enableSearchTools: Boolean = true,
        enableCommonTools: Boolean = true
    ) = FeishuConfig(
        appId = "test_app_id",
        appSecret = "test_app_secret",
        enableDocTools = enableDocTools,
        enableWikiTools = enableWikiTools,
        enableDriveTools = enableDriveTools,
        enableBitableTools = enableBitableTools,
        enableTaskTools = enableTaskTools,
        enableChatTools = enableChatTools,
        enablePermTools = enablePermTools,
        enableUrgentTools = enableUrgentTools,
        enableSheetTools = enableSheetTools,
        enableCalendarTools = enableCalendarTools,
        enableImTools = enableImTools,
        enableSearchTools = enableSearchTools,
        enableCommonTools = enableCommonTools
    )

    // ─── Mock helpers ───────────────────────────────────────────

    /**
     * Mock client.get() 对指定 path 前缀返回成功 JsonObject
     */
    protected fun mockGet(pathPrefix: String, data: JsonObject) {
        coEvery { client.get(match { it.startsWith(pathPrefix) }, any()) } returns
            Result.success(wrapData(data))
    }

    /**
     * Mock client.get() 对指定 path 前缀返回成功 (无 headers 参数版本)
     */
    protected fun mockGetExact(path: String, data: JsonObject) {
        coEvery { client.get(path, any()) } returns Result.success(wrapData(data))
    }

    /**
     * Mock client.post() 对指定 path 前缀返回成功
     */
    protected fun mockPost(pathPrefix: String, data: JsonObject) {
        coEvery { client.post(match { it.startsWith(pathPrefix) }, any(), any()) } returns
            Result.success(wrapData(data))
    }

    /**
     * Mock client.put() 返回成功
     */
    protected fun mockPut(pathPrefix: String, data: JsonObject) {
        coEvery { client.put(match { it.startsWith(pathPrefix) }, any()) } returns
            Result.success(wrapData(data))
    }

    /**
     * Mock client.patch() 返回成功
     */
    protected fun mockPatch(pathPrefix: String, data: JsonObject) {
        coEvery { client.patch(match { it.startsWith(pathPrefix) }, any()) } returns
            Result.success(wrapData(data))
    }

    /**
     * Mock client.delete() 返回成功
     */
    protected fun mockDelete(pathPrefix: String, data: JsonObject = JsonObject()) {
        coEvery { client.delete(match { it.startsWith(pathPrefix) }) } returns
            Result.success(wrapData(data))
    }

    /**
     * Mock client.get() 对指定 path 前缀返回失败
     */
    protected fun mockGetError(pathPrefix: String, msg: String = "API error") {
        coEvery { client.get(match { it.startsWith(pathPrefix) }, any()) } returns
            Result.failure(Exception(msg))
    }

    /**
     * Mock client.post() 对指定 path 前缀返回失败
     */
    protected fun mockPostError(pathPrefix: String, msg: String = "API error") {
        coEvery { client.post(match { it.startsWith(pathPrefix) }, any(), any()) } returns
            Result.failure(Exception(msg))
    }

    /**
     * Mock client.patch() 返回失败
     */
    protected fun mockPatchError(pathPrefix: String, msg: String = "API error") {
        coEvery { client.patch(match { it.startsWith(pathPrefix) }, any()) } returns
            Result.failure(Exception(msg))
    }

    /**
     * Mock client.delete() 返回失败
     */
    protected fun mockDeleteError(pathPrefix: String, msg: String = "API error") {
        coEvery { client.delete(match { it.startsWith(pathPrefix) }) } returns
            Result.failure(Exception(msg))
    }

    /**
     * Mock client.downloadRaw() 返回成功
     */
    protected fun mockDownloadRaw(pathPrefix: String, bytes: ByteArray = ByteArray(10)) {
        coEvery { client.downloadRaw(match { it.startsWith(pathPrefix) }) } returns
            Result.success(bytes)
    }

    /**
     * Mock client.downloadRawWithHeaders()
     */
    protected fun mockDownloadRawWithHeaders(
        pathPrefix: String,
        bytes: ByteArray = ByteArray(10),
        headers: Map<String, String> = mapOf("Content-Type" to "application/octet-stream")
    ) {
        coEvery { client.downloadRawWithHeaders(match { it.startsWith(pathPrefix) }) } returns
            Result.success(Pair(bytes, headers))
    }

    // ─── JSON helpers ───────────────────────────────────────────

    /**
     * 将 data 包装为飞书 API 标准响应结构 {"code":0,"data":{...}}
     */
    protected fun wrapData(data: JsonObject): JsonObject {
        return JsonObject().apply {
            addProperty("code", 0)
            addProperty("msg", "success")
            add("data", data)
        }
    }

    /**
     * 快速构造 JsonObject
     */
    protected fun jsonObj(vararg pairs: Pair<String, Any?>): JsonObject {
        return JsonObject().apply {
            pairs.forEach { (key, value) ->
                when (value) {
                    is String -> addProperty(key, value)
                    is Number -> addProperty(key, value)
                    is Boolean -> addProperty(key, value)
                    is JsonObject -> add(key, value)
                    is JsonArray -> add(key, value)
                    null -> add(key, null)
                }
            }
        }
    }

    /**
     * 快速构造 JsonArray
     */
    protected fun jsonArr(vararg items: JsonObject): JsonArray {
        return JsonArray().apply { items.forEach { add(it) } }
    }

    /**
     * 从 JSON 字符串解析
     */
    protected fun parseJson(json: String): JsonObject {
        return JsonParser.parseString(json).asJsonObject
    }
}
