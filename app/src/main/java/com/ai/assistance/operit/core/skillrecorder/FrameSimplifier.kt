package com.ai.assistance.operit.core.skillrecorder

import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * 帧压缩器：将 UI 层级 XML 压缩为紧凑文本摘要，
 * 并可合并连续相似事件以节省 LLM token。
 */
object FrameSimplifier {

    /**
     * 将 SimplifiedUINode 树转为紧凑文本。
     * 只保留有意义的节点（可点击、有文本、有描述）。
     */
    fun nodeTreeToText(root: SimplifiedUINode): String {
        return root.toTreeString()
    }

    /**
     * 合并连续相似帧：
     * - 连续 SCROLL 事件合并为一条
     * - 连续同一 Activity 的相同事件类型合并
     */
    fun condenseFrames(frames: List<RecordingFrame>): List<RecordingFrame> {
        if (frames.size <= 1) return frames

        val result = mutableListOf<RecordingFrame>()
        var i = 0

        while (i < frames.size) {
            val current = frames[i]

            // 合并连续 SCROLL 事件
            if (current.eventType == "SCROLL") {
                var scrollEnd = i
                while (scrollEnd + 1 < frames.size &&
                    frames[scrollEnd + 1].eventType == "SCROLL" &&
                    frames[scrollEnd + 1].activityName == current.activityName
                ) {
                    scrollEnd++
                }
                val scrollCount = scrollEnd - i + 1
                result.add(
                    current.copy(
                        eventDetails = current.eventDetails.copy(
                            additionalData = current.eventDetails.additionalData +
                                ("mergedCount" to scrollCount.toString())
                        )
                    )
                )
                i = scrollEnd + 1
            } else {
                result.add(current)
                i++
            }
        }
        return result
    }

    /**
     * 将帧列表格式化为 LLM prompt 文本。
     * 输出完整的定位信息：packageName、activityName、className、text、contentDescription、resourceId。
     * 对于 CLICK 事件，同时展示操作前后的 UI 状态帮助 AI 推断点击目标。
     * UI 层级使用简化树形式（只保留可交互/有内容的元素），而非原始 XML。
     */
    fun framesToPromptText(frames: List<RecordingFrame>): String {
        val sb = StringBuilder()
        for (frame in frames) {
            val pkg = frame.packageName ?: ""
            val activity = frame.activityName ?: "unknown"
            sb.appendLine("### Step ${frame.index + 1} (Activity: $activity, 包名: $pkg)")

            val eventDesc = buildEventDescription(frame)
            sb.appendLine("事件: $eventDesc")

            // For CLICK events from UI polling, show before/after UI to help AI infer click target
            if (frame.eventType == "CLICK" && frame.previousUiHierarchy.isNotBlank()) {
                val prevTree = summarizeUiHierarchy(frame.previousUiHierarchy)
                if (prevTree.isNotBlank()) {
                    sb.appendLine("操作前UI (可交互元素，▶=可点击):")
                    sb.appendLine(prevTree)
                    sb.appendLine()
                }
            }

            if (frame.uiHierarchySummary.isNotBlank()) {
                val afterTree = summarizeUiHierarchy(frame.uiHierarchySummary)
                if (afterTree.isNotBlank()) {
                    sb.appendLine("操作后UI:")
                    sb.appendLine(afterTree)
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    /**
     * Convert raw UI hierarchy XML to a compact tree text showing only meaningful elements.
     * Uses the same logic as SimplifiedUINode.toTreeString() — clickable nodes get ▶ prefix,
     * only nodes with text/contentDesc/resourceId/clickable are kept.
     * Falls back to truncated raw XML if parsing fails.
     */
    private fun summarizeUiHierarchy(xml: String): String {
        if (xml.isBlank()) return ""
        return try {
            val tree = parseXmlToSimplifiedNode(xml)
            val treeText = tree.toTreeString()
            // Limit output to prevent excessive prompt length
            if (treeText.length > 3000) {
                treeText.take(3000) + "\n... (截断)"
            } else {
                treeText
            }
        } catch (_: Exception) {
            // Fallback: if XML parsing fails, return truncated raw content
            if (xml.length > 800) xml.take(800) + "\n... (截断)" else xml
        }
    }

    /**
     * Parse UI hierarchy XML into a SimplifiedUINode tree.
     * Same logic as AccessibilityUITools.simplifyLayout but standalone (no class instantiation needed).
     */
    private fun parseXmlToSimplifiedNode(xml: String): SimplifiedUINode {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        data class TempNode(
            val className: String?,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String?,
            val isClickable: Boolean,
            val children: MutableList<TempNode> = mutableListOf()
        )

        val nodeStack = mutableListOf<TempNode>()
        var rootNode: TempNode? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val node = TempNode(
                            className = parser.getAttributeValue(null, "class")?.substringAfterLast('.'),
                            text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n"),
                            contentDesc = parser.getAttributeValue(null, "content-desc"),
                            resourceId = parser.getAttributeValue(null, "resource-id"),
                            bounds = parser.getAttributeValue(null, "bounds"),
                            isClickable = parser.getAttributeValue(null, "clickable") == "true"
                        )
                        if (rootNode == null) {
                            rootNode = node
                            nodeStack.add(node)
                        } else {
                            nodeStack.lastOrNull()?.children?.add(node)
                            nodeStack.add(node)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                    }
                }
            }
            parser.next()
        }

        fun TempNode.toSimplified(): SimplifiedUINode = SimplifiedUINode(
            className = className,
            text = text,
            contentDesc = contentDesc,
            resourceId = resourceId,
            bounds = bounds,
            isClickable = isClickable,
            children = children.map { it.toSimplified() }
        )

        return rootNode?.toSimplified() ?: SimplifiedUINode(
            className = null, text = null, contentDesc = null,
            resourceId = null, bounds = null, isClickable = false, children = emptyList()
        )
    }

    private fun buildEventDescription(frame: RecordingFrame): String {
        val details = frame.eventDetails
        val mergedCount = details.additionalData["mergedCount"]

        return when (frame.eventType) {
            "CLICK" -> {
                val parts = mutableListOf<String>()
                parts.add("点击")
                if (details.className != null) parts.add("[${details.className}]")
                if (!details.text.isNullOrBlank()) parts.add("text=\"${details.text}\"")
                if (!details.contentDescription.isNullOrBlank()) parts.add("contentDescription=\"${details.contentDescription}\"")
                if (!details.resourceId.isNullOrBlank()) parts.add("resourceId=\"${details.resourceId}\"")
                // If no meaningful target info, hint AI to look at UI context
                if (details.text.isNullOrBlank() && details.contentDescription.isNullOrBlank() && details.resourceId.isNullOrBlank()) {
                    parts.add("(具体目标请参考下方UI上下文)")
                }
                parts.joinToString(" ")
            }
            "LONG_CLICK" -> {
                val parts = mutableListOf<String>()
                parts.add("长按")
                if (details.className != null) parts.add("[${details.className}]")
                if (!details.text.isNullOrBlank()) parts.add("text=\"${details.text}\"")
                if (!details.contentDescription.isNullOrBlank()) parts.add("contentDescription=\"${details.contentDescription}\"")
                if (!details.resourceId.isNullOrBlank()) parts.add("resourceId=\"${details.resourceId}\"")
                if (parts.size == 1) parts.add("元素")
                parts.joinToString(" ")
            }
            "TEXT_INPUT" -> {
                val input = details.inputText ?: details.text ?: ""
                val cls = details.className ?: "EditText"
                val ridPart = if (!details.resourceId.isNullOrBlank()) " resourceId=\"${details.resourceId}\"" else ""
                "输入文本 \"$input\" 到 [$cls]$ridPart"
            }
            "SCROLL" -> {
                if (mergedCount != null) "滚动 (${mergedCount}次)" else "滚动"
            }
            "SCREEN_CHANGE" -> {
                "页面切换到 ${frame.activityName ?: "新页面"}"
            }
            else -> frame.eventType
        }
    }
}
