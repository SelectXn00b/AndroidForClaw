package com.xiaomo.feishu.messaging

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/bot-content.ts
 * - ../openclaw/src/channels/feishu/post.ts
 *
 * Parses all Feishu message types into text/Markdown for the agent.
 */

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 解析结果
 */
data class ParseResult(
    val text: String,
    val mediaKeys: MediaKeys? = null
)

/**
 * 媒体 key 信息
 */
data class MediaKeys(
    val imageKey: String? = null,
    val fileKey: String? = null,
    val fileName: String? = null,
    val mediaType: String // "image", "file", "audio", "video", "sticker"
)

/**
 * 飞书消息内容解析器
 * 对齐 OpenClaw bot-content.ts + post.ts
 */
object FeishuContentParser {

    private const val TAG = "FeishuContentParser"
    private val gson = Gson()

    /**
     * 解析消息内容（顶层路由）
     * 对齐 OpenClaw parseMessageContent()
     */
    fun parseMessageContent(msgType: String, content: String): ParseResult {
        return try {
            when (msgType) {
                "text" -> parseTextContent(content)
                "post" -> ParseResult(text = parsePostContent(content))
                "image" -> parseImageContent(content)
                "file" -> parseFileContent(content)
                "audio" -> parseAudioContent(content)
                "video" -> parseVideoContent(content)
                "sticker" -> parseStickerContent(content)
                "share_chat" -> parseShareChatContent(content)
                "share_user" -> parseShareUserContent(content)
                "merge_forward" -> ParseResult(text = parseMergeForwardContent(content))
                else -> {
                    Log.w(TAG, "Unknown message type: $msgType")
                    ParseResult(text = content)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $msgType content", e)
            ParseResult(text = content)
        }
    }

    // ===== Text =====

    private fun parseTextContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val text = json.get("text")?.asString ?: content
            ParseResult(text = text)
        } catch (e: Exception) {
            ParseResult(text = content)
        }
    }

    // ===== Post (Rich Text) =====

    /**
     * 将飞书富文本 (post) 转换为 Markdown
     * 对齐 OpenClaw parsePostContent() + post.ts
     */
    fun parsePostContent(content: String): String {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)

            // 查找 payload: 直接 {title, content} 或 post.zh_cn/en_us 下
            val payload = resolvePostPayload(json) ?: return content

            val title = payload.get("title")?.asString
            val paragraphs = payload.getAsJsonArray("content") ?: return content

            val sb = StringBuilder()

            // Title → Markdown heading
            if (!title.isNullOrBlank()) {
                sb.appendLine("## $title")
                sb.appendLine()
            }

            // 每个 paragraph 是一个 element 数组
            for (i in 0 until paragraphs.size()) {
                val paragraph = paragraphs[i]
                if (paragraph is JsonArray) {
                    val line = renderParagraph(paragraph)
                    sb.appendLine(line)
                }
            }

            sb.toString().trimEnd()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse post content", e)
            content
        }
    }

    /**
     * 定位 post payload（支持多种 JSON 结构）
     */
    private fun resolvePostPayload(json: JsonObject): JsonObject? {
        // 直接 {title, content} 结构
        if (json.has("content") && json.get("content") is JsonArray) {
            return json
        }

        // 嵌套 post.zh_cn / post.en_us 结构
        val post = json.getAsJsonObject("post") ?: json

        // 尝试多种 locale
        for (locale in listOf("zh_cn", "en_us", "ja_jp")) {
            val localePayload = post.getAsJsonObject(locale)
            if (localePayload != null) return localePayload
        }

        // 取第一个可用的 locale
        for (key in post.keySet()) {
            val child = post.get(key)
            if (child is JsonObject && child.has("content")) {
                return child
            }
        }

        return null
    }

    /**
     * 渲染段落内所有元素为 Markdown
     */
    private fun renderParagraph(elements: JsonArray): String {
        val sb = StringBuilder()
        for (element in elements) {
            if (element is JsonObject) {
                sb.append(renderElement(element))
            }
        }
        return sb.toString()
    }

    /**
     * 渲染单个富文本元素
     * 对齐 OpenClaw post.ts element rendering
     */
    private fun renderElement(element: JsonObject): String {
        val tag = element.get("tag")?.asString ?: return ""

        return when (tag) {
            "text" -> renderTextElement(element)
            "a" -> {
                val text = element.get("text")?.asString ?: ""
                val href = element.get("href")?.asString ?: ""
                if (href.isNotEmpty()) "[$text]($href)" else text
            }
            "at" -> {
                val userName = element.get("user_name")?.asString
                    ?: element.get("name")?.asString ?: "user"
                "@$userName"
            }
            "img" -> {
                val imageKey = element.get("image_key")?.asString ?: ""
                "[image:$imageKey]"
            }
            "media" -> {
                val fileKey = element.get("file_key")?.asString ?: ""
                "[file:$fileKey]"
            }
            "emotion" -> {
                val emojiType = element.get("emoji_type")?.asString ?: ""
                "[$emojiType]"
            }
            "code_block", "pre" -> {
                val language = element.get("language")?.asString ?: ""
                val text = element.get("text")?.asString ?: ""
                "\n```$language\n$text\n```\n"
            }
            "code" -> {
                val text = element.get("text")?.asString ?: ""
                "`$text`"
            }
            "hr" -> "\n---\n"
            "br" -> "\n"
            else -> element.get("text")?.asString ?: ""
        }
    }

    /**
     * 渲染 text 元素（支持样式）
     */
    private fun renderTextElement(element: JsonObject): String {
        val text = element.get("text")?.asString ?: return ""
        val style = element.getAsJsonObject("style") ?: return text

        var result = text
        if (style.get("bold")?.asBoolean == true) result = "**$result**"
        if (style.get("italic")?.asBoolean == true) result = "*$result*"
        if (style.get("strikethrough")?.asBoolean == true) result = "~~$result~~"
        if (style.get("underline")?.asBoolean == true) result = "<u>$result</u>"
        if (style.get("code")?.asBoolean == true) result = "`$result`"

        return result
    }

    // ===== Media types =====

    private fun parseImageContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val imageKey = json.get("image_key")?.asString
            ParseResult(
                text = "[图片]",
                mediaKeys = imageKey?.let { MediaKeys(imageKey = it, mediaType = "image") }
            )
        } catch (e: Exception) {
            ParseResult(text = "[图片]")
        }
    }

    private fun parseFileContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val fileKey = json.get("file_key")?.asString
            val fileName = json.get("file_name")?.asString ?: "未知文件"
            ParseResult(
                text = "[文件: $fileName]",
                mediaKeys = fileKey?.let { MediaKeys(fileKey = it, fileName = fileName, mediaType = "file") }
            )
        } catch (e: Exception) {
            ParseResult(text = "[文件]")
        }
    }

    private fun parseAudioContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val fileKey = json.get("file_key")?.asString
            ParseResult(
                text = "[语音]",
                mediaKeys = fileKey?.let { MediaKeys(fileKey = it, mediaType = "audio") }
            )
        } catch (e: Exception) {
            ParseResult(text = "[语音]")
        }
    }

    private fun parseVideoContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val fileKey = json.get("file_key")?.asString
            val imageKey = json.get("image_key")?.asString
            ParseResult(
                text = "[视频]",
                mediaKeys = fileKey?.let {
                    MediaKeys(fileKey = it, imageKey = imageKey, mediaType = "video")
                }
            )
        } catch (e: Exception) {
            ParseResult(text = "[视频]")
        }
    }

    private fun parseStickerContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val fileKey = json.get("file_key")?.asString
            ParseResult(
                text = "[表情]",
                mediaKeys = fileKey?.let { MediaKeys(fileKey = it, mediaType = "sticker") }
            )
        } catch (e: Exception) {
            ParseResult(text = "[表情]")
        }
    }

    // ===== Share types =====

    private fun parseShareChatContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val name = json.get("chat_name")?.asString
                ?: json.get("name")?.asString
            val chatId = json.get("share_chat_id")?.asString ?: ""
            val display = if (!name.isNullOrBlank()) "[分享群: $name]" else "[分享群: $chatId]"
            ParseResult(text = display)
        } catch (e: Exception) {
            ParseResult(text = "[分享群]")
        }
    }

    private fun parseShareUserContent(content: String): ParseResult {
        return try {
            val json = gson.fromJson(content, JsonObject::class.java)
            val userId = json.get("user_id")?.asString ?: ""
            ParseResult(text = "[分享用户: $userId]")
        } catch (e: Exception) {
            ParseResult(text = "[分享用户]")
        }
    }

    // ===== Merge Forward =====

    /**
     * 解析合并转发消息
     * 对齐 OpenClaw parseMergeForwardContent()
     */
    fun parseMergeForwardContent(content: String): String {
        return try {
            val json = gson.fromJson(content, JsonElement::class.java)

            val messages: JsonArray = when {
                json is JsonArray -> json
                json is JsonObject && json.has("messages") -> json.getAsJsonArray("messages")
                json is JsonObject && json.has("combine") -> json.getAsJsonArray("combine")
                else -> return "[合并转发消息]"
            }

            if (messages.size() == 0) return "[合并转发消息（空）]"

            val sb = StringBuilder("[合并转发消息]\n")
            val limit = minOf(messages.size(), 50)

            for (i in 0 until limit) {
                val msg = messages[i]
                if (msg is JsonObject) {
                    val msgType = msg.get("msg_type")?.asString ?: "text"
                    val body = msg.getAsJsonObject("body")
                    val msgContent = body?.get("content")?.asString ?: ""
                    val summary = formatSubMessage(msgType, msgContent)
                    sb.appendLine("- $summary")
                }
            }

            if (messages.size() > 50) {
                sb.appendLine("... (共 ${messages.size()} 条)")
            }

            sb.toString().trimEnd()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse merge forward", e)
            "[合并转发消息]"
        }
    }

    /**
     * 格式化子消息摘要
     */
    private fun formatSubMessage(msgType: String, content: String): String {
        return when (msgType) {
            "text" -> {
                try {
                    val json = gson.fromJson(content, JsonObject::class.java)
                    val text = json.get("text")?.asString ?: content
                    if (text.length > 100) text.take(100) + "..." else text
                } catch (e: Exception) {
                    if (content.length > 100) content.take(100) + "..." else content
                }
            }
            "image" -> "[图片]"
            "file" -> "[文件]"
            "audio" -> "[语音]"
            "video" -> "[视频]"
            "sticker" -> "[表情]"
            "post" -> "[富文本]"
            "share_chat" -> "[分享群]"
            "share_user" -> "[分享用户]"
            else -> "[$msgType]"
        }
    }
}
