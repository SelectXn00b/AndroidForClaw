package com.ai.assistance.operit.data.repository

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-AGENT-009 UI 可视化补完 — 节点颜色选取契约。
 *
 * 测 [pickNodeColorByAttributes]（纯逻辑，不依赖 ObjectBox ToMany）：
 *  - TC-AGENT-243-a: `#persistent_instruction` 优先级最高，覆盖文档紫、Person/Concept
 *  - TC-AGENT-243-b: 不带 persistent_instruction 时颜色与改动前一致（无回归）
 *
 * 对应 docs/hermes-test-cases.md TC-AGENT-243-a / TC-AGENT-243-b。
 */
class MemoryNodeColorTest {

    private val gold = Color(0xFFFFB300)
    private val purple = Color(0xFF9575CD)
    private val green = Color(0xFF81C784)
    private val blue = Color(0xFF64B5F6)
    private val gray = Color.LightGray

    // ===== TC-AGENT-243-a: persistent_instruction takes precedence =====

    /** 仅有 #persistent_instruction → 金色。 */
    @Test
    fun `TC-AGENT-243-a only persistent_instruction returns gold`() {
        val color = pickNodeColorByAttributes(listOf("#persistent_instruction"), isDocumentNode = false)
        assertEquals(gold, color)
    }

    /** #persistent_instruction + Person → 仍是金色（覆盖 Person 绿）。 */
    @Test
    fun `TC-AGENT-243-a persistent_instruction overrides Person`() {
        val color = pickNodeColorByAttributes(listOf("Person", "#persistent_instruction"), isDocumentNode = false)
        assertEquals(gold, color)
    }

    /** #persistent_instruction + Concept → 仍是金色（覆盖 Concept 蓝）。 */
    @Test
    fun `TC-AGENT-243-a persistent_instruction overrides Concept`() {
        val color = pickNodeColorByAttributes(listOf("Concept", "#persistent_instruction"), isDocumentNode = false)
        assertEquals(gold, color)
    }

    /** #persistent_instruction + isDocumentNode=true → 仍是金色（覆盖文档紫）。 */
    @Test
    fun `TC-AGENT-243-a persistent_instruction overrides document node`() {
        val color = pickNodeColorByAttributes(listOf("#persistent_instruction"), isDocumentNode = true)
        assertEquals(gold, color)
    }

    /** #persistent_instruction + 一堆其它 tag → 仍是金色。 */
    @Test
    fun `TC-AGENT-243-a persistent_instruction with many other tags returns gold`() {
        val color = pickNodeColorByAttributes(
                listOf("foo", "bar", "Concept", "#persistent_instruction", "baz"),
                isDocumentNode = false
        )
        assertEquals(gold, color)
    }

    // ===== TC-AGENT-243-b: existing colors preserved (no regression) =====

    /** 空 tag + 非文档 → 灰色（原行为）。 */
    @Test
    fun `TC-AGENT-243-b no tags returns gray`() {
        val color = pickNodeColorByAttributes(emptyList(), isDocumentNode = false)
        assertEquals(gray, color)
    }

    /** 仅 Person → 绿色（原行为）。 */
    @Test
    fun `TC-AGENT-243-b Person tag returns green`() {
        val color = pickNodeColorByAttributes(listOf("Person"), isDocumentNode = false)
        assertEquals(green, color)
    }

    /** 仅 Concept → 蓝色（原行为）。 */
    @Test
    fun `TC-AGENT-243-b Concept tag returns blue`() {
        val color = pickNodeColorByAttributes(listOf("Concept"), isDocumentNode = false)
        assertEquals(blue, color)
    }

    /** isDocumentNode=true 不带 persistent_instruction → 紫色（原行为）。 */
    @Test
    fun `TC-AGENT-243-b document node returns purple`() {
        val color = pickNodeColorByAttributes(emptyList(), isDocumentNode = true)
        assertEquals(purple, color)
    }

    /** isDocumentNode=true + Person tag → 仍紫（文档优先于普通 tag）。 */
    @Test
    fun `TC-AGENT-243-b document node beats Person tag`() {
        val color = pickNodeColorByAttributes(listOf("Person"), isDocumentNode = true)
        assertEquals(purple, color)
    }

    /** 不识别的 tag → 灰色（原行为）。 */
    @Test
    fun `TC-AGENT-243-b unknown tag returns gray`() {
        val color = pickNodeColorByAttributes(listOf("random_tag"), isDocumentNode = false)
        assertEquals(gray, color)
    }
}
