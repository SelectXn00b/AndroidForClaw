package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-032 (2026-06-14)：纯 JVM 行为单测，覆盖**短路**路径——既不需要 Robolectric 也
 * 不需要 MockWebServer。完整 multipart shape 验证 deferred to §3 E2E。
 *
 * 对应 TC-AGENT-032-e/f（详见 docs/hermes-test-cases.md）。
 */
class TranscriptionToolsBehaviorTest {

    /**
     * TC-AGENT-032-e: 文件不存在时函数应短路，返回错误而非抛异常或真发请求。
     */
    @Test
    fun `TC-AGENT-032-e missing file returns error without throwing`() {
        val nonExistent = "/this/path/definitely/does/not/exist_${System.currentTimeMillis()}.mp3"
        assertFalse("Sanity: 测试前置文件不应该存在", File(nonExistent).exists())

        val result = transcribeAudio(nonExistent)

        assertNotNull("transcribeAudio 必须返回 TranscribeResult（不许抛异常）", result)
        assertFalse(
            "文件不存在时 success 必须 false。\n实际返回: $result",
            result.success
        )
        assertEquals(
            "文件不存在时 transcript 必须为空字符串。\n实际返回: $result",
            "",
            result.transcript
        )
        assertNotNull(
            "文件不存在时必须有 error 文案给 agent / Telegram 降级使用。\n实际返回: $result",
            result.error
        )
        val errLower = result.error?.lowercase() ?: ""
        assertTrue(
            "error 文案必须 mention 文件不存在 —— 含 `not found` 或 `does not exist` 或 " +
                "`file` 任一关键词。\n实际 error: ${result.error}",
            errLower.contains("not found") ||
                errLower.contains("does not exist") ||
                errLower.contains("file")
        )
    }

    /**
     * TC-AGENT-032-f: 既无 VOICE_TOOLS_OPENAI_KEY 也无 OPENAI_API_KEY 时函数必须早退，
     * **不**真发网络请求（避免在 CI 环境意外打 OpenAI 计费）。
     *
     * 注意：纯 JVM 单测里 `System.getenv` 无法清掉真实运行环境的 env，所以本测试只能在
     * 没配 key 的开发机上稳定通过；CI 环境通常没配，本地开发机如果配了会 SKIP（assumeTrue）。
     */
    @Test
    fun `TC-AGENT-032-f missing key short-circuits before network call`() {
        val voiceKey = System.getenv("VOICE_TOOLS_OPENAI_KEY")
        val openaiKey = System.getenv("OPENAI_API_KEY")
        org.junit.Assume.assumeTrue(
            "本测试要求测试环境无 VOICE_TOOLS_OPENAI_KEY / OPENAI_API_KEY；" +
                "当前已配则跳过（避免污染真实 key 的测试结果）。",
            voiceKey.isNullOrBlank() && openaiKey.isNullOrBlank()
        )

        // 准备一个真实存在的临时文件（避免被 TC-e 的 missing-file 路径短路）
        val tmp = File.createTempFile("tc032f-", ".mp3")
        tmp.writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00))  // ID3 header bytes
        try {
            val result = transcribeAudio(tmp.absolutePath)

            assertFalse(
                "无 key 时 success 必须 false。\n实际返回: $result",
                result.success
            )
            assertNotNull(
                "无 key 时必须有 error 文案。\n实际返回: $result",
                result.error
            )
            assertTrue(
                "error 文案必须 mention `No STT API key` 或同义关键字 —— " +
                    "对齐 Python upstream 行为，让上层 Telegram 知道是 key 缺失。\n" +
                    "实际 error: ${result.error}",
                (result.error ?: "").contains("No STT API key", ignoreCase = true) ||
                    (result.error ?: "").contains("VOICE_TOOLS_OPENAI_KEY", ignoreCase = true) ||
                    (result.error ?: "").contains("OPENAI_API_KEY", ignoreCase = true)
            )
            // provider 字段有值（说明确实走到 OpenAI provider 了，只是 key 缺失早退）
            assertNotNull(
                "missing-key 路径仍应回填 provider 字段，方便日志区分。\n实际返回: $result",
                result.provider
            )
        } finally {
            tmp.delete()
        }
    }
}
