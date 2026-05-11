package com.ai.assistance.operit.core.skillrecorder

import com.ai.assistance.operit.util.AppLogger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * 根据触摸坐标在 UI 层级树中查找被点击的元素。
 *
 * UI hierarchy XML 中每个 node 都有 bounds 属性，格式为 "[left,top][right,bottom]"。
 * 给定一个 (x, y) 坐标，找到最小的（最深层的）包含该坐标的可点击节点。
 */
object CoordinateElementMatcher {

    private const val TAG = "CoordinateElementMatcher"

    data class MatchedElement(
        val text: String?,
        val contentDescription: String?,
        val resourceId: String?,
        val className: String?,
        val bounds: String
    )

    /**
     * 在 UI hierarchy XML 中查找坐标 (x, y) 命中的最具体的可交互元素。
     *
     * 策略：
     * 1. 找所有 bounds 包含 (x,y) 的节点
     * 2. 优先选择 clickable=true 的节点
     * 3. 在 clickable 节点中选面积最小的（最深层/最具体）
     * 4. 如果没有 clickable 节点，选所有命中节点中面积最小且有 text/desc/id 的
     */
    fun findElementAtCoordinate(uiHierarchyXml: String, x: Int, y: Int): MatchedElement? {
        if (uiHierarchyXml.isBlank()) return null

        return try {
            val candidates = mutableListOf<CandidateNode>()

            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser().apply { setInput(StringReader(uiHierarchyXml)) }

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val bounds = parser.getAttributeValue(null, "bounds") ?: ""
                    val rect = parseBounds(bounds)

                    if (rect != null && rect.contains(x, y)) {
                        val text = parser.getAttributeValue(null, "text")?.takeIf { it.isNotBlank() }
                        val desc = parser.getAttributeValue(null, "content-desc")?.takeIf { it.isNotBlank() }
                        val resId = parser.getAttributeValue(null, "resource-id")?.takeIf { it.isNotBlank() }
                        val cls = parser.getAttributeValue(null, "class")?.takeIf { it.isNotBlank() }
                        val clickable = parser.getAttributeValue(null, "clickable") == "true"

                        candidates.add(CandidateNode(
                            text = text,
                            contentDescription = desc,
                            resourceId = resId,
                            className = cls,
                            bounds = bounds,
                            clickable = clickable,
                            area = rect.area()
                        ))
                    }
                }
                parser.next()
            }

            if (candidates.isEmpty()) {
                AppLogger.d(TAG, "No elements found at ($x, $y)")
                return null
            }

            // 策略 1: 优先选 clickable 节点中面积最小的
            val clickables = candidates.filter { it.clickable }
            val best = if (clickables.isNotEmpty()) {
                clickables.minByOrNull { it.area }
            } else {
                // 策略 2: 没有 clickable 节点，选有标识信息且面积最小的
                val withInfo = candidates.filter {
                    it.text != null || it.contentDescription != null || it.resourceId != null
                }
                if (withInfo.isNotEmpty()) {
                    withInfo.minByOrNull { it.area }
                } else {
                    // 最后 fallback: 面积最小的任意节点
                    candidates.minByOrNull { it.area }
                }
            }

            best?.let {
                // 如果 best 节点没有 text，尝试从子节点/同级获取
                val finalText = it.text ?: findTextFromNeighbors(candidates, it)
                MatchedElement(
                    text = finalText,
                    contentDescription = it.contentDescription,
                    resourceId = it.resourceId,
                    className = simplifyClassName(it.className),
                    bounds = it.bounds
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "匹配坐标元素失败: ${e.message}")
            null
        }
    }

    /**
     * 如果最佳匹配节点没有 text，从其子节点（更小面积且被同一点命中）获取 text。
     */
    private fun findTextFromNeighbors(candidates: List<CandidateNode>, target: CandidateNode): String? {
        // 找比 target 面积更小的同位置节点中有 text 的
        return candidates
            .filter { it.area < target.area && it.text != null }
            .minByOrNull { it.area }
            ?.text
    }

    /**
     * 简化 Android 类名（去掉包前缀）。
     * android.widget.Button → Button
     * android.widget.TextView → TextView
     */
    private fun simplifyClassName(fullName: String?): String? {
        if (fullName == null) return null
        return fullName.substringAfterLast(".")
    }

    private data class CandidateNode(
        val text: String?,
        val contentDescription: String?,
        val resourceId: String?,
        val className: String?,
        val bounds: String,
        val clickable: Boolean,
        val area: Int
    )

    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
        fun area(): Int = (right - left) * (bottom - top)
    }

    /**
     * 解析 bounds 属性。格式: "[left,top][right,bottom]"
     */
    private fun parseBounds(bounds: String): Rect? {
        if (bounds.isBlank()) return null
        return try {
            // "[0,0][1080,2340]" → extract 4 numbers
            val nums = Regex("\\d+").findAll(bounds).map { it.value.toInt() }.toList()
            if (nums.size >= 4) {
                Rect(nums[0], nums[1], nums[2], nums[3])
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
