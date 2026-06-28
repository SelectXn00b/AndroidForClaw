package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-046 写入侧接线守卫（source-scan）：
 *  - `MemoryRepository.createMemory` 必须接 `triggerKeywords` 形参并写 `MemoryProperty(key=KEY_TRIGGER_KEYWORDS)`
 *  - `MemoryRepository.setTriggerKeywords(memoryId, keywords)` 必须存在
 *  - `MemoryQueryToolExecutor` 的 `create_memory` 工具 schema 必须声明 `trigger_keywords` 字段
 *
 * **测试策略**：跟 PersistentInstructionInjectionTest 一样源码扫描（参考既有 PersistentInstructionAgentHintTest /
 * MemoryDedupTest 模式）—— Repository 强依赖 Context / ObjectBox，JVM 单测 mock 收益低；
 * 把"字段被声明且被写入 property"固化成源码契约，运行时正确性由 §3 E2E 兜底。
 *
 * 对应 TC-AGENT-281-a / 282-a / 283-a（见 docs/hermes-test-cases.md）。
 */
class MemoryRepositoryTriggerKeywordsWiringTest {

    /**
     * TC-AGENT-281-a: createMemory 必须新增 triggerKeywords 形参并在节点上写 trigger_keywords property。
     */
    @Test
    fun `TC-AGENT-281-a createMemory writes trigger_keywords property when supplied`() {
        val source = File(memoryRepositoryPath()).readText()

        // (1) createMemory 签名包含 triggerKeywords
        val sigMatch = Regex("""suspend\s+fun\s+createMemory\s*\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(source)
        assertTrue(
            "MemoryRepository 必须有 createMemory 方法（签名匹配失败）。",
            sigMatch != null
        )
        val params = sigMatch!!.groupValues[1]
        assertTrue(
            "createMemory 必须新增 `triggerKeywords: List<String>? = null` 形参 —— " +
                "R-AGENT-046 写入侧入口。实际形参=`${params.take(400)}`",
            Regex("""\btriggerKeywords\s*:\s*List\s*<\s*String\s*>\??""").containsMatchIn(params)
        )

        // (2) 方法体写一条 MemoryProperty(key=KEY_TRIGGER_KEYWORDS)
        assertTrue(
            "MemoryRepository 必须在 createMemory 里通过 MemoryProperty.KEY_TRIGGER_KEYWORDS 写 property。",
            source.contains("MemoryProperty.KEY_TRIGGER_KEYWORDS") ||
                source.contains("KEY_TRIGGER_KEYWORDS")
        )

        // (3) 必须把 keywords joinToString(",") 后写入 value
        assertTrue(
            "MemoryRepository 写 trigger_keywords property 时必须用 joinToString(\",\") 把 keywords 拼成 CSV。",
            Regex("""joinToString\s*\(\s*","\s*\)""").containsMatchIn(source)
        )

        // (4) MemoryRepository 必须有一个 propertyBox（ObjectBox 的 MemoryProperty Box）
        assertTrue(
            "MemoryRepository 必须声明 `propertyBox = store.boxFor<MemoryProperty>()` 用于持久化。",
            source.contains("store.boxFor<MemoryProperty>()") ||
                Regex("""propertyBox\s*=\s*store\.boxFor""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-283-a: setTriggerKeywords(memoryId, keywords) 方法存在并落库。
     */
    @Test
    fun `TC-AGENT-283-a setTriggerKeywords upserts and deletes`() {
        val source = File(memoryRepositoryPath()).readText()

        // 方法存在
        assertTrue(
            "MemoryRepository 必须有 `setTriggerKeywords` 方法 —— UI 编辑/ViewModel 写入入口。",
            Regex("""fun\s+setTriggerKeywords\s*\(""").containsMatchIn(source)
        )

        // upsert 信号：既能写新 prop 也能改 value
        assertTrue(
            "setTriggerKeywords 必须 upsert —— 已有 property 改 value，没有则新建。" +
                "在方法体里必须看到 firstOrNull / KEY_TRIGGER_KEYWORDS 双重检索。",
            source.contains("firstOrNull") && source.contains("KEY_TRIGGER_KEYWORDS")
        )

        // delete 分支：keywords 为空时必须 remove property
        assertTrue(
            "setTriggerKeywords 在 keywords 为空 / null 时必须能删除 property（回退到老条目=每轮注入语义）—— " +
                "方法体应出现 `properties.remove(` 或 `propertyBox.remove(`。",
            Regex("""properties\.remove\s*\(""").containsMatchIn(source) ||
                Regex("""propertyBox\.remove\s*\(""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-282-a: MemoryQueryToolExecutor 的 create_memory 工具读取 `trigger_keywords` 参数并转发。
     */
    @Test
    fun `TC-AGENT-282-a create_memory tool executor reads trigger_keywords param`() {
        val source = File(memoryQueryToolExecutorPath()).readText()

        // (1) 必须读 trigger_keywords 参数
        assertTrue(
            "MemoryQueryToolExecutor.executeCreateMemory 必须从 tool.parameters 里读 `trigger_keywords`。",
            Regex(""""trigger_keywords"""").containsMatchIn(source)
        )

        // (2) 必须把 trigger_keywords 传到 createMemory
        assertTrue(
            "MemoryQueryToolExecutor 必须把 triggerKeywords = ... 传给 memoryRepository.createMemory(...) —— " +
                "否则 agent 调 create_memory 时即使带了 trigger_keywords 也会被丢弃。",
            Regex("""triggerKeywords\s*=""").containsMatchIn(source)
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
}
