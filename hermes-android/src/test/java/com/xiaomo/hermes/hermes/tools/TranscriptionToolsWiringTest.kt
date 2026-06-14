package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-032 (2026-06-14)：把 Python 上游 `tools/transcription_tools.py` 的 OpenAI Whisper
 * `_transcribe_openai` 接到 Kotlin 侧。本类用源码扫描方式守住 wiring 关键字面值；完整 multipart
 * 行为由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer 依赖）。
 *
 * 对应 TC-AGENT-032-a..d（详见 docs/hermes-test-cases.md）。
 */
class TranscriptionToolsWiringTest {

    private val source: String by lazy { File(transcriptionToolsPath()).readText() }

    /**
     * TC-AGENT-032-a: TranscriptionTools.kt 文件存在 + 公共 API 签名正确。
     */
    @Test
    fun `TC-AGENT-032-a TranscriptionTools file and public API exist`() {
        val file = File(transcriptionToolsPath())
        assertTrue(
            "TranscriptionTools.kt 必须存在于 hermes/tools/ 下 —— 1:1 对齐 Python " +
                "tools/transcription_tools.py。\n实际路径: ${file.path}",
            file.exists()
        )
        assertTrue(
            "TranscriptionTools.kt 必须含顶层 `fun transcribeAudio(filePath: String` —— " +
                "对齐 Python `transcribe_audio(file_path, model=None)` 的公共入口。",
            Regex("""fun\s+transcribeAudio\s*\(\s*filePath\s*:\s*String""").containsMatchIn(source)
        )
        assertTrue(
            "TranscriptionTools.kt 必须含 `data class TranscribeResult` 声明 —— " +
                "返回类型对齐 Python dict {success, transcript, error, provider}。",
            Regex("""data\s+class\s+TranscribeResult\b""").containsMatchIn(source)
        )
        // 字段名对齐
        listOf("success", "transcript", "error", "provider").forEach { fieldName ->
            assertTrue(
                "TranscribeResult 必须含字段 `$fieldName` —— 对齐 Python 上游字段名。",
                Regex("""\b$fieldName\s*:\s*""").containsMatchIn(source)
            )
        }
    }

    /**
     * TC-AGENT-032-b: OpenAI Whisper provider 实现关键字面值。
     */
    @Test
    fun `TC-AGENT-032-b OpenAI Whisper request shape literals`() {
        // Endpoint + URL path
        assertTrue(
            "TranscriptionTools 必须含 `whisper-1` 字面值 —— OpenAI 默认 STT 模型，" +
                "对齐 Python `transcription_tools.py:67 DEFAULT_STT_MODEL`。",
            source.contains("whisper-1")
        )
        assertTrue(
            "TranscriptionTools 必须含 `https://api.openai.com/v1` 字面值 —— " +
                "OpenAI 默认 base URL，对齐 Python `:75 STT_OPENAI_BASE_URL`。",
            source.contains("https://api.openai.com/v1")
        )
        assertTrue(
            "TranscriptionTools 必须含 `/audio/transcriptions` URL path 字面值 —— " +
                "OpenAI Whisper endpoint。",
            source.contains("/audio/transcriptions")
        )
        // Auth
        assertTrue(
            "TranscriptionTools 必须含 `Authorization` header 字面值 —— Bearer token 鉴权。",
            source.contains("Authorization")
        )
        assertTrue(
            "TranscriptionTools 必须含 `Bearer ` 字面值（含尾空格）—— Bearer token 拼接。",
            source.contains("Bearer ")
        )
        // Multipart
        assertTrue(
            "TranscriptionTools 必须含 multipart 调用 —— `MultipartBody.Builder` 或 " +
                "`multipart/form-data` 字面值任一。OpenAI SDK 用 multipart 上传 audio file。",
            source.contains("MultipartBody.Builder") || source.contains("multipart/form-data")
        )
        // response_format=text（对齐上游 :512）
        assertTrue(
            "TranscriptionTools 必须含 `response_format` + `text` 字面值 —— " +
                "对齐上游对 whisper-1 的 response_format=text 处理。",
            source.contains("response_format") && source.contains("text")
        )
    }

    /**
     * TC-AGENT-032-c: API key 读取 fallback 顺序对齐上游。
     */
    @Test
    fun `TC-AGENT-032-c key resolution falls back from VOICE_TOOLS_OPENAI_KEY to OPENAI_API_KEY`() {
        assertTrue(
            "TranscriptionTools 必须含 `VOICE_TOOLS_OPENAI_KEY` 环境变量名 —— " +
                "上游优先读这个独立 key，避免与 OpenRouter 主 key 冲突。",
            source.contains("VOICE_TOOLS_OPENAI_KEY")
        )
        assertTrue(
            "TranscriptionTools 必须含 `OPENAI_API_KEY` 字面值 —— fallback key，" +
                "对齐 Python `tool_backend_helpers.py:104` 顺序。",
            source.contains("OPENAI_API_KEY")
        )
        assertTrue(
            "TranscriptionTools 必须含 `No STT API key` 错误文案 —— missing key 时的用户提示。",
            source.contains("No STT API key")
        )
    }

    /**
     * TC-AGENT-032-d: 文件大小 + 格式校验对齐上游。
     */
    @Test
    fun `TC-AGENT-032-d file size cap and supported formats`() {
        // 25MB 上限：MAX_FILE_SIZE 常量或裸字面值
        assertTrue(
            "TranscriptionTools 必须含 25MB 文件上限 —— 对齐 Python " +
                "`transcription_tools.py:79 MAX_FILE_SIZE = 25 * 1024 * 1024`。\n" +
                "断言：含 `MAX_FILE_SIZE` 常量名 或 含 `25` 与 `1024` 共现。",
            source.contains("MAX_FILE_SIZE") ||
                (source.contains("25") && source.contains("1024"))
        )
        // SUPPORTED_FORMATS 常量
        assertTrue(
            "TranscriptionTools 必须含 `SUPPORTED_FORMATS` 常量声明。",
            source.contains("SUPPORTED_FORMATS")
        )
        // 至少 4 种音频扩展名（从 Python 上游 `:77` SUPPORTED_FORMATS 摘）
        listOf(".mp3", ".ogg", ".wav", ".m4a").forEach { ext ->
            assertTrue(
                "SUPPORTED_FORMATS 必须含扩展名字面值 `$ext` —— OpenAI Whisper 可识别格式之一。",
                source.contains("\"$ext\"")
            )
        }
        assertFalse(
            "TranscriptionTools 不应该把 stub 文案 `not available on Android` 留下来 —— " +
                "新实现必须真发请求或真校验，而不是直接 stub 返回。" +
                "（`Model X not available on Groq/OpenAI` 是上游正常的 log 文案，不是 stub。）",
            source.contains("not available on Android")
        )
    }

    // ----- helpers -----

    private fun hermesAndroidSrcMainRoot(): File {
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun transcriptionToolsPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/tools/TranscriptionTools.kt").path
}
