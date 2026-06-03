package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.data.model.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-003 memory dedup —— 写入侧 + 读出侧的双向防御契约。
 *
 * **Bug 背景**：`MemoryRepository.createMemory()` 缺失 Python 上游 `MemoryStore.add` 的
 * 去重逻辑（`if content in entries: return`），加上 `runSearchMemoriesWithDebug` 末尾的
 * `// deduplicateBySemantics(sortedMemories)` 一直被注释，导致 agent 累积大量语义相似
 * 节点，retrieve 出来的上下文又被这些重复污染，形成循环。
 *
 * **测试策略**：核心去重判定剥到 [MemoryDedup] 纯函数（参考 `MemoryNodeColorTest` 的
 * `pickNodeColorByAttributes` 模式），JVM 单测直接构造 [Memory] 验证；另外用源码扫描
 * （参考 `LaunchAppToolTest` / `AgentStatusOverlayWiringTest`）把"仓库 + 工具层 + prompt"
 * 三处 wiring 固化，防"下次顺手清理时把这条逻辑回滚"。
 *
 * 不用 ObjectBox/Robolectric 真跑写入路径的理由：
 *  - 仓库层依赖 `BoxStore` + Box accessor，JVM 单测里 mock 起来收益低
 *  - 关心的回归是"决策是否做 + wiring 是否在"，这两者已被本测试覆盖
 *  - 真正的端到端验证由 agent E2E（scripts/e2e 下的 sh 脚本）兜底
 *
 * 对应 TC-AGENT-260-a..h + TC-AGENT-261-a..f（见 docs/hermes-test-cases.md）。
 */
class MemoryDedupTest {

    // ===== TC-AGENT-260-a: jaccard fallback blocks high-overlap content =====

    /** TC-AGENT-260-a: embedding=null 时，jaccard char-3gram 高重叠的应阻断。 */
    @Test
    fun `TC-AGENT-260-a jaccard fallback blocks high-overlap content`() {
        // 构造一对内容上 char-3gram 集合高度重叠的候选 vs 新输入
        val existing = Memory().apply { title = "口味"; content = "用户喜欢吃辣的食物特别是火锅串串" }
        val decision = decideDedupOnCreate(
            newTitle = "用户口味",
            newContent = "用户喜欢吃辣的食物特别是火锅串串",  // 改一字符内的微小变化以避免 exact_duplicate
            newEmbedding = null,
            candidates = listOf(existing),
        )
        assertTrue("高重叠内容应该被阻断", decision.blocked)
        assertTrue("候选列表必须包含命中条目", decision.similarMemories.contains(existing))
        // 由于 newContent 与 existing.content 完全相同 → 命中 exact_duplicate 优先
        assertEquals("exact_duplicate", decision.reason)

        // 真正测 jaccard 兜底：制造一个非精确但高重叠的样本
        val existing2 = Memory().apply { content = "今日北京天气晴朗气温二十五度南风三级体感舒适" }
        val decision2 = decideDedupOnCreate(
            newTitle = "天气",
            newContent = "今日北京天气晴朗气温二十五度南风三级体感非常舒适",  // 多一个"非常"
            newEmbedding = null,
            candidates = listOf(existing2),
        )
        assertTrue("jaccard 兜底应该阻断高重叠（非精确）内容", decision2.blocked)
        assertEquals("jaccard_similar", decision2.reason)
    }

    // ===== TC-AGENT-260-b: cosine similarity above threshold blocks =====

    /** TC-AGENT-260-b: embedding 几乎共线（cosine ≈ 1.0）时应阻断。 */
    @Test
    fun `TC-AGENT-260-b cosine similarity above threshold blocks`() {
        val candidateEmb = FloatArray(8) { i -> if (i == 0) 0.99f else if (i == 1) 0.01f else 0f }
        val existing = Memory().apply {
            content = "candidate-different-content"  // content 不一样，纯靠 embedding 命中
            embedding = Embedding(candidateEmb)
        }
        val newEmb = FloatArray(8) { i -> if (i == 0) 1.0f else 0f }
        val decision = decideDedupOnCreate(
            newTitle = "anything",
            newContent = "totally different surface content here",
            newEmbedding = newEmb,
            candidates = listOf(existing),
        )
        assertTrue("cosine 高于阈值应阻断", decision.blocked)
        assertEquals("cosine_similar", decision.reason)
        assertTrue(decision.similarMemories.contains(existing))
    }

    /** TC-AGENT-260-b': cosine 低于阈值不阻断（验证阈值确实在生效）。 */
    @Test
    fun `TC-AGENT-260-b cosine similarity below threshold allows`() {
        val candidateEmb = FloatArray(8) { i -> if (i == 0) 1f else 0f }
        val existing = Memory().apply {
            content = "fa fa fa fa fa fa"
            embedding = Embedding(candidateEmb)
        }
        // 正交向量 → cosine = 0
        val newEmb = FloatArray(8) { i -> if (i == 1) 1f else 0f }
        val decision = decideDedupOnCreate(
            newTitle = "x",
            newContent = "qa qa qa qa qa qa",
            newEmbedding = newEmb,
            candidates = listOf(existing),
        )
        assertFalse("正交 embedding 不应阻断", decision.blocked)
        assertEquals("none", decision.reason)
    }

    // ===== TC-AGENT-260-c: unrelated content allows creation =====

    /** TC-AGENT-260-c: 内容完全无关时 embedding=null 也应放行（避免 jaccard 误杀）。 */
    @Test
    fun `TC-AGENT-260-c unrelated content allows creation`() {
        val existing = Memory().apply { content = "用户口味偏辣" }
        val decision = decideDedupOnCreate(
            newTitle = "scuba",
            newContent = "今天去爬山看到一只松鼠在树上跑",
            newEmbedding = null,
            candidates = listOf(existing),
        )
        assertFalse("无关内容应放行", decision.blocked)
        assertEquals("none", decision.reason)
        assertTrue(decision.similarMemories.isEmpty())
    }

    /** TC-AGENT-260-c': 空候选列表必放行。 */
    @Test
    fun `TC-AGENT-260-c empty candidates allows creation`() {
        val decision = decideDedupOnCreate(
            newTitle = "x",
            newContent = "x",
            newEmbedding = null,
            candidates = emptyList(),
        )
        assertFalse(decision.blocked)
        assertEquals("none", decision.reason)
    }

    // ===== TC-AGENT-260-d: exact content match marked exact_duplicate =====

    /** TC-AGENT-260-d: 完全一字不差时 reason=exact_duplicate（对齐 Python `if content in entries: return`）。 */
    @Test
    fun `TC-AGENT-260-d exact content match marked exact_duplicate`() {
        val existing = Memory().apply { content = "完全一字不差的内容串" }
        val decision = decideDedupOnCreate(
            newTitle = "whatever",
            newContent = "完全一字不差的内容串",
            newEmbedding = null,
            candidates = listOf(existing),
        )
        assertTrue(decision.blocked)
        assertEquals(
            "精确匹配必须标 exact_duplicate（不要被 jaccard/cosine 抢先）",
            "exact_duplicate", decision.reason
        )
    }

    // ===== TC-AGENT-260-e: read-side dedup folds duplicates preserving order =====

    /** TC-AGENT-260-e: deduplicateBySemantics 保留首个、折叠后续重复，输入顺序保留。 */
    @Test
    fun `TC-AGENT-260-e read-side dedup folds duplicates preserving order`() {
        val m1 = Memory().apply { id = 1; content = "A 段落原文" }
        val m2 = Memory().apply { id = 2; content = "A 段落原文" }  // 与 m1 重复
        val m3 = Memory().apply { id = 3; content = "B 段落完全不同的内容" }
        val out = deduplicateBySemantics(listOf(m1, m2, m3))
        assertEquals("重复 A 应被折叠，剩 2 条", 2, out.size)
        assertEquals("首个 A 必须保留（输入顺序）", 1L, out[0].id)
        assertEquals("B 必须在 A 之后（顺序保留）", 3L, out[1].id)
    }

    /** TC-AGENT-260-e': 单元素 / 空 list 必直接返回。 */
    @Test
    fun `TC-AGENT-260-e read-side dedup degenerate inputs`() {
        assertEquals(emptyList<Memory>(), deduplicateBySemantics(emptyList()))
        val m = Memory().apply { id = 99; content = "only one" }
        val out = deduplicateBySemantics(listOf(m))
        assertEquals(1, out.size)
        assertEquals(99L, out[0].id)
    }

    /** TC-AGENT-260-e'': cosine 同义节点也会被折叠。 */
    @Test
    fun `TC-AGENT-260-e read-side dedup folds cosine-similar entries`() {
        val emb1 = FloatArray(4) { i -> if (i == 0) 1f else 0f }
        val emb2 = FloatArray(4) { i -> if (i == 0) 0.999f else 0f }  // cosine ≈ 1
        val m1 = Memory().apply { id = 1; content = "alpha statement"; embedding = Embedding(emb1) }
        val m2 = Memory().apply { id = 2; content = "beta different surface"; embedding = Embedding(emb2) }
        val m3 = Memory().apply { id = 3; content = "gamma totally unrelated content here" }
        val out = deduplicateBySemantics(listOf(m1, m2, m3))
        assertEquals("cosine 同义应折叠，剩 2 条", 2, out.size)
        assertTrue("m1 必须保留", out.any { it.id == 1L })
        assertTrue("m3 必须保留", out.any { it.id == 3L })
        assertFalse("m2 应被折叠掉", out.any { it.id == 2L })
    }

    // ===== TC-AGENT-260-f: source contract wires dedup in repo =====

    /** TC-AGENT-260-f: 仓库层把 dedup 接入两处（createMemory + runSearchMemoriesWithDebug）。 */
    @Test
    fun `TC-AGENT-260-f source contract wires dedup in repo`() {
        val source = File(memoryRepositoryPath()).readText()

        // 1. createMemory 必须含 force 参数（agent 后门）
        assertTrue(
            "createMemory 必须新增 force: Boolean 参数供 agent 显式跳过去重（R-AGENT-003 回归）",
            Regex("""fun\s+createMemory\s*\([^)]*\bforce\s*:\s*Boolean""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source)
        )

        // 2. createMemory 函数体必须调用 decideDedupOnCreate
        assertTrue(
            "createMemory 必须调用 decideDedupOnCreate 进行写入侧去重（R-AGENT-003 回归）",
            source.contains("decideDedupOnCreate(")
        )

        // 3. runSearchMemoriesWithDebug 末尾的 `// deduplicateBySemantics(sortedMemories)` 必须复活
        //    防呆：禁止保留注释形式
        assertFalse(
            "MemoryRepository 不应保留 `// deduplicateBySemantics` 注释——必须真调用",
            Regex("""//\s*deduplicateBySemantics\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "runSearchMemoriesWithDebug 末尾必须真调用 deduplicateBySemantics（R-AGENT-003 回归）",
            source.contains("deduplicateBySemantics(sortedMemories)") ||
                source.contains("deduplicateBySemantics(\n            sortedMemories")
        )
    }

    // ===== TC-AGENT-260-g: executor parses force param =====

    /** TC-AGENT-260-g: 工具层 executor 解析 force 并透传到仓库。 */
    @Test
    fun `TC-AGENT-260-g executor parses force param`() {
        val source = File(memoryQueryToolExecutorPath()).readText()
        assertTrue(
            "MemoryQueryToolExecutor.executeCreateMemory 必须解析 force 参数",
            Regex("""it\.name\s*==\s*"force"""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryQueryToolExecutor.executeCreateMemory 必须把 force 透传给 createMemory(...)",
            Regex("""createMemory\s*\([^)]*force\s*=\s*""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source)
        )
    }

    // ===== TC-AGENT-260-h: prompts declare force param with dedup hint =====

    /** TC-AGENT-260-h: SystemToolPromptsInternal EN + CN 各声明 force 参数并解释 dedup 行为。 */
    @Test
    fun `TC-AGENT-260-h prompts declare force param with dedup hint`() {
        val source = File(systemToolPromptsInternalPath()).readText()
        // create_memory 出现 2 次（EN + CN）
        val createMemoryDeclarations = Regex("""name\s*=\s*"create_memory"""").findAll(source).count()
        assertEquals(
            "create_memory 必须在 EN + CN 都声明，实际 $createMemoryDeclarations",
            2, createMemoryDeclarations
        )
        // force 参数必须在 prompt 中出现（任一语言段提到即可，扫两次）
        val forceParamMentions = Regex("""name\s*=\s*"force"""").findAll(source).count()
        assertTrue(
            "create_memory 的 ToolPrompt 必须声明 force 参数（EN + CN 各一处，实际 $forceParamMentions）",
            forceParamMentions >= 2
        )
        // 描述里必须含 dedup / duplicate 关键词，让模型知道这工具会因重复返错
        assertTrue(
            "create_memory 的 EN 描述里必须含 'duplicate'（让模型知道有去重行为）",
            source.contains("duplicate")
        )
    }

    // ===== TC-AGENT-261: 手动重复清理 UI =====

    /** TC-AGENT-261-a: 空 / 单元素输入必返回 empty。 */
    @Test
    fun `TC-AGENT-261-a findDuplicateGroups degenerate inputs`() {
        assertTrue(findDuplicateGroups(emptyList()).isEmpty())
        val solo = Memory().apply { id = 1; content = "only one" }
        assertTrue("单元素无重复组", findDuplicateGroups(listOf(solo)).isEmpty())
    }

    /** TC-AGENT-261-b: 精确重复 → 1 组；不相似的不入组。 */
    @Test
    fun `TC-AGENT-261-b findDuplicateGroups groups exact duplicates`() {
        val m1 = Memory().apply { id = 1; content = "alpha statement" }
        val m2 = Memory().apply { id = 2; content = "alpha statement" }
        val m3 = Memory().apply { id = 3; content = "beta totally unrelated entry" }
        val groups = findDuplicateGroups(listOf(m1, m2, m3))
        assertEquals("应为 1 组", 1, groups.size)
        val grp = groups[0]
        assertEquals("组内 2 条", 2, grp.size)
        assertEquals("首条保持 m1（输入顺序）", 1L, grp[0].id)
        assertEquals("第二条 m2", 2L, grp[1].id)
        assertFalse("m3 不应在任何组里", groups.flatten().any { it.id == 3L })
    }

    /** TC-AGENT-261-c: 传递性 —— A~B（cosine） + B~C（content equality）→ 一组 {A,B,C}。 */
    @Test
    fun `TC-AGENT-261-c findDuplicateGroups transitively merges via union-find`() {
        // m1 与 m2：cosine 高（embedding 几乎共线，content 完全不同 → 不会走 exact_duplicate）
        val emb1 = FloatArray(4) { i -> if (i == 0) 1f else 0f }
        val emb2 = FloatArray(4) { i -> if (i == 0) 0.9999f else 0f }
        val m1 = Memory().apply { id = 1; content = "完全独立的 alpha 句"; embedding = Embedding(emb1) }
        val sharedContent = "毫无 alpha 关系的 beta 段落"
        val m2 = Memory().apply { id = 2; content = sharedContent; embedding = Embedding(emb2) }
        // m2 与 m3：content 一字不差（areMemoriesSimilar 第一行短路 true）。m3 无 embedding 不影响。
        val m3 = Memory().apply { id = 3; content = sharedContent }
        val groups = findDuplicateGroups(listOf(m1, m2, m3))
        assertEquals("union-find 传递性：应为 1 组", 1, groups.size)
        assertEquals("组内 3 条全收", 3, groups[0].size)
        val ids = groups[0].map { it.id }.toSet()
        assertTrue("m1 经 cosine 与 m2 相连；m2 经 content 等同与 m3 相连；并查集合一组", ids.containsAll(setOf(1L, 2L, 3L)))
    }

    /** TC-AGENT-261-d: 仓库层暴露 scanDuplicateGroups + deleteMemories 且 deleteMemories 走既有 deleteMemory。 */
    @Test
    fun `TC-AGENT-261-d repository wires scan and batch delete`() {
        val source = File(memoryRepositoryPath()).readText()
        assertTrue(
            "MemoryRepository 必须暴露 suspend fun scanDuplicateGroups()",
            Regex("""suspend\s+fun\s+scanDuplicateGroups\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryRepository 必须暴露 suspend fun deleteMemories(ids: List<Long>): Int",
            Regex("""suspend\s+fun\s+deleteMemories\s*\(\s*ids\s*:\s*List<Long>\s*\)\s*:\s*Int""")
                .containsMatchIn(source)
        )
        assertTrue(
            "deleteMemories 必须复用既有 deleteMemory（保留级联清理链路）",
            Regex("""deleteMemories[\s\S]{0,400}?deleteMemory\(""").containsMatchIn(source)
        )
        assertTrue(
            "scanDuplicateGroups 必须调用 findDuplicateGroups",
            source.contains("findDuplicateGroups(")
        )
    }

    /** TC-AGENT-261-e: UI 必须有清理入口（扫帚 icon CleaningServices）。 */
    @Test
    fun `TC-AGENT-261-e app bar wires cleanup icon`() {
        val source = File(memoryScreenPath()).readText()
        assertTrue(
            "MemorySearchBar 必须新增 onCleanupClick 参数",
            Regex("""onCleanupClick\s*:\s*\(\)\s*->\s*Unit""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryScreen 必须使用 CleaningServices icon",
            source.contains("Icons.Default.CleaningServices")
        )
        assertTrue(
            "MemoryScreen 必须接 viewModel.scanDuplicates()",
            source.contains("viewModel.scanDuplicates()")
        )
    }

    /** TC-AGENT-261-f: Dialog 存在 + ViewModel 状态机 3 方法 + dedupScan 字段。 */
    @Test
    fun `TC-AGENT-261-f dialog and viewmodel wire dedup cleanup`() {
        val dialogSrc = File(memoryDialogsPath()).readText()
        assertTrue(
            "MemoryDialogs.kt 必须定义 DedupCleanupDialog",
            Regex("""fun\s+DedupCleanupDialog\s*\(""").containsMatchIn(dialogSrc)
        )
        assertTrue(
            "DedupCleanupDialog 必须复用 BatchDeleteConfirmDialog 做二次确认",
            dialogSrc.contains("BatchDeleteConfirmDialog(")
        )

        val vmSrc = File(memoryViewModelPath()).readText()
        assertTrue("ViewModel 必须有 dedupScan 字段", vmSrc.contains("dedupScan"))
        assertTrue("ViewModel 必须定义 sealed DedupScanState", vmSrc.contains("sealed class DedupScanState"))
        assertTrue("scanDuplicates 方法存在", Regex("""fun\s+scanDuplicates\s*\(""").containsMatchIn(vmSrc))
        assertTrue(
            "deleteSelectedDuplicates(ids) 方法存在",
            Regex("""fun\s+deleteSelectedDuplicates\s*\(\s*ids\s*:\s*List<Long>""").containsMatchIn(vmSrc)
        )
        assertTrue(
            "dismissDedupDialog 方法存在",
            Regex("""fun\s+dismissDedupDialog\s*\(""").containsMatchIn(vmSrc)
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun memoryRepositoryPath(): String =
        File(appSrcMainRoot(), "data/repository/MemoryRepository.kt").path

    private fun memoryQueryToolExecutorPath(): String =
        File(appSrcMainRoot(), "core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt").path

    private fun systemToolPromptsInternalPath(): String =
        File(appSrcMainRoot(), "core/config/SystemToolPromptsInternal.kt").path

    private fun memoryScreenPath(): String =
        File(appSrcMainRoot(), "ui/features/memory/screens/MemoryScreen.kt").path

    private fun memoryDialogsPath(): String =
        File(appSrcMainRoot(), "ui/features/memory/screens/dialogs/MemoryDialogs.kt").path

    private fun memoryViewModelPath(): String =
        File(appSrcMainRoot(), "ui/features/memory/viewmodel/MemoryViewModel.kt").path
}
