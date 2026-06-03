package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.Memory
import kotlin.math.sqrt

/**
 * R-AGENT-003 memory bugfix —— 写入 / 读出两侧的去重纯逻辑层。
 *
 * 为什么单独成文件：核心去重判定剥到纯 Kotlin 顶层函数，不依赖 ObjectBox / Robolectric /
 * Android Context，JVM 单测可直接构造 [Memory] 实例验证（参考 `MemoryNodeColorTest`
 * 的 `pickNodeColorByAttributes` 模式）。仓库层 [MemoryRepository.createMemory] 和
 * [MemoryRepository.runSearchMemoriesWithDebug] 只负责把 ObjectBox 查到的候选传进来，
 * 接收 [DedupDecision] 后做副作用。
 *
 * 设计取舍：
 * - **不**对 [Memory.tags] / [Memory.links] 等 `ToMany` 关系做引用，避免 JVM 单测下
 *   `lateinit` 未初始化崩溃。
 * - 阈值默认与 Python 上游 `MemoryStore.add` 的 `if content in entries: return` 等价
 *   行为对齐（精确匹配视为 `exact_duplicate`），其它阈值（cosine 0.85 / jaccard 0.75）
 *   是 Android 侧扩展，无 Python 上游。
 */

/** 写入侧去重决策结果。`blocked=true` 表示发现重复，由仓库层决定是否阻断写入。 */
data class DedupDecision(
    val blocked: Boolean,
    val similarMemories: List<Memory>,
    /**
     * 阻断原因：
     * - `none`: 不阻断
     * - `exact_duplicate`: 与某条候选 `content` 一字不差（对齐 Python 上游）
     * - `cosine_similar`: 余弦相似度 ≥ [DEFAULT_COSINE_THRESHOLD]
     * - `jaccard_similar`: jaccard char-ngram 相似度 ≥ [DEFAULT_JACCARD_THRESHOLD]（无 embedding 时的回退）
     */
    val reason: String,
) {
    companion object {
        val ALLOW = DedupDecision(blocked = false, similarMemories = emptyList(), reason = "none")
    }
}

/** 默认余弦阈值。基于经验：> 0.85 几乎一定是同一信息的不同表述。 */
const val DEFAULT_COSINE_THRESHOLD: Float = 0.85f

/**
 * 默认 jaccard char-ngram 阈值（n=3）。
 * 比 cosine 阈值高，因为字符级相似度对短文本噪声敏感（"用户喜欢吃辣" / "用户口味偏辣"
 * 这种 4-6 字短句很容易超 0.7，但 0.75+ 才算真重复）。
 */
const val DEFAULT_JACCARD_THRESHOLD: Float = 0.75f

/** 读出侧 dedup 用更严的阈值：宁可漏合，不可错合（已经 retrieve 出来的结果做最后过滤）。 */
const val READ_SIDE_COSINE_THRESHOLD: Float = 0.92f

/** 读出侧 jaccard 兜底阈值。 */
const val READ_SIDE_JACCARD_THRESHOLD: Float = 0.85f

/**
 * 仓库层 [MemoryRepository.createMemory] 在发现疑似重复且 `force=false` 时抛出。
 * 工具层 ([com.ai.assistance.operit.core.tools.defaultTool.standard.MemoryQueryToolExecutor])
 * 捕获后把候选信息序列化到 ToolResult 错误字段，agent 看到后决定 update 还是 force 重试。
 */
class DuplicateMemoryException(
    val reason: String,
    val similarMemories: List<Memory>,
) : RuntimeException(
    "Possible duplicate memory ($reason). " +
        "Existing similar: " + similarMemories.joinToString("; ") { "'${it.title}' (uuid=${it.uuid})" }
)

/**
 * 写入侧：判断"是否应该阻断创建 + 返回相似候选给 agent"。
 *
 * 调用方（[MemoryRepository.createMemory]）应在 `force=false` 时遵守
 * [DedupDecision.blocked]：阻断 + 把 [DedupDecision.similarMemories] 序列化到 ToolResult
 * 错误信息里，让 agent 决定 update 还是带 `force=true` 重试。
 *
 * 算法（按 short-circuit 顺序）：
 * 1. 任一候选 `content` 与 [newContent] 完全相等 → `exact_duplicate`（对齐 Python 上游）
 * 2. [newEmbedding] 非空且任一候选 embedding 同维度，余弦 ≥ [cosineThreshold] → `cosine_similar`
 * 3. jaccard char-ngram(n=3) ≥ [jaccardThreshold] → `jaccard_similar`（embedding 缺失时的兜底）
 * 4. 否则 [DedupDecision.ALLOW]
 *
 * [candidates] 必须是仓库层用 [newContent] / [newTitle] 已做过初筛的小集合（推荐 ≤ 5 条），
 * 本函数不做全库 N×N 扫描。
 */
fun decideDedupOnCreate(
    newTitle: String,
    newContent: String,
    newEmbedding: FloatArray?,
    candidates: List<Memory>,
    cosineThreshold: Float = DEFAULT_COSINE_THRESHOLD,
    jaccardThreshold: Float = DEFAULT_JACCARD_THRESHOLD,
): DedupDecision {
    if (candidates.isEmpty() || newContent.isBlank()) return DedupDecision.ALLOW

    // 1. 精确重复（Python 上游对齐）—— 不论 title 是否相同
    val exact = candidates.firstOrNull { it.content == newContent }
    if (exact != null) {
        return DedupDecision(
            blocked = true,
            similarMemories = listOf(exact),
            reason = "exact_duplicate",
        )
    }

    // 2. 语义相似（cosine on embedding）
    if (newEmbedding != null && newEmbedding.isNotEmpty()) {
        val cosineHits = candidates.filter { mem ->
            val v = mem.embedding?.vector
            v != null && v.size == newEmbedding.size && cosineSimilarity(newEmbedding, v) >= cosineThreshold
        }
        if (cosineHits.isNotEmpty()) {
            return DedupDecision(
                blocked = true,
                similarMemories = cosineHits,
                reason = "cosine_similar",
            )
        }
    }

    // 3. jaccard 字符 n-gram 兜底（embedding 缺失 / 维度不匹配的场景）
    val jaccardHits = candidates.filter { mem ->
        jaccardCharNgram(newContent, mem.content, n = 3) >= jaccardThreshold
    }
    if (jaccardHits.isNotEmpty()) {
        return DedupDecision(
            blocked = true,
            similarMemories = jaccardHits,
            reason = "jaccard_similar",
        )
    }

    // 4. 不阻断 —— title 此参数本期不参与判定（agent 已学会"换 title 重存"，title 等同无效）。
    //    保留 newTitle 形参是为下一期接入"title 相似度加权"留扩展点。
    val _unusedForNow = newTitle
    return DedupDecision.ALLOW
}

/**
 * 读出侧：从一批已排序的 retrieve 结果中，折叠语义重复的条目，保留首个出现的。
 *
 * 仓库层 [MemoryRepository.runSearchMemoriesWithDebug] 在最后一步调用，等于在不动数据库的
 * 前提下给 agent 一个"已去重"的视图（解决存量重复污染 agent 上下文的问题）。
 *
 * 算法：O(N²) 两两比较，N=retrieve 结果集大小（通常 ≤ topK，已经被相关度阈值过滤过，不会爆炸）。
 * - 精确 content 相等 → 折叠
 * - embedding 同维度且 cosine ≥ [cosineThreshold] → 折叠
 * - jaccard char-ngram ≥ [jaccardThreshold] → 折叠（embedding 缺失兜底）
 *
 * 输入顺序代表相关度，输出顺序也保留输入顺序（折叠时丢后留前）。
 */
fun deduplicateBySemantics(
    memories: List<Memory>,
    cosineThreshold: Float = READ_SIDE_COSINE_THRESHOLD,
    jaccardThreshold: Float = READ_SIDE_JACCARD_THRESHOLD,
): List<Memory> {
    if (memories.size <= 1) return memories
    val kept = ArrayList<Memory>(memories.size)
    for (m in memories) {
        val isDuplicate = kept.any { existing -> areMemoriesSimilar(existing, m, cosineThreshold, jaccardThreshold) }
        if (!isDuplicate) kept.add(m)
    }
    return kept
}

/**
 * 全库扫描：用并查集把语义相似的 memory 归到同一组，返回所有 size>=2 的组。
 *
 * 给 UI 层 ([com.ai.assistance.operit.ui.features.memory.screens.dialogs.DedupCleanupDialog])
 * 列出供用户勾选删除。算法 O(N²) 两两比较 + union-find；N 通常在几百到几千量级，可接受。
 *
 * 设计取舍：
 * - **传递性**：A~B + B~C 即使 A 与 C 表面看不像，也并入同一组（用户视角"这堆都是讲一回事"）
 * - **组内顺序保持输入顺序**：调用方可借此约定"保留首个 / 删后续"（UI 默认勾选第 2+）
 * - **只返回 size>=2 的组**：size=1 不算重复，没意义
 *
 * 阈值默认走读出侧（严，宁可漏合不可错合）。
 */
fun findDuplicateGroups(
    memories: List<Memory>,
    cosineThreshold: Float = READ_SIDE_COSINE_THRESHOLD,
    jaccardThreshold: Float = READ_SIDE_JACCARD_THRESHOLD,
): List<List<Memory>> {
    val n = memories.size
    if (n < 2) return emptyList()
    val parent = IntArray(n) { it }
    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        var cur = x
        while (parent[cur] != root) {
            val next = parent[cur]
            parent[cur] = root
            cur = next
        }
        return root
    }
    fun union(a: Int, b: Int) {
        val ra = find(a); val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            if (areMemoriesSimilar(memories[i], memories[j], cosineThreshold, jaccardThreshold)) {
                union(i, j)
            }
        }
    }
    val byRoot = LinkedHashMap<Int, MutableList<Memory>>()  // LinkedHashMap 保留首次出现顺序
    for (i in 0 until n) {
        byRoot.getOrPut(find(i)) { mutableListOf() }.add(memories[i])
    }
    return byRoot.values.filter { it.size >= 2 }
}

/** 两个 memory 是否构成"语义重复"。供 [deduplicateBySemantics] / [findDuplicateGroups] 内部使用。 */
internal fun areMemoriesSimilar(
    a: Memory,
    b: Memory,
    cosineThreshold: Float,
    jaccardThreshold: Float,
): Boolean {
    if (a.content == b.content && a.content.isNotEmpty()) return true
    val av = a.embedding?.vector
    val bv = b.embedding?.vector
    if (av != null && bv != null && av.size == bv.size && av.isNotEmpty()) {
        if (cosineSimilarity(av, bv) >= cosineThreshold) return true
    }
    if (a.content.isNotBlank() && b.content.isNotBlank()) {
        if (jaccardCharNgram(a.content, b.content, n = 3) >= jaccardThreshold) return true
    }
    return false
}

/**
 * 余弦相似度。两向量必须同维度且非空，否则返回 0f。
 * 公式：dot(a,b) / (||a|| · ||b||)，零向量也返回 0f（避免 NaN）。
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        val x = a[i].toDouble()
        val y = b[i].toDouble()
        dot += x * y
        normA += x * x
        normB += y * y
    }
    if (normA == 0.0 || normB == 0.0) return 0f
    return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
}

/**
 * Jaccard 字符 n-gram 相似度。用于 embedding 缺失时的兜底重复判定。
 *
 * 算法：把两段字符串切成 n-gram 集合，返回 |intersect| / |union|。
 * - n=3 在中文短文本上经验最稳：n=2 噪声大（"用户" / "户喜" 这种贡献太多假阳性），
 *   n=4 在 6-10 字短句上集合太稀。
 * - 字符串短于 n 时返回 0（无可比性）。
 * - 完全相同的字符串返回 1.0。
 */
fun jaccardCharNgram(a: String, b: String, n: Int = 3): Float {
    if (a == b && a.isNotEmpty()) return 1f
    if (a.length < n || b.length < n) return 0f
    val gramsA = charNgrams(a, n)
    val gramsB = charNgrams(b, n)
    if (gramsA.isEmpty() || gramsB.isEmpty()) return 0f
    val intersect = gramsA.intersect(gramsB).size
    val union = gramsA.size + gramsB.size - intersect
    if (union == 0) return 0f
    return intersect.toFloat() / union.toFloat()
}

private fun charNgrams(s: String, n: Int): Set<String> {
    val out = HashSet<String>(maxOf(0, s.length - n + 1))
    for (i in 0..(s.length - n)) {
        out.add(s.substring(i, i + n))
    }
    return out
}
