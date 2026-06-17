package com.ai.assistance.operit.ui.features.memory.viewmodel

import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-AGENT-041-b (2026-06-17): `applyAutoRootFilterToGraph(graph, filter)` 顶层纯函数 —— 把 graph 按
 * `AutoRootFilter` 三态过滤。filter 与现有 `GatewayFilter` 正交（互不嵌套、互不干扰），由 VM
 * `refreshGraph()` 串成 `applyAutoRootFilterToGraph(applyGatewayFilterToGraph(baseGraph), filter)`。
 *
 * Node 的 root 身份从 `Node.metadata["tags"]`（逗号分隔字符串，由
 * `MemoryRepository.buildGraphFromMemories` 注入）读出，与 gateway filter 同源 —— 整个 filter 链
 * 不读 ObjectBox、不重新查询，纯 in-memory 转换。
 *
 * 因为是纯函数（输入 Graph + filter，输出 Graph），ROI 极高，走真单测。
 *
 * 对应 TC-AGENT-041-b-d/e/f。
 */
class MemoryViewModelAutoRootFilterTest {

    private fun fakeGraph(): Graph {
        // 5 节点：1 个三 root（summary / extracted / summary_id）+ 1 gateway + 1 Person
        val nodes = listOf(
            Node(
                id = "n-summary",
                label = "summary root",
                metadata = mapOf("tags" to "#auto_summary_root,#auto_root")
            ),
            Node(
                id = "n-extracted",
                label = "extracted root",
                metadata = mapOf("tags" to "#auto_extracted_root,#auto_root")
            ),
            Node(
                id = "n-summary-id",
                label = "summary id root",
                metadata = mapOf("tags" to "#auto_summary_id_root,#auto_root")
            ),
            Node(
                id = "n-gateway",
                label = "gateway from feishu",
                metadata = mapOf("tags" to "#gateway:feishu")
            ),
            Node(
                id = "n-person",
                label = "Alice",
                metadata = mapOf("tags" to "Person")
            ),
        )
        // 边：连一些跨节点边，验剪悬挂边
        val edges = listOf(
            Edge(id = 1L, sourceId = "n-summary", targetId = "n-person"),
            Edge(id = 2L, sourceId = "n-extracted", targetId = "n-gateway"),
            Edge(id = 3L, sourceId = "n-person", targetId = "n-gateway"),
        )
        return Graph(nodes, edges)
    }

    /**
     * TC-AGENT-041-b-d: HideAuto 屏蔽所有 #auto_root 节点，剪悬挂边。
     */
    @Test
    fun `TC-AGENT-041-b-d HideAuto removes all auto_root nodes`() {
        val graph = fakeGraph()

        val filtered = applyAutoRootFilterToGraph(graph, AutoRootFilter.HideAuto)

        assertEquals("HideAuto 应屏蔽 3 个 root 节点，剩 gateway + Person 共 2 节点", 2, filtered.nodes.size)
        val ids = filtered.nodes.map { it.id }.toSet()
        assertTrue("剩余应含 gateway 节点", ids.contains("n-gateway"))
        assertTrue("剩余应含 Person 节点", ids.contains("n-person"))
        // 边：edge 1 / 2 端点已被屏蔽，应剪掉；edge 3 (person↔gateway) 保留
        assertEquals("HideAuto 应只剩 1 条 person↔gateway 边", 1, filtered.edges.size)
        assertEquals(3L, filtered.edges[0].id)
    }

    /**
     * TC-AGENT-041-b-e: OnlyAuto(buckets) 只看选中的 bucket；空 set = 看全部三 root。
     */
    @Test
    fun `TC-AGENT-041-b-e OnlyAuto with bucket subset filters to chosen buckets`() {
        val graph = fakeGraph()

        // 子集：只选 summary
        val onlySummary = applyAutoRootFilterToGraph(
            graph,
            AutoRootFilter.OnlyAuto(setOf("#auto_summary_root"))
        )
        assertEquals(
            "OnlyAuto({#auto_summary_root}) 应只剩 summary 1 节点",
            1,
            onlySummary.nodes.size
        )
        assertEquals("n-summary", onlySummary.nodes[0].id)

        // 子集：选 summary + extracted
        val twoBuckets = applyAutoRootFilterToGraph(
            graph,
            AutoRootFilter.OnlyAuto(setOf("#auto_summary_root", "#auto_extracted_root"))
        )
        assertEquals(
            "OnlyAuto({summary, extracted}) 应剩 2 节点",
            2,
            twoBuckets.nodes.size
        )

        // 空 set：看全部三 root
        val all = applyAutoRootFilterToGraph(graph, AutoRootFilter.OnlyAuto(emptySet()))
        assertEquals(
            "OnlyAuto(emptySet) 应剩三 root 全部 3 节点（gateway+Person 被屏蔽）",
            3,
            all.nodes.size
        )
        val allIds = all.nodes.map { it.id }.toSet()
        assertTrue(allIds.contains("n-summary"))
        assertTrue(allIds.contains("n-extracted"))
        assertTrue(allIds.contains("n-summary-id"))
    }

    /**
     * TC-AGENT-041-b-f: All 透传 + filter 与 gateway filter 正交（输入已被 gateway filter 处理过的
     * graph，再过 auto-root filter，结果应与"两个 filter 都被独立应用"等价）。
     *
     * 模拟"先 gateway ExcludeGateway 再 auto-root HideAuto"链：
     *   先剪掉 gateway 节点 → 只剩 3 root + Person 共 4 节点 + 1 条 summary↔Person 边（其他端点被屏蔽）
     *   再过 HideAuto → 只剩 Person 1 节点 + 0 条边（summary↔person 的 summary 端被屏蔽）
     */
    @Test
    fun `TC-AGENT-041-b-f auto_root filter and gateway filter compose orthogonally`() {
        val graph = fakeGraph()

        // All 透传不动
        val all = applyAutoRootFilterToGraph(graph, AutoRootFilter.All)
        assertEquals("All 应透传：节点数不变", graph.nodes.size, all.nodes.size)
        assertEquals("All 应透传：边数不变", graph.edges.size, all.edges.size)

        // 模拟 gateway-filter 先跑：手搓"已剪掉 gateway 节点"的 graph
        val afterGatewayExclude = Graph(
            nodes = graph.nodes.filterNot { node ->
                node.metadata["tags"]?.contains("#gateway:") == true
            },
            edges = graph.edges.filter { it.sourceId != "n-gateway" && it.targetId != "n-gateway" }
        )
        assertEquals(
            "断言前置：gateway-exclude 后剩 3 root + Person = 4 节点",
            4,
            afterGatewayExclude.nodes.size
        )

        // 再叠 HideAuto：只剩 Person，0 边
        val composed = applyAutoRootFilterToGraph(afterGatewayExclude, AutoRootFilter.HideAuto)
        assertEquals(
            "gateway ExcludeGateway 与 auto_root HideAuto 叠加后应只剩 Person 1 节点",
            1,
            composed.nodes.size
        )
        assertEquals("n-person", composed.nodes[0].id)
        assertEquals(
            "两个 filter 都剪干净后应无边",
            0,
            composed.edges.size
        )
    }
}
