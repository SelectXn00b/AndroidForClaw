package com.ai.assistance.operit.data.repository

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-AGENT-012 (2026-06-06): `pickNodeColorByAttributes` 必须识别任意以 `#gateway:` 开头的 tag，
 * 返回 gateway 专属色 `Color(0xFF26A69A)`（蓝绿）。
 *
 * 优先级（与 R-012 需求文档 §"节点颜色区分"对齐）：
 *   #persistent_instruction (金 0xFFFFB300)  > isDocumentNode (紫 0xFF9575CD) >
 *   #gateway:* (蓝绿 0xFF26A69A) > tagNames.first() == "Person"/"Concept" > LightGray
 *
 * 这是 pure-logic 测试，直接 import `pickNodeColorByAttributes(tagNames, isDocumentNode)`。
 * 对应 TC-AGENT-248-a。
 */
class MemoryRepositoryGatewayColorTest {

    private val GOLD = Color(0xFFFFB300)
    private val PURPLE = Color(0xFF9575CD)
    private val GATEWAY = Color(0xFF26A69A)
    private val GREEN = Color(0xFF81C784)
    private val BLUE = Color(0xFF64B5F6)
    private val DEFAULT = Color.LightGray

    @Test
    fun `TC-AGENT-248-a only gateway tag returns gateway color`() {
        assertEquals(
            "仅 `#gateway:feishu` tag 应返回 gateway 蓝绿色 0xFF26A69A",
            GATEWAY,
            pickNodeColorByAttributes(listOf("#gateway:feishu"), isDocumentNode = false)
        )
    }

    @Test
    fun `TC-AGENT-248-a gateway tag with normal tag returns gateway color`() {
        // gateway tag 不一定在 list 首位，识别要扫描整个 list（startsWith 检查）
        assertEquals(
            "tag 列表含 `#gateway:wechat` 即使有其他普通 tag 也应返回 gateway 蓝绿色",
            GATEWAY,
            pickNodeColorByAttributes(listOf("topic", "#gateway:wechat"), isDocumentNode = false)
        )
    }

    @Test
    fun `TC-AGENT-248-a persistent_instruction takes priority over gateway`() {
        // 极端共存场景：persistent_instruction (gold) 优先于 gateway
        assertEquals(
            "`#persistent_instruction` 与 `#gateway:feishu` 共存时返回金色（持久指令优先）",
            GOLD,
            pickNodeColorByAttributes(
                listOf("#persistent_instruction", "#gateway:feishu"),
                isDocumentNode = false
            )
        )
    }

    @Test
    fun `TC-AGENT-248-a isDocumentNode takes priority over gateway`() {
        // isDocumentNode (purple) 优先于 gateway —— "这是文档"结构属性比"来自哪个 IM 平台"语义强
        assertEquals(
            "`isDocumentNode=true` 与 `#gateway:feishu` 共存时返回紫色（文档优先）",
            PURPLE,
            pickNodeColorByAttributes(listOf("#gateway:feishu"), isDocumentNode = true)
        )
    }

    @Test
    fun `TC-AGENT-248-a no gateway tag falls through to existing logic`() {
        // 不含 gateway tag 应走原有分支（Person/Concept/默认），不能误伤
        assertEquals(
            "无 gateway tag 且首 tag = Person 应返回绿色（原有逻辑）",
            GREEN,
            pickNodeColorByAttributes(listOf("Person"), isDocumentNode = false)
        )
        assertEquals(
            "无 gateway tag 且首 tag = Concept 应返回蓝色（原有逻辑）",
            BLUE,
            pickNodeColorByAttributes(listOf("Concept"), isDocumentNode = false)
        )
        assertEquals(
            "无任何 tag 应返回 LightGray（原有默认）",
            DEFAULT,
            pickNodeColorByAttributes(emptyList(), isDocumentNode = false)
        )
    }
}
