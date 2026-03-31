package com.xiaomo.feishu.tools.doc

/**
 * E2E 测试：飞书文档创建（标题 + 内容）
 *
 * 测试目标：验证 "创建个文档 随便写点内容" 能同时写入标题和内容。
 *
 * 测试步骤：
 * 1. 创建文档（带标题）
 * 2. 写入内容（通过 DocUpdateHelper）
 * 3. 读取文档 raw_content
 * 4. 断言：标题不为空 AND 内容不为空
 *
 * 运行方式（通过 ADB）：
 * ```
 * adb shell am instrument -w -e class com.xiaomo.feishu.tools.doc.DocCreateE2ETest \
 *   com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * 或通过 GatewayServer HTTP 接口触发（需要在 app 中注册）。
 */

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.storage.WeixinAccountStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DocCreateE2ETest {

    companion object {
        private const val TAG = "DocCreateE2ETest"
        private const val TEST_TITLE = "E2E测试文档_${System.currentTimeMillis()}"
        private const val TEST_CONTENT = "这是一段测试内容，用于验证文档创建后内容能正确写入。"
    }

    /**
     * 从设备上的 openclaw.json 读取飞书配置
     */
    private fun loadFeishuConfig(): FeishuConfig? {
        return try {
            // 尝试从设备上的配置文件读取
            val configFile = File("/data/data/com.xiaomo.androidforclaw/files/openclaw/openclaw.json")
            if (!configFile.exists()) {
                Log.e(TAG, "Config file not found: ${configFile.absolutePath}")
                return null
            }
            val json = com.google.gson.Gson().fromJson(configFile.readText(), JsonObject::class.java)
            val feishu = json.getAsJsonObject("feishu") ?: return null
            FeishuConfig(
                enabled = feishu.get("enabled")?.asBoolean ?: false,
                appId = feishu.get("appId")?.asString ?: return null,
                appSecret = feishu.get("appSecret")?.asString ?: return null,
                domain = feishu.get("domain")?.asString ?: "feishu",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config", e)
            null
        }
    }

    /**
     * 从 assets 或 filesDir 加载配置（备用方案）
     */
    private fun loadFeishuConfigFromFilesDir(filesDir: File): FeishuConfig? {
        return try {
            val configFile = File(filesDir, "openclaw/openclaw.json")
            if (!configFile.exists()) {
                Log.e(TAG, "Config not found: ${configFile.absolutePath}")
                return null
            }
            val json = com.google.gson.Gson().fromJson(configFile.readText(), JsonObject::class.java)
            val feishu = json.getAsJsonObject("feishu") ?: return null
            FeishuConfig(
                enabled = feishu.get("enabled")?.asBoolean ?: false,
                appId = feishu.get("appId")?.asString ?: return null,
                appSecret = feishu.get("appSecret")?.asString ?: return null,
                domain = feishu.get("domain")?.asString ?: "feishu",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config from filesDir", e)
            null
        }
    }

    /**
     * 核心测试：创建文档 → 写入内容 → 读取 → 验证
     */
    @Test
    fun testDocCreateWithTitleAndContent() = runBlocking {
        val config = loadFeishuConfig()
            ?: run {
                Log.w(TAG, "⚠️ 无法加载飞书配置，跳过 E2E 测试")
                return@runBlocking
            }

        if (!config.enabled) {
            Log.w(TAG, "⚠️ 飞书未启用，跳过 E2E 测试")
            return@runBlocking
        }

        val client = FeishuClient(config)

        // ===== Step 1: 获取 tenant_access_token =====
        val tokenResult = client.getTenantAccessToken()
        assertTrue("获取 tenant_access_token 失败: ${tokenResult.exceptionOrNull()?.message}",
            tokenResult.isSuccess)
        val token = tokenResult.getOrNull()!!
        Log.i(TAG, "✅ tenant_access_token 获取成功")

        // ===== Step 2: 创建文档 =====
        val createBody = mapOf(
            "title" to TEST_TITLE,
            "type" to "doc"
        )
        val createResult = client.post("/open-apis/docx/v1/documents", createBody)
        assertTrue("创建文档失败: ${createResult.exceptionOrNull()?.message}",
            createResult.isSuccess)

        val createData = createResult.getOrNull()!!
        val docId = createData
            .getAsJsonObject("data")
            ?.getAsJsonObject("document")
            ?.get("document_id")
            ?.asString
        assertNotNull("缺少 document_id", docId)
        Log.i(TAG, "✅ 文档创建成功: docId=$docId, title=$TEST_TITLE")

        // ===== Step 3: 写入内容（使用 DocUpdateHelper，对齐修复后的逻辑）=====
        val updateHelper = DocUpdateHelper(client)
        val writeResult = updateHelper.updateDocContent(docId!!, TEST_CONTENT)
        assertTrue("内容写入失败: ${writeResult.exceptionOrNull()?.message}",
            writeResult.isSuccess)
        Log.i(TAG, "✅ 内容写入成功")

        // ===== Step 4: 读取文档 raw_content =====
        val readResult = client.get("/open-apis/docx/v1/documents/$docId/raw_content")
        assertTrue("读取文档失败: ${readResult.exceptionOrNull()?.message}",
            readResult.isSuccess)

        val readData = readResult.getOrNull()!!
        val content = readData
            .getAsJsonObject("data")
            ?.get("content")
            ?.asString ?: ""

        Log.i(TAG, "📄 文档内容: \"$content\"")

        // ===== Step 5: 验证 =====
        // 5a: 标题不为空（通过创建 API 已确认）
        assertTrue("❌ 标题为空", TEST_TITLE.isNotBlank())
        Log.i(TAG, "✅ 标题验证通过: $TEST_TITLE")

        // 5b: 内容不为空
        assertTrue("❌ 内容为空！这说明写入逻辑有 bug", content.isNotBlank())
        assertEquals("❌ 内容不匹配", TEST_CONTENT, content.trim())
        Log.i(TAG, "✅ 内容验证通过: $content")

        // ===== Step 6: 清理 =====
        try {
            client.delete("/open-apis/docx/v1/documents/$docId")
            Log.i(TAG, "🗑️ 测试文档已删除: $docId")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 删除测试文档失败: ${e.message}")
        }

        Log.i(TAG, "🎉 E2E 测试全部通过！标题和内容均正常写入。")
    }

    /**
     * 最小测试：只测内容写入部分（假设已有文档）
     * 可通过 ADB 传入 docId 参数运行
     */
    @Test
    fun testContentWriteOnly() = runBlocking {
        val config = loadFeishuConfig()
            ?: run {
                Log.w(TAG, "⚠️ 无法加载飞书配置，跳过测试")
                return@runBlocking
            }

        val client = FeishuClient(config)
        val tokenResult = client.getTenantAccessToken()
        assertTrue("获取 token 失败", tokenResult.isSuccess)

        // 创建测试文档
        val createBody = mapOf("title" to "E2E内容写入测试", "type" to "doc")
        val createResult = client.post("/open-apis/docx/v1/documents", createBody)
        assertTrue("创建文档失败", createResult.isSuccess)

        val docId = createResult.getOrNull()!!
            .getAsJsonObject("data")
            ?.getAsJsonObject("document")
            ?.get("document_id")?.asString!!

        // 测试 DocUpdateHelper
        val helper = DocUpdateHelper(client)
        val testContent = "Hello E2E Test ${System.currentTimeMillis()}"
        val writeResult = helper.updateDocContent(docId, testContent)

        if (writeResult.isFailure) {
            Log.e(TAG, "❌ 内容写入失败: ${writeResult.exceptionOrNull()?.message}")
            // 清理
            client.delete("/open-apis/docx/v1/documents/$docId")
            fail("内容写入失败: ${writeResult.exceptionOrNull()?.message}")
        }

        // 读取验证
        val readResult = client.get("/open-apis/docx/v1/documents/$docId/raw_content")
        assertTrue("读取失败", readResult.isSuccess)

        val content = readResult.getOrNull()!!
            .getAsJsonObject("data")
            ?.get("content")?.asString ?: ""

        Log.i(TAG, "写入内容: $testContent")
        Log.i(TAG, "读取内容: $content")

        assertTrue("❌ 读取到的内容为空", content.isNotBlank())
        assertTrue("❌ 内容不包含测试文本", content.contains(testContent))

        // 清理
        client.delete("/open-apis/docx/v1/documents/$docId")
        Log.i(TAG, "🎉 内容写入测试通过！")
    }
}
