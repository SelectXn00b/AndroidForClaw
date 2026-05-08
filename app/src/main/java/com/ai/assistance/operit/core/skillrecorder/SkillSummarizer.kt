package com.ai.assistance.operit.core.skillrecorder

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.EventDetails
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Skill 总结器：将构建器步骤（录制帧 + 思考逻辑）发送给 LLM，生成 SKILL.md 内容。
 */
class SkillSummarizer(private val context: Context) {

    companion object {
        private const val TAG = "SkillSummarizer"
        private const val AI_TIMEOUT_MS = 120_000L // 120 seconds
    }

    /**
     * 对录制会话进行 AI 总结，生成 SKILL.md 内容。
     * @param configId 用户选择的模型配置 ID，为 null 时使用第一个可用配置
     * @return 生成的 SKILL.md 文本，失败返回 null
     */
    suspend fun summarize(session: RecordingSession, configId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            if (session.steps.isEmpty()) {
                AppLogger.w(TAG, "没有步骤可总结")
                return@withContext null
            }

            val stepsText = buildStepsPromptText(session.steps)
            val systemPrompt = buildSystemPrompt(session.draftText)
            val userPrompt = buildUserPrompt(session, stepsText)

            val chatHistory = listOf(
                PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt),
                PromptTurn(kind = PromptTurnKind.USER, content = userPrompt)
            )

            val service = createAIService(configId) ?: return@withContext generateDirectSkillMd(session)

            val result = withTimeoutOrNull(AI_TIMEOUT_MS) {
                val stream = service.sendMessage(
                    context = context,
                    chatHistory = chatHistory,
                    stream = true,
                    enableRetry = true
                )

                val sb = StringBuilder()
                stream.collect { chunk ->
                    sb.append(chunk)
                }
                sb.toString().trim()
            }

            if (result.isNullOrBlank()) {
                AppLogger.w(TAG, if (result == null) "AI 总结超时，使用直接格式化" else "AI 返回空结果，使用直接格式化")
                return@withContext generateDirectSkillMd(session)
            }

            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI 总结失败", e)
            generateDirectSkillMd(session)
        }
    }

    private suspend fun createAIService(configId: String? = null): AIService? {
        return try {
            val configManager = ModelConfigManager(context)
            val configs = configManager.getAllConfigSummaries()
            val targetId = configId ?: configs.firstOrNull()?.id ?: return null
            val config = configManager.getModelConfig(targetId) ?: return null
            AIServiceFactory.createService(config, configManager, context)
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建 AI 服务失败", e)
            null
        }
    }

    /**
     * 将步骤列表转换为 LLM prompt 文本，交替输出录制帧和思考文本。
     * 明确标注步骤类型，让 AI 区分"固定路径"和"动态推理"。
     */
    private fun buildStepsPromptText(steps: List<BuilderStep>): String {
        val sb = StringBuilder()
        steps.forEachIndexed { index, step ->
            when (step) {
                is BuilderStep.Record -> {
                    sb.appendLine("=== 步骤 ${index + 1}: 📍 录制操作（固定路径，照搬执行） ===")
                    if (step.label.isNotBlank()) {
                        sb.appendLine("用户描述: ${step.label}")
                    }
                    sb.appendLine("[!] 以下是用户录制的精确操作路径，生成时必须保留每一步的 UI 定位信息，Agent 执行时原封不动复现。")
                    val condensed = FrameSimplifier.condenseFrames(step.frames)
                    sb.appendLine(FrameSimplifier.framesToPromptText(condensed))
                }
                is BuilderStep.Think -> {
                    sb.appendLine("=== 步骤 ${index + 1}: 🧠 推理逻辑（动态判断，需要 Agent 实时思考） ===")
                    sb.appendLine("[!] 以下是需要 Agent 在执行时根据具体情况动态判断的逻辑，不能写成固定操作。")
                    sb.appendLine(step.content)
                    sb.appendLine()
                }
            }
        }
        return sb.toString()
    }

    private fun buildSystemPrompt(draftText: String? = null): String {
        val base = """
你是一位 Android 自动化 Skill 编写专家。
你的任务是根据用户提供的步骤序列，生成一份 SKILL.md 文件。
这份文件将被 AI Agent (Hermes) 读取并**通过无障碍服务在手机上精确复现这些操作**。

⚠️ 最重要的原则：Agent 是通过无障碍服务操作手机的程序，不是人类。它需要**精确的 UI 元素定位信息**才能找到并操作目标元素。

## 如何从录制数据推断点击目标

录制数据中的 CLICK 事件来自 UI 轮询（检测到界面发生变化），不包含直接的点击坐标。但每帧包含了：
- **操作前UI**：点击发生前的界面状态（包含所有可交互元素）
- **操作后UI**：点击发生后的界面状态

**你必须对比前后 UI 来推断用户点了什么**：
1. 看"操作前UI"中有哪些可点击/可交互的元素（按钮、链接、tab、列表项等）
2. 看"操作后UI"中界面发生了什么变化（出现新页面？某个 tab 高亮了？列表展开了？）
3. 推断：从前 UI 中哪个元素被点击后会产生后 UI 的变化
4. 在生成的 SKILL.md 中，用推断出的元素信息（text/resourceId/contentDescription）写出精确的点击指令

例如：前 UI 有 `text="工作台"` 的按钮，后 UI 切换到了工作台页面 → 推断用户点了 "工作台" 按钮。

## 两种步骤的本质区别

输入包含两种性质完全不同的步骤，生成时必须严格区分对待：

### 📍 录制步骤（固定路径 — 照搬执行，不需要推理）
录制存在的意义：**某些 UI 入口非常难找**（层级深、命名不直观、需要多次滚动才能发现），AI Agent 凭自己的判断力根本找不到。所以用户亲手录制了这条路径。

**用户描述**：每段录制步骤可能附带"用户描述"字段。这是用户对这段操作语义的精确概括（如"从主页进入房态房量页面"），你必须直接将其作为该段在 SKILL.md 中的标题/说明。**不需要再从 UI diff 推断操作意图——用户已经告诉你了。**

生成规则：
- 若有"用户描述"，直接用作 section 标题（如 `## 步骤 1：从主页进入房态房量页面（固定路径）`）
- 若无"用户描述"，才需要从帧数据推断操作意图作为标题
- 这是**精确的物理操作序列**，Agent 执行时必须**原封不动按顺序复现**，不需要思考"该点哪个"
- 每一步都保留完整的 UI 元素定位信息（text、contentDescription、className、resourceId）
- **禁止**将多个录制点击合并或概括成一句自然语言（如"导航到 XX 页面"）
- **禁止**省略中间步骤（即使看起来"显而易见"）
- 在生成的 SKILL.md 中，录制路径段落用 `<!-- PATH:FIXED -->` 标记，表示这是固定路径段
- **必须标注前提条件**：每个固定路径段开头用 `> 前提：xxx` 说明执行此段前需要在哪个状态（从帧数据的第一帧推断）。例如"前提：已打开美团酒店 app 主页"。这让 Agent 知道 skill 会从哪里开始导航，避免与用户指令里的目标重复操作

### 🧠 思考步骤（动态推理 — Agent 需要实时判断）
思考步骤存在的意义：某些操作**取决于当时的具体情况**（比如根据用户指令选择日期、选房型、填写动态内容），不能写死，需要 Agent 根据上下文实时推理。

生成规则：
- 在生成的 SKILL.md 中，思考段落用 `<!-- THINK:DYNAMIC -->` 标记
- 明确描述 Agent 需要根据什么条件做什么判断
- 保留用户写的推理逻辑，不要改写成固定操作

## 格式要求

1. 开头使用 YAML frontmatter，包含 name, description, category: recorded, platform: android
2. 逐步描述操作流程，保持步骤的原始顺序
3. **录制步骤必须保留具体的 UI 元素定位信息**，这是最关键的要求：
   - 每个操作必须明确指出目标元素的 **text**（显示文字）、**contentDescription**（无障碍描述）、**className**（元素类型如 Button/TextView）
   - 如果输入数据中包含 **resourceId**（如 `com.example:id/btn_submit`），必须保留
   - 写成 Agent 可直接执行的格式，例如：
     - `点击 text="房态房量" 的 [TextView] 元素`
     - `点击 resourceId="com.meituan.hotel:id/tab_status" 的按钮`
     - `在 [EditText] className="android.widget.EditText" 中输入 "搜索内容"`
     - `向下滚动页面`
   - **不要**把具体元素信息概括成模糊的自然语言（如"找到并进入房态房量页面"）
4. 每个操作步骤需注明所在的 **Activity**（从帧数据的 activityName 获取）和 **包名**（packageName）
5. 如果发现有连续滚动操作，可以合并为一条（如"向下滚动 3 次"）；但点击、输入等操作不要合并
6. 使用中文编写

## 输出结构示例

```markdown
---
name: 查询房态
description: 在美团酒店商家后台查询指定日期的房态房量
category: recorded
platform: android
---

# 查询房态

<!-- PATH:FIXED -->
## 步骤 1：进入房态房量页面（固定路径）

> 前提：已打开美团酒店 app（包名: com.meituan.hotel），当前在 MainActivity。
> ⚡ 以下为录制的固定操作路径，按顺序精确执行即可。

1. 点击 text="工作台" 的 [TextView] (Activity: MainActivity, 包名: com.meituan.hotel)
2. 向下滚动 2 次
3. 点击 text="房态房量" 的 [TextView] (Activity: MainActivity, 包名: com.meituan.hotel)
4. 点击 text="日历视图" 的 [Tab] (Activity: RoomStatusActivity, 包名: com.meituan.hotel)

<!-- THINK:DYNAMIC -->
## 步骤 2：选择目标日期（动态推理）

> 🧠 以下需要根据用户指令动态判断。

根据用户指定的日期，在日历上找到对应日期并点击。如果目标日期不在当前可视范围内，需要向右滑动日历翻页。
```""".trimIndent()

        return if (!draftText.isNullOrBlank()) {
            base + "\n\n7. 用户在构建前提供了意图描述（草稿），请优先参考该描述来理解操作目的，并据此优化 Skill 的 name、description 和步骤描述"
        } else {
            base
        }
    }

    private fun buildUserPrompt(
        session: RecordingSession,
        stepsText: String
    ): String {
        val draftSection = if (!session.draftText.isNullOrBlank()) {
            "## 用户意图描述\n${session.draftText}\n\n"
        } else ""

        val recordSteps = session.steps.filterIsInstance<BuilderStep.Record>()
        val thinkSteps = session.steps.filterIsInstance<BuilderStep.Think>()
        val totalFrames = recordSteps.sumOf { it.frames.size }
        val durationSec = session.duration / 1000

        return """
${draftSection}## 构建概要
- 总步骤数: ${session.steps.size} (${recordSteps.size} 个录制步骤, ${thinkSteps.size} 个思考步骤)
- 总帧数: $totalFrames
- 时长: ${durationSec}秒

## 步骤详情:

$stepsText

请根据以上步骤生成 SKILL.md 文件内容。
""".trimIndent()
    }

    /**
     * 直接从录制数据生成 SKILL.md，不使用 AI。
     * 录制的操作序列本身就是完整的操作指令（像说明书一样）。
     * ClickTargetInferrer 已在录制阶段将点击目标信息写入 EventDetails。
     */
    fun generateDirectSkillMd(session: RecordingSession): String {
        val sb = StringBuilder()
        val title = session.draftText?.takeIf { it.isNotBlank() } ?: "录制的操作流程"

        sb.appendLine("---")
        sb.appendLine("name: ${title.take(50)}")
        sb.appendLine("description: $title")
        sb.appendLine("category: recorded")
        sb.appendLine("platform: android")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# $title")
        sb.appendLine()

        var stepNum = 1
        for (step in session.steps) {
            when (step) {
                is BuilderStep.Record -> {
                    sb.appendLine("<!-- PATH:FIXED -->")
                    val sectionTitle = if (step.label.isNotBlank()) step.label else "固定操作路径"
                    sb.appendLine("## 步骤 $stepNum: $sectionTitle（固定路径）")
                    sb.appendLine()

                    // 前提条件
                    val firstFrame = step.frames.firstOrNull()
                    val mainPackage = step.frames.mapNotNull { it.packageName }
                        .groupingBy { it }.eachCount()
                        .maxByOrNull { it.value }?.key
                    if (mainPackage != null || firstFrame?.activityName != null) {
                        val parts = mutableListOf<String>()
                        if (mainPackage != null) parts.add("应用 $mainPackage")
                        if (firstFrame?.activityName != null) parts.add("当前在 ${firstFrame.activityName}")
                        sb.appendLine("> 前提：已打开${parts.joinToString("，")}")
                    }
                    sb.appendLine("> ⚡ 以下为录制的固定操作路径，按顺序精确执行即可。")
                    sb.appendLine()

                    // 合并连续 SCROLL
                    val condensed = FrameSimplifier.condenseFrames(step.frames)
                    var actionNum = 1
                    for (frame in condensed) {
                        val desc = formatFrameAsInstruction(frame)
                        val activityPart = frame.activityName?.let { " (Activity: $it)" } ?: ""
                        sb.appendLine("$actionNum. $desc$activityPart")
                        actionNum++
                    }
                    sb.appendLine()
                }
                is BuilderStep.Think -> {
                    sb.appendLine("<!-- THINK:DYNAMIC -->")
                    sb.appendLine("## 步骤 $stepNum: ${step.content.lines().firstOrNull()?.take(30) ?: "动态推理"}（动态推理）")
                    sb.appendLine()
                    sb.appendLine("> 🧠 以下需要根据用户指令动态判断。")
                    sb.appendLine()
                    sb.appendLine(step.content)
                    sb.appendLine()
                }
            }
            stepNum++
        }

        return sb.toString()
    }

    /**
     * 将单个帧格式化为操作指令文本。
     * 直接使用 EventDetails 中的元素信息（ClickTargetInferrer 已在录制阶段填充）。
     */
    private fun formatFrameAsInstruction(frame: RecordingFrame): String {
        val details = frame.eventDetails
        val mergedCount = details.additionalData["mergedCount"]

        return when (frame.eventType) {
            "CLICK" -> {
                val selector = buildElementSelector(details)
                if (selector == "元素") {
                    // 没有任何定位信息，尝试从 previousUiHierarchy 提取候选
                    if (frame.previousUiHierarchy.isNotBlank()) {
                        val candidates = extractClickableSummary(frame.previousUiHierarchy)
                        if (candidates.isNotBlank()) {
                            "点击操作（界面可点击元素：$candidates）"
                        } else {
                            "点击操作"
                        }
                    } else {
                        "点击操作"
                    }
                } else {
                    "点击 $selector"
                }
            }
            "LONG_CLICK" -> {
                val selector = buildElementSelector(details)
                "长按 $selector"
            }
            "TEXT_INPUT" -> {
                val input = details.inputText ?: details.text ?: ""
                val cls = details.className ?: "EditText"
                val ridPart = if (!details.resourceId.isNullOrBlank()) " resourceId=\"${details.resourceId}\"" else ""
                "在 [$cls]$ridPart 中输入 \"$input\""
            }
            "SCROLL" -> {
                if (mergedCount != null) "向下滚动 ${mergedCount} 次" else "向下滚动"
            }
            "SCREEN_CHANGE" -> {
                "页面切换到 ${frame.activityName ?: "新页面"}"
            }
            else -> frame.eventType
        }
    }

    /**
     * Extract a brief summary of clickable elements from UI hierarchy XML.
     * Returns a comma-separated list like: text="工作台", text="房态房量", ID=com.xx:id/btn
     */
    private fun extractClickableSummary(xml: String): String {
        if (xml.isBlank()) return ""
        return try {
            val clickables = mutableListOf<String>()
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser().apply { setInput(java.io.StringReader(xml)) }

            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "node") {
                    val clickable = parser.getAttributeValue(null, "clickable") == "true"
                    if (clickable) {
                        val text = parser.getAttributeValue(null, "text")?.takeIf { it.isNotBlank() }
                        val desc = parser.getAttributeValue(null, "content-desc")?.takeIf { it.isNotBlank() }
                        val resId = parser.getAttributeValue(null, "resource-id")?.takeIf { it.isNotBlank() }
                        val label = when {
                            text != null -> "text=\"${text.take(20)}\""
                            desc != null -> "desc=\"${desc.take(20)}\""
                            resId != null -> "ID=$resId"
                            else -> null
                        }
                        if (label != null && clickables.size < 8) {
                            clickables.add(label)
                        }
                    }
                }
                parser.next()
            }
            clickables.joinToString(", ")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 构建元素选择器描述，保留尽可能多的定位信息
     */
    private fun buildElementSelector(details: EventDetails): String {
        val parts = mutableListOf<String>()
        val cls = details.className
        if (cls != null) parts.add("[$cls]")
        if (!details.text.isNullOrBlank()) parts.add("text=\"${details.text}\"")
        if (!details.contentDescription.isNullOrBlank()) parts.add("contentDescription=\"${details.contentDescription}\"")
        if (!details.resourceId.isNullOrBlank()) parts.add("resourceId=\"${details.resourceId}\"")
        return if (parts.isEmpty()) "元素" else parts.joinToString(" ")
    }
}
