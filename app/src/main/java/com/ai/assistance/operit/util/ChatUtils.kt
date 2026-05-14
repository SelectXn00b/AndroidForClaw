package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.withContent

/** Utility functions for chat message handling */
object ChatUtils {
    fun stripGeminiThoughtSignatureMeta(content: String): String {
        return ChatMarkupRegex.removeGeminiThoughtSignatureMeta(content)
    }

    fun stripGeminiThoughtSignatureMeta(messages: List<Pair<String, String>>): List<Pair<String, String>> {
        return messages.map { (role, content) ->
            role to stripGeminiThoughtSignatureMeta(content)
        }
    }

    fun stripGeminiThoughtSignatureMetaTurns(messages: List<PromptTurn>): List<PromptTurn> {
        return messages.map { turn ->
            turn.withContent(stripGeminiThoughtSignatureMeta(turn.content))
        }
    }

    fun isGeminiProviderModel(providerModel: String): Boolean {
        return when (providerModel.substringBefore(":").uppercase()) {
            "GOOGLE", "GEMINI_GENERIC" -> true
            else -> false
        }
    }

    /** 过滤掉内容中的思考部分和搜索来源 移除<think></think>、<thinking></thinking>和<search></search>标签及其中的内容，并处理未闭合的情况 */
    fun removeThinkingContent(content: String): String {
        // 使用正则表达式匹配<think>、<thinking>和<search>标签及其内容
        // 这个正则表达式会匹配以下情况：
        // 1. <think>...</think> (正常闭合的标签)
        // 2. <think>... (未闭合，直到字符串末尾)
        // 3. <thinking>...</thinking> (正常闭合的标签)
        // 4. <thinking>... (未闭合，直到字符串末尾)
        // 5. <search>...</search> (正常闭合的标签)
        // 6. <search>... (未闭合，直到字符串末尾)
        // \\z 匹配字符串的绝对末尾
        val thinkPattern = "<think(?:ing)?>.*?(</think(?:ing)?>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        val searchPattern = "<search>.*?(</search>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        return content.replace(thinkPattern, "").replace(searchPattern, "").trim()
    }

    /**
     * 提取think标签内的内容（用于DeepSeek的reasoning_content）
     *
     * 同时处理：
     * 1. `<think>...</think>` / `<thinking>...</thinking>` 闭合标签 — 取标签内文本作为 reasoning
     * 2. `<think>...` / `<thinking>...` 未闭合（被截断 / 流式中断）— 把开始标签到字符串末尾整段当 reasoning
     *    避免脏数据（带开放 `<think>`）落库后污染下一轮请求历史，导致部分模型空回复（飞书无响应 bug）
     *
     * 行为与 [removeThinkingContent] 对齐（都用 `\z` fallback），消除两个工具函数的不一致。
     *
     * @param content 包含think标签的内容
     * @return Pair(移除think标签后的内容, think标签内的内容)
     */
    fun extractThinkingContent(content: String): Pair<String, String> {
        // 闭合标签优先匹配（贪婪到最近的 </think>）；如无闭合则吃到字符串末尾。
        // 使用两个独立 group 区分闭合 / 未闭合分支，便于提取捕获文本。
        val thinkPattern = "<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        val thinkMatches = thinkPattern.findAll(content)

        // 收集所有think标签内的内容
        val thinkingContent = thinkMatches.joinToString("\n") { it.groupValues[1].trim() }

        // 移除think标签和search标签（两者都支持未闭合 fallback）
        val contentWithoutThink = content
            .replace(thinkPattern, "")
            .replace("<search>[\\s\\S]*?(</search>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .trim()

        return Pair(contentWithoutThink, thinkingContent)
    }

    /**
     * 估算给定文本的token数量
     * @param text 要估算token的文本
     * @return 估算的token数量
     */
    fun estimateTokenCount(text: String): Int {
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount
        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }

    /**
     * 从 AI 响应中提取 JSON 对象部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJson(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 { 和最后一个 }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        
        return if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            text.substring(firstBrace, lastBrace + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            text
        }
    }

    /**
     * 从 AI 响应中提取 JSON 数组部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJsonArray(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 [ 和最后一个 ]
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        
        return if (firstBracket != -1 && lastBracket != -1 && firstBracket < lastBracket) {
            text.substring(firstBracket, lastBracket + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            text
        }
    }
}
