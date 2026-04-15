package com.xiaomo.androidforclaw.hermes.environments

/**
 * Tool Call Parser - 模型 tool call 格式解析器
 * 1:1 对齐 hermes/environments/tool_call_parsers/
 *
 * 支持 12 种模型的非标准 tool call 格式解析：
 * - deepseek_v3, deepseek_v3_1
 * - glm45, glm47
 * - qwen, qwen3_coder
 * - kimi_k2, longcat, llama, mistral
 * - hermes (标准格式)
 */
abstract class ToolCallParser {

    /** 支持的模型标识 */
    abstract val supportedModels: List<String>

    /**
     * 从 LLM 响应中解析 tool calls
     *
     * @param response LLM 原始响应文本
     * @return 解析出的 tool calls
     */
    abstract fun parseToolCalls(response: String): List<ParsedToolCall>

    /**
     * 将 tool calls 格式化为模型期望的格式
     *
     * @param toolCalls tool calls
     * @return 格式化后的字符串
     */
    abstract fun formatToolCalls(toolCalls: List<ParsedToolCall>): String

    /**
     * 判断响应是否包含 tool call
     */
    abstract fun hasToolCall(response: String): Boolean
}

/**
 * 解析出的 tool call
 */
data class ParsedToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>,
    val rawArguments: String? = null  // 原始参数字符串
)

/**
 * Parser 注册表
 * 对应 hermes/environments/tool_call_parsers/__init__.py
 */
class ToolCallParserRegistry {

    private val parsers: MutableMap<String, ToolCallParser> = mutableMapOf()

    /**
     * 注册 parser
     */
    fun register(parser: ToolCallParser) {
        for (model in parser.supportedModels) {
            parsers[model] = parser
        }
    }

    /**
     * 获取模型对应的 parser
     */
    fun getParser(modelName: String): ToolCallParser? {
        return parsers[modelName]
    }

    /**
     * 根据模型名自动匹配 parser
     */
    fun findParser(modelName: String): ToolCallParser? {
        // 精确匹配
        parsers[modelName]?.let { return it }

        // 模糊匹配
        for ((key, parser) in parsers) {
            if (modelName.contains(key, ignoreCase = true)) {
                return parser
            }
        }

        // 默认返回 null (模型不支持 tool call 解析)
        return null
    }

    companion object {
        /** 内置 parsers */
        val BUILT_IN_PARSERS: List<ToolCallParser> = listOf(
            // TODO: 添加每个 parser 的实现
            // DeepSeekV3Parser, DeepSeekV31Parser, GLM45Parser, GLM47Parser,
            // QwenParser, Qwen3CoderParser, KimiK2Parser, LongcatParser,
            // LlamaParser, MistralParser, HermesParser
        )
    }
}
