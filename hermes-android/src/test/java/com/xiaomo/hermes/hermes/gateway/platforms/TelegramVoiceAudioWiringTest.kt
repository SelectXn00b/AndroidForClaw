package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-008 (2026-06-14)：Telegram 入站 voice / audio 真下载 + STT 自动转写的 wiring 守卫。
 *
 * **范围**：本类只盯**两个分支**——`message.has("voice")` 和 `message.has("audio")`——
 * 在 `Telegram.kt::_handleMessage` 内的处理代码。其他分支（photo/document/video/sticker）
 * 由 TC-GW-008-c 红线守住"不能动"。
 *
 * **测试策略**：源码字面值扫描 + 函数体局部正则。完整 end-to-end 行为（真发 getFile + 真下载 +
 * 真转写）由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer 依赖，引入新依赖
 * 风险大于收益）。
 *
 * 对应 TC-GW-008-a..e（详见 docs/hermes-test-cases.md）。
 */
class TelegramVoiceAudioWiringTest {

    private val source: String by lazy { File(telegramKtPath()).readText() }

    /**
     * 在 _handleMessage 里抓出指定分支（voice / audio / photo 等）的代码块文本。
     * 用 `message.has("<branch>")` 作起点，`message.has("<下一个分支>")` 或 `else ->` 作终止。
     */
    private fun extractBranch(branchName: String): String {
        val startMarker = """message.has("$branchName")"""
        val startIdx = source.indexOf(startMarker)
        if (startIdx < 0) return ""
        // 找下一个 `message.has(` 或 `else ->` 作为终止
        val rest = source.substring(startIdx)
        val endRegex = Regex("""\n\s+(?:message\.has\(|else\s*->)""")
        val match = endRegex.find(rest, startIndex = startMarker.length)
        return if (match != null) rest.substring(0, match.range.first) else rest
    }

    /**
     * TC-GW-008-a: voice 分支必须改造为真下载 + 自动转写 + bilingual 前缀文案。
     */
    @Test
    fun `TC-GW-008-a voice branch downloads then transcribes`() {
        val voiceBranch = extractBranch("voice")
        assertTrue(
            "Telegram.kt 必须含 `message.has(\"voice\")` 分支。\n" +
                "实际：source 中找不到该字面值。",
            voiceBranch.isNotEmpty()
        )
        assertTrue(
            "voice 分支必须调 `_downloadTelegramFile(` —— Telegram getFile + 真下载入口。\n" +
                "实际 voice 分支:\n$voiceBranch",
            voiceBranch.contains("_downloadTelegramFile(")
        )
        assertTrue(
            "voice 分支必须调 `transcribeAudio(` —— 自动转写（对齐 Python 上游 _enrich_message_with_transcription）。\n" +
                "实际 voice 分支:\n$voiceBranch",
            voiceBranch.contains("transcribeAudio(") ||
                voiceBranch.contains("TranscriptionTools.transcribeAudio")
        )
        assertTrue(
            "voice 分支转写成功后必须给 text 加 bilingual 前缀字面值 " +
                "`[The user sent a voice message~ Here's what they said:` —— " +
                "对齐 Python `gateway/run.py:8218`。\n实际 voice 分支:\n$voiceBranch",
            voiceBranch.contains("[The user sent a voice message~ Here's what they said:")
        )
        // mediaUrls 不应再是裸 fileId
        assertFalse(
            "voice 分支不应再把 fileId 直接塞进 mediaUrls —— 必须先下载到本地路径。\n" +
                "实际 voice 分支:\n$voiceBranch",
            Regex("""mediaUrls\s*=\s*listOf\(\s*fileId\s*\)""").containsMatchIn(voiceBranch)
        )
    }

    /**
     * TC-GW-008-b: audio 分支同样改造，但 ext 为 mp3 / mediaTypes audio/mpeg / 保留 caption。
     */
    @Test
    fun `TC-GW-008-b audio branch downloads then transcribes with caption`() {
        val audioBranch = extractBranch("audio")
        assertTrue(
            "Telegram.kt 必须含 `message.has(\"audio\")` 分支。",
            audioBranch.isNotEmpty()
        )
        assertTrue(
            "audio 分支必须调 `_downloadTelegramFile(` —— 同 voice 分支。",
            audioBranch.contains("_downloadTelegramFile(")
        )
        assertTrue(
            "audio 分支必须调 `transcribeAudio(` —— 同 voice 分支。",
            audioBranch.contains("transcribeAudio(") ||
                audioBranch.contains("TranscriptionTools.transcribeAudio")
        )
        assertTrue(
            "audio 分支必须保留 `caption` 读取 —— audio 与 voice 不同：audio 有 caption。\n" +
                "实际 audio 分支:\n$audioBranch",
            audioBranch.contains("caption")
        )
        assertTrue(
            "audio 分支必须含 `audio/mpeg` mediaType 字面值。",
            audioBranch.contains("audio/mpeg")
        )
        // ext 至少在分支里出现 mp3 字面（用于 _downloadTelegramFile(fileId, "mp3") 调用）
        assertTrue(
            "audio 分支必须含 `mp3` 字面值 —— 写盘 ext 选择，对齐 Python `:2658` 强制 .mp3。\n" +
                "实际 audio 分支:\n$audioBranch",
            audioBranch.contains("mp3")
        )
    }

    /**
     * TC-GW-008-c (红线守卫): photo / document / video / sticker 四个分支不能被误改。
     */
    @Test
    fun `TC-GW-008-c photo document video sticker branches stay placeholder`() {
        listOf("photo", "document", "video", "sticker").forEach { branchName ->
            val branch = extractBranch(branchName)
            assertTrue(
                "Telegram.kt 必须保留 `$branchName` 分支。",
                branch.isNotEmpty()
            )
            // 这四个分支应该仍是 mediaUrls = listOf(fileId) 模式
            assertTrue(
                "$branchName 分支必须保留 `mediaUrls = listOf(fileId)` 占位模式 —— " +
                    "本轮 R-GW-008 只动 voice/audio，图片/视频/文档/sticker 留下次 R 处理。\n" +
                    "实际 $branchName 分支:\n$branch",
                Regex("""mediaUrls\s*=\s*listOf\s*\(\s*\w*[Ff]ileId\s*\)""").containsMatchIn(branch)
            )
            // 不应有 _downloadTelegramFile 调用（守红线）
            assertFalse(
                "$branchName 分支**不应**调 `_downloadTelegramFile(` —— " +
                    "本轮 R-GW-008 红线：图片/视频/文档/sticker 留下次 R。\n" +
                    "实际 $branchName 分支:\n$branch",
                branch.contains("_downloadTelegramFile(")
            )
        }
    }

    /**
     * TC-GW-008-d: _downloadTelegramFile 函数必须存在且形状正确。
     */
    @Test
    fun `TC-GW-008-d _downloadTelegramFile fetches getFile then downloads then caches`() {
        // 函数声明
        assertTrue(
            "Telegram.kt 必须含 `private fun _downloadTelegramFile` 或 " +
                "`private suspend fun _downloadTelegramFile` 函数声明。",
            Regex("""private\s+(?:suspend\s+)?fun\s+_downloadTelegramFile\b""").containsMatchIn(source)
        )
        // 步骤 1: 拼 getFile URL
        assertTrue(
            "_downloadTelegramFile 必须含 `getFile?file_id=` 字面值 —— " +
                "Telegram Bot API 第一步：从 fileId 拿 file_path。",
            source.contains("getFile?file_id=")
        )
        // 步骤 2: 拼下载 URL
        assertTrue(
            "_downloadTelegramFile 必须含 `/file/bot` 字面值 —— " +
                "Telegram CDN 下载 URL 前缀（`https://api.telegram.org/file/bot<token>/<file_path>`）。",
            source.contains("/file/bot")
        )
        // 步骤 3: 调 cacheAudioFromBytes 写盘
        assertTrue(
            "_downloadTelegramFile 必须调 `cacheAudioFromBytes(` —— " +
                "复用 BasePlatformAdapter 既有 helper 写到 <cacheDir>/media/audio/。",
            source.contains("cacheAudioFromBytes(")
        )
    }

    /**
     * TC-GW-008-e: 转写失败时降级文案对齐上游。
     */
    @Test
    fun `TC-GW-008-e transcription failure falls back to bilingual notice`() {
        assertTrue(
            "Telegram.kt 必须含失败降级文案字面值 " +
                "`[The user sent a voice message but I had trouble transcribing it~` —— " +
                "对齐 Python `gateway/run.py:8222-8252`，让 agent 知道有语音但转写失败。",
            source.contains("[The user sent a voice message but I had trouble transcribing it~")
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

    private fun telegramKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/platforms/Telegram.kt").path
}
