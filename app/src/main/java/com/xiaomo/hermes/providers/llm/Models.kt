package com.xiaomo.hermes.providers.llm

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/content-blocks.ts, pi-tools.types.ts
 */


import com.google.gson.annotations.SerializedName

/**
 * LLM 通用数据模型
 * Used to unify interfaces of different API providers
 *
 * Reference: OpenClaw src/agents/llm-types.ts
 */

// ============= Message Models =============

/**
 * Inline image attached to a message (base64-encoded).
 * Aligned with OpenClaw ImageContent (pi-ai).
 */
data class ImageBlock(
    val base64: String,
    val mimeType: String = "image/jpeg"
)

/**
 * 通用消息格式
 *
 * For multimodal messages the text goes in [content] and images in [images].
 * The API adapter will assemble them into the provider-specific content array.
 */
data class Message(
    val role: String,  // "system", "user", "assistant", "tool"
    val content: String,
    val name: String? = null,  // tool name for tool role
    val toolCallId: String? = null,  // for tool role
    val toolCalls: List<ToolCall>? = null,  // for assistant with tool calls
    val images: List<ImageBlock>? = null  // inline images (user messages & tool results)
)

/**
 * Tool Call（工具调用）
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String  // JSON string
)

// ============= Tool Definition Models =============

/**
 * 工具定义
 */
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
) {
    override fun toString(): String {
        return """{"type":"$type","function":${function}}"""
    }
}

/**
 * 函数定义
 */
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: ParametersSchema
) {
    override fun toString(): String {
        return """{"name":"$name","description":"$description","parameters":${parameters}}"""
    }
}

/**
 * 参数 Schema
 */
data class ParametersSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList()
) {
    override fun toString(): String {
        val props = properties.entries.joinToString(",") { (key, value) ->
            """"$key":${value}"""
        }
        val req = required.joinToString(",") { """"$it"""" }
        return """{"type":"$type","properties":{$props},"required":[$req]}"""
    }
}

/**
 * 属性 Schema
 */
data class PropertySchema(
    val type: String,  // "string", "number", "boolean", "array", "object"
    val description: String,
    val enum: List<String>? = null,
    val items: PropertySchema? = null,  // for array type
    val properties: Map<String, PropertySchema>? = null  // for object type
) {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        parts.add(""""type":"$type"""")
        parts.add(""""description":"$description"""")
        enum?.let {
            val enumStr = it.joinToString(",") { v -> """"$v"""" }
            parts.add(""""enum":[$enumStr]""")
        }
        items?.let {
            parts.add(""""items":${it}""")
        }
        properties?.let { props ->
            val propsStr = props.entries.joinToString(",") { (k, v) ->
                """"$k":${v}"""
            }
            parts.add(""""properties":{$propsStr}""")
        }
        return "{${parts.joinToString(",")}}"
    }
}

// ============= Response Models =============

/**
 * LLM 响应（通用格式）
 */
data class LLMResponse(
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val thinkingContent: String? = null,  // Extended Thinking content
    val usage: TokenUsage? = null,
    val finishReason: String? = null
)

/**
 * Token 使用统计
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// ============= Helper Extensions =============

/**
 * 将 Message 转换为适用于日志的简短描述
 */
fun Message.toLogString(): String {
    val preview = content.take(50) + if (content.length > 50) "..." else ""
    val imgCount = images?.size ?: 0
    val imgSuffix = if (imgCount > 0) ", images=$imgCount" else ""
    return "Message(role=$role, content=\"$preview\", toolCalls=${toolCalls?.size ?: 0}$imgSuffix)"
}

/**
 * 创建系统消息
 */
fun systemMessage(content: String) = Message(
    role = "system",
    content = content
)

/**
 * 创建用户消息
 */
fun userMessage(content: String) = Message(
    role = "user",
    content = content
)

/**
 * 创建带图片的用户消息 (multimodal)
 */
fun userMessage(content: String, images: List<ImageBlock>) = Message(
    role = "user",
    content = content,
    images = images.ifEmpty { null }
)

/**
 * 创建助手消息
 */
fun assistantMessage(
    content: String? = null,
    toolCalls: List<ToolCall>? = null
) = Message(
    role = "assistant",
    content = content ?: "",
    toolCalls = toolCalls
)

/**
 * 创建工具结果消息
 */
fun toolMessage(
    toolCallId: String,
    content: String,
    name: String? = null,
    images: List<ImageBlock>? = null
) = Message(
    role = "tool",
    content = content,
    toolCallId = toolCallId,
    name = name,
    images = images
)

// ============= Compatibility Extensions =============

/**
 * 从旧的 LegacyMessage 转换到新的 Message
 *
 * LegacyMessage.content can be:
 *   - String  → plain text
 *   - List<Map<String, Any?>>  → multimodal content blocks
 *     Each block has "type" key: "text" or "image_url" / "image"
 *
 * This extracts text parts into Message.content and image parts into Message.images,
 * instead of calling toString() on the whole structure (which was the old bug).
 */
fun com.xiaomo.hermes.providers.LegacyMessage.toNewMessage(): Message {
    return when (val c = this.content) {
        is String -> Message(
            role = this.role,
            content = c,
            name = this.name,
            toolCallId = this.toolCallId,
            toolCalls = this.toolCalls?.map { tc ->
                ToolCall(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
            }
        )
        is List<*> -> {
            // Parse multimodal content blocks
            val textParts = mutableListOf<String>()
            val imageBlocks = mutableListOf<ImageBlock>()

            for (item in c) {
                if (item !is Map<*, *>) continue
                when (item["type"]) {
                    "text" -> {
                        val text = item["text"] as? String
                        if (!text.isNullOrBlank()) textParts.add(text)
                    }
                    "image_url" -> {
                        // OpenAI format: { type: "image_url", image_url: { url: "data:image/jpeg;base64,..." } }
                        val imageUrl = item["image_url"]
                        val url = when (imageUrl) {
                            is Map<*, *> -> imageUrl["url"] as? String
                            is String -> imageUrl
                            else -> null
                        }
                        if (url != null && url.startsWith("data:")) {
                            val parts = url.removePrefix("data:").split(";base64,", limit = 2)
                            if (parts.size == 2) {
                                imageBlocks.add(ImageBlock(
                                    base64 = parts[1],
                                    mimeType = parts[0]
                                ))
                            }
                        }
                    }
                    "image" -> {
                        // Anthropic format: { type: "image", source: { type: "base64", media_type: "...", data: "..." } }
                        val source = item["source"] as? Map<*, *>
                        val data = source?.get("data") as? String
                        val mediaType = source?.get("media_type") as? String ?: "image/jpeg"
                        if (!data.isNullOrBlank()) {
                            imageBlocks.add(ImageBlock(base64 = data, mimeType = mediaType))
                        }
                    }
                }
            }

            Message(
                role = this.role,
                content = textParts.joinToString("\n").ifBlank { "" },
                name = this.name,
                toolCallId = this.toolCallId,
                toolCalls = this.toolCalls?.map { tc ->
                    ToolCall(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
                },
                images = imageBlocks.ifEmpty { null }
            )
        }
        else -> Message(
            role = this.role,
            content = c?.toString() ?: "",
            name = this.name,
            toolCallId = this.toolCallId,
            toolCalls = this.toolCalls?.map { tc ->
                ToolCall(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
            }
        )
    }
}

/**
 * 从新的 Message 转换到旧的 LegacyMessage
 *
 * If the message carries images, content is stored as List<Map> (multimodal blocks)
 * so it round-trips correctly through session persistence.
 */
fun Message.toLegacyMessage(): com.xiaomo.hermes.providers.LegacyMessage {
    // Build multimodal content if images present
    val legacyContent: Any = if (!images.isNullOrEmpty()) {
        buildList {
            if (content.isNotBlank()) {
                add(mapOf("type" to "text", "text" to content))
            }
            for (img in images!!) {
                add(mapOf(
                    "type" to "image",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to img.mimeType,
                        "data" to img.base64
                    )
                ))
            }
        }
    } else {
        content
    }

    return com.xiaomo.hermes.providers.LegacyMessage(
        role = this.role,
        content = legacyContent,
        name = this.name,
        toolCallId = this.toolCallId,
        toolCalls = this.toolCalls?.map { tc ->
            com.xiaomo.hermes.providers.LegacyToolCall(
                id = tc.id,
                type = "function",
                function = com.xiaomo.hermes.providers.LegacyFunction(
                    name = tc.name,
                    arguments = tc.arguments
                )
            )
        }
    )
}
