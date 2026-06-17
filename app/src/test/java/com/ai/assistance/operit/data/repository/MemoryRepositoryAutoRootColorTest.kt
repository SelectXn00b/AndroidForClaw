package com.ai.assistance.operit.data.repository

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R-AGENT-041-b (2026-06-17): `pickNodeColorByAttributes` 必须为 R-AGENT-038 的三 root 节点提供
 * bucket 专属色，且优先级排在 `#persistent_instruction` (gold) / `isDocumentNode` (purple) 之后、
 * `#gateway:*` (teal) / Person/Concept / LightGray 之前。
 *
 * 三 bucket 色（与 docs/hermes-requirements.md R-AGENT-041-b 锁定）：
 *   #auto_summary_root   → Color(0xFFEF5350) 红
 *   #auto_extracted_root → Color(0xFFFFA726) 橘
 *   #auto_summary_id_root → Color(0xFFAB47BC) 紫红（与现有 PURPLE 0xFF9575CD 区分）
 *
 * pure-logic 测试，与 `MemoryRepositoryGatewayColorTest` (R-AGENT-012) 同款。
 * 对应 TC-AGENT-041-b-a/b/c。
 */
class MemoryRepositoryAutoRootColorTest {

    private val GOLD = Color(0xFFFFB300)
    private val PURPLE = Color(0xFF9575CD)
    private val GATEWAY = Color(0xFF26A69A)
    private val GREEN = Color(0xFF81C784)
    private val BLUE = Color(0xFF64B5F6)
    private val DEFAULT = Color.LightGray

    private val AUTO_SUMMARY = Color(0xFFEF5350)
    private val AUTO_EXTRACTED = Color(0xFFFFA726)
    private val AUTO_SUMMARY_ID = Color(0xFFAB47BC)

    /**
     * TC-AGENT-041-b-a: `#auto_summary_root` (+ shared `#auto_root`) 返回红色。
     */
    @Test
    fun `TC-AGENT-041-b-a auto_summary_root tag returns red`() {
        assertEquals(
            "`#auto_summary_root` 应返回红色 0xFFEF5350",
            AUTO_SUMMARY,
            pickNodeColorByAttributes(
                listOf("#auto_summary_root", "#auto_root"),
                isDocumentNode = false
            )
        )
        // 单独不带 #auto_root 也应识别（防御：MemoryArchiver 一定会加 #auto_root，但
        // pickNode 不应该硬依赖那条 family tag 才工作）
        assertEquals(
            "仅 `#auto_summary_root` 单 tag 也应返回红色",
            AUTO_SUMMARY,
            pickNodeColorByAttributes(listOf("#auto_summary_root"), isDocumentNode = false)
        )
    }

    /**
     * TC-AGENT-041-b-b: `#auto_extracted_root` 与 `#auto_summary_id_root` 各自返回独立色，
     * 不互窜。
     */
    @Test
    fun `TC-AGENT-041-b-b auto_extracted and auto_summary_id roots return their own colors`() {
        assertEquals(
            "`#auto_extracted_root` 应返回橘色 0xFFFFA726",
            AUTO_EXTRACTED,
            pickNodeColorByAttributes(
                listOf("#auto_extracted_root", "#auto_root"),
                isDocumentNode = false
            )
        )
        assertEquals(
            "`#auto_summary_id_root` 应返回紫红 0xFFAB47BC（与 PURPLE 区分）",
            AUTO_SUMMARY_ID,
            pickNodeColorByAttributes(
                listOf("#auto_summary_id_root", "#auto_root"),
                isDocumentNode = false
            )
        )
    }

    /**
     * TC-AGENT-041-b-c: 优先级排序守门：`#persistent_instruction` 优于 root，root 优于 `#gateway:*`。
     *
     * 排序（高 → 低）：
     *   #persistent_instruction (gold) > isDocumentNode (purple) > root (3 色) >
     *   #gateway:* (teal) > Person/Concept > LightGray
     */
    @Test
    fun `TC-AGENT-041-b-c root tag priority sits between persistent_instruction and gateway`() {
        // persistent_instruction > root：金色（持久指令优先）
        assertEquals(
            "`#persistent_instruction` 与 `#auto_summary_root` 共存时返回金色",
            GOLD,
            pickNodeColorByAttributes(
                listOf("#persistent_instruction", "#auto_summary_root", "#auto_root"),
                isDocumentNode = false
            )
        )
        // isDocumentNode > root：紫色（文档结构属性优先）
        assertEquals(
            "`isDocumentNode=true` 与 `#auto_summary_root` 共存时返回紫色",
            PURPLE,
            pickNodeColorByAttributes(
                listOf("#auto_summary_root", "#auto_root"),
                isDocumentNode = true
            )
        )
        // root > gateway：返回 root 色（auto-summary 红，比 gateway teal 更结构化）
        assertEquals(
            "`#auto_summary_root` 与 `#gateway:feishu` 共存时返回红色（root 优先于 gateway）",
            AUTO_SUMMARY,
            pickNodeColorByAttributes(
                listOf("#auto_summary_root", "#auto_root", "#gateway:feishu"),
                isDocumentNode = false
            )
        )
        // 无 root tag 时不能误伤现有逻辑：gateway 仍走 teal
        assertEquals(
            "无 root tag、含 `#gateway:wechat` 应返回 gateway teal（原有逻辑不被破坏）",
            GATEWAY,
            pickNodeColorByAttributes(listOf("#gateway:wechat"), isDocumentNode = false)
        )
        // 无 root tag 时 Person 走绿
        assertEquals(
            "无 root tag、首 tag = Person 应返回绿色（原有逻辑）",
            GREEN,
            pickNodeColorByAttributes(listOf("Person"), isDocumentNode = false)
        )
        // 无 root tag 时 Concept 走蓝
        assertEquals(
            "无 root tag、首 tag = Concept 应返回蓝色（原有逻辑）",
            BLUE,
            pickNodeColorByAttributes(listOf("Concept"), isDocumentNode = false)
        )
        // 默认 LightGray
        assertEquals(
            "无任何 tag 应返回 LightGray",
            DEFAULT,
            pickNodeColorByAttributes(emptyList(), isDocumentNode = false)
        )
    }
}
