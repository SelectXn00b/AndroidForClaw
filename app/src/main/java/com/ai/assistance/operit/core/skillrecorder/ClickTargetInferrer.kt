package com.ai.assistance.operit.core.skillrecorder

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * 点击目标推断器：通过对比操作前后的 UI 层级树，推断用户点击了哪个元素。
 *
 * 推断策略：
 * 1. 从操作前 UI 中提取所有可点击元素
 * 2. 从操作后 UI 中提取所有可点击元素
 * 3. 对比两棵树，找出"状态变化"的可点击元素（选中/焦点/消失/子内容变化）
 * 4. 如果找到唯一匹配，则为点击目标
 * 5. 如果找到多个候选，按优先级排序（selected > focused > disappeared > children_changed）
 */
object ClickTargetInferrer {

    data class ClickableElement(
        val className: String?,
        val text: String?,
        val contentDescription: String?,
        val resourceId: String?,
        val bounds: String?,
        val isSelected: Boolean,
        val isFocused: Boolean,
        val isChecked: Boolean,
        val childSignature: String // hash of children content for change detection
    )

    data class InferredTarget(
        val className: String?,
        val text: String?,
        val contentDescription: String?,
        val resourceId: String?,
        val confidence: Confidence
    )

    enum class Confidence {
        HIGH,    // Single obvious match (state change, selection, or unique disappearance)
        MEDIUM,  // Multiple candidates but one is most likely
        LOW      // Best guess from available data
    }

    /**
     * 从操作前后 UI 对比中推断点击目标。
     * @param beforeXml 操作前 UI hierarchy XML
     * @param afterXml 操作后 UI hierarchy XML
     * @return 推断的点击目标，或 null 如果无法推断
     */
    fun inferClickTarget(beforeXml: String, afterXml: String): InferredTarget? {
        if (beforeXml.isBlank() || afterXml.isBlank()) return null

        val beforeElements = try {
            extractClickableElements(beforeXml)
        } catch (_: Exception) {
            return null
        }

        val afterElements = try {
            extractClickableElements(afterXml)
        } catch (_: Exception) {
            return null
        }

        if (beforeElements.isEmpty()) return null

        // Strategy 1: Find elements whose selected/checked/focused state changed
        val stateChangedElements = findStateChangedElements(beforeElements, afterElements)
        if (stateChangedElements.size == 1) {
            val el = stateChangedElements[0]
            return InferredTarget(
                className = el.className,
                text = el.text,
                contentDescription = el.contentDescription,
                resourceId = el.resourceId,
                confidence = Confidence.HIGH
            )
        }

        // Strategy 2: Find clickable elements that disappeared from the tree
        // (e.g., a button that triggered navigation to a new page)
        val disappearedElements = findDisappearedClickables(beforeElements, afterElements)
        if (disappearedElements.size == 1) {
            val el = disappearedElements[0]
            return InferredTarget(
                className = el.className,
                text = el.text,
                contentDescription = el.contentDescription,
                resourceId = el.resourceId,
                confidence = Confidence.HIGH
            )
        }

        // Strategy 3: Find clickable elements whose children changed
        // (e.g., a dropdown that expanded, a list that appeared)
        val childrenChangedElements = findChildrenChangedClickables(beforeElements, afterElements)
        if (childrenChangedElements.size == 1) {
            val el = childrenChangedElements[0]
            return InferredTarget(
                className = el.className,
                text = el.text,
                contentDescription = el.contentDescription,
                resourceId = el.resourceId,
                confidence = Confidence.MEDIUM
            )
        }

        // Strategy 4: Combine candidates and pick the best one
        val allCandidates = stateChangedElements + disappearedElements + childrenChangedElements
        if (allCandidates.isNotEmpty()) {
            // Prefer elements with text or resourceId (more useful for SKILL.md)
            val bestCandidate = allCandidates
                .sortedByDescending { scoreElement(it) }
                .first()
            return InferredTarget(
                className = bestCandidate.className,
                text = bestCandidate.text,
                contentDescription = bestCandidate.contentDescription,
                resourceId = bestCandidate.resourceId,
                confidence = if (allCandidates.size <= 3) Confidence.MEDIUM else Confidence.LOW
            )
        }

        return null
    }

    /**
     * Score an element by how informative it is for identification.
     */
    private fun scoreElement(el: ClickableElement): Int {
        var score = 0
        if (!el.text.isNullOrBlank()) score += 3
        if (!el.resourceId.isNullOrBlank()) score += 2
        if (!el.contentDescription.isNullOrBlank()) score += 2
        if (!el.className.isNullOrBlank()) score += 1
        return score
    }

    /**
     * Find clickable elements whose selected/checked/focused state changed between before and after.
     */
    private fun findStateChangedElements(
        before: List<ClickableElement>,
        after: List<ClickableElement>
    ): List<ClickableElement> {
        val result = mutableListOf<ClickableElement>()

        for (bEl in before) {
            // Match by resourceId or (text + className)
            val aEl = findMatchingElement(bEl, after) ?: continue

            // Check if state changed (became selected, focused, or checked)
            if ((!bEl.isSelected && aEl.isSelected) ||
                (!bEl.isFocused && aEl.isFocused) ||
                (!bEl.isChecked && aEl.isChecked)
            ) {
                result.add(bEl)
            }
        }

        return result
    }

    /**
     * Find clickable elements that existed before but don't exist after (page navigation).
     */
    private fun findDisappearedClickables(
        before: List<ClickableElement>,
        after: List<ClickableElement>
    ): List<ClickableElement> {
        // If ALL before elements disappeared (complete page change), don't use this strategy
        val afterIds = after.map { elementKey(it) }.toSet()
        val disappeared = before.filter { elementKey(it) !in afterIds }

        // Only useful if some elements survived (partial change) or the disappeared list is small
        if (disappeared.size == before.size) {
            // Complete page change - can't determine which element was clicked from disappearance alone
            // Return empty to fall through to other strategies
            return emptyList()
        }

        // Filter to only elements with useful identifying info
        return disappeared.filter { el ->
            !el.text.isNullOrBlank() || !el.resourceId.isNullOrBlank() || !el.contentDescription.isNullOrBlank()
        }
    }

    /**
     * Find clickable elements whose child content changed (expansion, collapse, etc.)
     */
    private fun findChildrenChangedClickables(
        before: List<ClickableElement>,
        after: List<ClickableElement>
    ): List<ClickableElement> {
        val result = mutableListOf<ClickableElement>()

        for (bEl in before) {
            val aEl = findMatchingElement(bEl, after) ?: continue
            if (bEl.childSignature != aEl.childSignature) {
                result.add(bEl)
            }
        }

        return result
    }

    /**
     * Find an element in the target list that matches the source element.
     */
    private fun findMatchingElement(source: ClickableElement, targets: List<ClickableElement>): ClickableElement? {
        // Priority 1: Match by resourceId (most reliable)
        if (!source.resourceId.isNullOrBlank()) {
            targets.find { it.resourceId == source.resourceId }?.let { return it }
        }

        // Priority 2: Match by text + className + bounds
        if (!source.text.isNullOrBlank()) {
            targets.find {
                it.text == source.text && it.className == source.className && it.bounds == source.bounds
            }?.let { return it }

            // Relax: just text + className
            targets.find {
                it.text == source.text && it.className == source.className
            }?.let { return it }
        }

        // Priority 3: Match by contentDescription + className
        if (!source.contentDescription.isNullOrBlank()) {
            targets.find {
                it.contentDescription == source.contentDescription && it.className == source.className
            }?.let { return it }
        }

        // Priority 4: Match by bounds + className (position-based)
        if (!source.bounds.isNullOrBlank()) {
            targets.find {
                it.bounds == source.bounds && it.className == source.className
            }?.let { return it }
        }

        return null
    }

    /**
     * Generate a key for element identity matching.
     */
    private fun elementKey(el: ClickableElement): String {
        return buildString {
            if (!el.resourceId.isNullOrBlank()) {
                append("id:${el.resourceId}")
            } else {
                append("${el.className}|${el.text}|${el.contentDescription}|${el.bounds}")
            }
        }
    }

    /**
     * Parse UI hierarchy XML and extract all clickable elements with their properties.
     */
    private fun extractClickableElements(xml: String): List<ClickableElement> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        data class ParsedNode(
            val className: String?,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String?,
            val isClickable: Boolean,
            val isSelected: Boolean,
            val isFocused: Boolean,
            val isChecked: Boolean,
            val children: MutableList<ParsedNode> = mutableListOf()
        )

        // Recursive child text finder
        fun findFirstChildText(nodes: List<ParsedNode>): String? {
            for (node in nodes) {
                if (!node.text.isNullOrBlank()) return node.text
                val childText = findFirstChildText(node.children)
                if (childText != null) return childText
            }
            return null
        }

        val nodeStack = mutableListOf<ParsedNode>()
        val allClickables = mutableListOf<ClickableElement>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val node = ParsedNode(
                            className = parser.getAttributeValue(null, "class")?.substringAfterLast('.'),
                            text = parser.getAttributeValue(null, "text")?.takeIf { it.isNotBlank() },
                            contentDesc = parser.getAttributeValue(null, "content-desc")?.takeIf { it.isNotBlank() },
                            resourceId = parser.getAttributeValue(null, "resource-id")?.takeIf { it.isNotBlank() },
                            bounds = parser.getAttributeValue(null, "bounds"),
                            isClickable = parser.getAttributeValue(null, "clickable") == "true",
                            isSelected = parser.getAttributeValue(null, "selected") == "true",
                            isFocused = parser.getAttributeValue(null, "focused") == "true",
                            isChecked = parser.getAttributeValue(null, "checked") == "true"
                        )
                        nodeStack.lastOrNull()?.children?.add(node)
                        nodeStack.add(node)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        val completed = nodeStack.removeLastOrNull()
                        if (completed != null && completed.isClickable) {
                            // Build child signature from immediate children's text content
                            val childSig = completed.children.joinToString("|") {
                                "${it.className}:${it.text}:${it.contentDesc}:${it.isSelected}:${it.isChecked}"
                            }
                            allClickables.add(
                                ClickableElement(
                                    className = completed.className,
                                    text = completed.text ?: findFirstChildText(completed.children),
                                    contentDescription = completed.contentDesc,
                                    resourceId = completed.resourceId,
                                    bounds = completed.bounds,
                                    isSelected = completed.isSelected,
                                    isFocused = completed.isFocused,
                                    isChecked = completed.isChecked,
                                    childSignature = childSig
                                )
                            )
                        }
                    }
                }
            }
            parser.next()
        }

        return allClickables
    }
}
