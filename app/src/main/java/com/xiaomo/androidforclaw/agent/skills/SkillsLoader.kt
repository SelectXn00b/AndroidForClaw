package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.tencent.mmkv.MMKV
import java.io.File

/**
 * Skills Loader
 * Implements OpenClaw's three-tier loading mechanism
 *
 * Loading priority (higher priority overrides lower):
 * 1. Bundled Skills (lowest) - assets/skills/
 * 2. Managed Skills (medium) - /sdcard/.androidforclaw/skills/
 * 3. Workspace Skills (highest) - /sdcard/.androidforclaw/workspace/skills/
 *
 * Workspace aligns with OpenClaw architecture:
 * - OpenClaw: ~/.openclaw/workspace/ (Git repo)
 * - AndroidForClaw: /sdcard/.androidforclaw/workspace/ (Git-enabled)
 * - Users can directly access and edit via file manager
 */
class SkillsLoader(private val context: Context) {
    companion object {
        private const val TAG = "SkillsLoader"

        // Three-tier Skills directories (aligns with OpenClaw architecture)
        private const val BUNDLED_SKILLS_PATH = "skills"  // assets path
        private const val MANAGED_SKILLS_DIR = "/sdcard/.androidforclaw/skills"  // aligns with ~/.openclaw/skills/
        private const val WORKSPACE_SKILLS_DIR = "/sdcard/.androidforclaw/workspace/skills"  // aligns with ~/.openclaw/workspace/

        // Skill file name
        private const val SKILL_FILE_NAME = "SKILL.md"
    }

    // Skills cache
    private val skillsCache = mutableMapOf<String, SkillDocument>()
    private var cacheValid = false

    // File monitoring (Block 6 - hot reload)
    private var fileObserver: FileObserver? = null
    private var hotReloadEnabled = false

    /**
     * Load all Skills
     * Priority override: Workspace > Managed > Bundled
     *
     * @return Map<name, SkillDocument>
     */
    fun loadSkills(): Map<String, SkillDocument> {
        // Return cached if valid
        if (cacheValid && skillsCache.isNotEmpty()) {
            Log.d(TAG, "返回缓存的 Skills (${skillsCache.size} 个)")
            return skillsCache.toMap()
        }

        Log.d(TAG, "开始加载 Skills...")
        skillsCache.clear()

        // Load by priority, higher priority overrides lower
        val bundledCount = loadBundledSkills(skillsCache)
        val managedCount = loadManagedSkills(skillsCache)
        val workspaceCount = loadWorkspaceSkills(skillsCache)

        cacheValid = true

        Log.i(TAG, "Skills 加载完成: 总计 ${skillsCache.size} 个")
        Log.i(TAG, "  - Bundled: $bundledCount")
        Log.i(TAG, "  - Managed: $managedCount (覆盖)")
        Log.i(TAG, "  - Workspace: $workspaceCount (覆盖)")

        return skillsCache.toMap()
    }

    /**
     * Reload Skills (clear cache)
     */
    fun reload() {
        Log.i(TAG, "重新加载 Skills...")
        cacheValid = false
        loadSkills()
    }

    /**
     * Enable hot reload (Block 6)
     * Monitor Workspace and Managed directories, auto reload on file changes
     */
    fun enableHotReload() {
        if (hotReloadEnabled) {
            Log.d(TAG, "热重载已启用")
            return
        }

        try {
            // Monitor Workspace Skills directory
            val workspaceDir = File(WORKSPACE_SKILLS_DIR)
            if (workspaceDir.exists()) {
                fileObserver = object : FileObserver(workspaceDir, CREATE or MODIFY or DELETE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && path.endsWith(SKILL_FILE_NAME)) {
                            Log.i(TAG, "检测到 Skill 文件变化: $path")
                            Log.i(TAG, "自动重新加载 Skills...")
                            reload()
                        }
                    }
                }
                fileObserver?.startWatching()
                hotReloadEnabled = true
                Log.i(TAG, "✅ 热重载已启用 - 监控: $WORKSPACE_SKILLS_DIR")
            } else {
                Log.w(TAG, "Workspace 目录不存在，跳过热重载")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启用热重载失败", e)
        }
    }

    /**
     * Disable hot reload (Block 6)
     */
    fun disableHotReload() {
        fileObserver?.stopWatching()
        fileObserver = null
        hotReloadEnabled = false
        Log.i(TAG, "热重载已禁用")
    }

    /**
     * Check if hot reload is enabled
     */
    fun isHotReloadEnabled(): Boolean = hotReloadEnabled

    /**
     * Get Always Skills (always-loaded skills)
     * These skills are loaded into system prompt at startup
     */
    fun getAlwaysSkills(): List<SkillDocument> {
        val allSkills = loadSkills()
        val alwaysSkills = allSkills.values.filter { it.metadata.always }
        Log.d(TAG, "Always Skills: ${alwaysSkills.size} 个")
        return alwaysSkills
    }

    /**
     * Select relevant Skills based on user goal (Block 5 improvement)
     *
     * @param userGoal User goal/instruction
     * @param excludeAlways Whether to exclude always skills (avoid duplication)
     * @return List of relevant Skills
     */
    fun selectRelevantSkills(
        userGoal: String,
        excludeAlways: Boolean = true
    ): List<SkillDocument> {
        val allSkills = loadSkills()
        val keywords = userGoal.lowercase()

        // 1. Use task type identification
        val recommendedSkillNames = identifyTaskType(userGoal)

        // 2. Keyword matching
        val relevant = allSkills.values.filter { skill ->
            // Exclude always skills (avoid duplicate injection)
            if (excludeAlways && skill.metadata.always) {
                return@filter false
            }

            // Prioritize task type recommendations
            if (recommendedSkillNames.contains(skill.name)) {
                return@filter true
            }

            // Then try keyword matching
            keywords.contains(skill.name.lowercase()) ||
                    keywords.contains(skill.description.lowercase()) ||
                    matchesKeywords(skill, keywords)
        }

        Log.d(TAG, "选择相关 Skills: ${relevant.size} 个")
        for (skill in relevant) {
            Log.d(TAG, "  - ${skill.name} (${skill.description})")
        }

        return relevant
    }

    /**
     * Check if Skill's dependency requirements are met
     */
    fun checkRequirements(skill: SkillDocument): RequirementsCheckResult {
        val requires = skill.metadata.requires
            ?: return RequirementsCheckResult.Satisfied

        if (!requires.hasRequirements()) {
            return RequirementsCheckResult.Satisfied
        }

        val missingBins = requires.bins.filter { !isBinaryAvailable(it) }
        val missingEnv = requires.env.filter { System.getenv(it) == null }
        val missingConfig = requires.config.filter { !isConfigAvailable(it) }

        if (missingBins.isEmpty() && missingEnv.isEmpty() && missingConfig.isEmpty()) {
            return RequirementsCheckResult.Satisfied
        }

        return RequirementsCheckResult.Unsatisfied(
            missingBins = missingBins,
            missingEnv = missingEnv,
            missingConfig = missingConfig
        )
    }

    /**
     * Get Skill statistics
     */
    fun getStatistics(): SkillsStatistics {
        val skills = loadSkills()
        val alwaysSkills = skills.values.count { it.metadata.always }
        val onDemandSkills = skills.size - alwaysSkills
        val totalTokens = skills.values.sumOf { it.estimateTokens() }
        val alwaysTokens = skills.values.filter { it.metadata.always }.sumOf { it.estimateTokens() }

        return SkillsStatistics(
            totalSkills = skills.size,
            alwaysSkills = alwaysSkills,
            onDemandSkills = onDemandSkills,
            totalTokens = totalTokens,
            alwaysTokens = alwaysTokens
        )
    }

    // ==================== Private Methods ====================

    /**
     * Load bundled Skills from assets/skills/ (Bundled)
     * Priority: Lowest
     */
    private fun loadBundledSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0

        try {
            val skillDirs = context.assets.list(BUNDLED_SKILLS_PATH) ?: emptyArray()
            Log.d(TAG, "扫描 Bundled Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillPath = "$BUNDLED_SKILLS_PATH/$dir/$SKILL_FILE_NAME"
                try {
                    val content = context.assets.open(skillPath)
                        .bufferedReader().use { it.readText() }

                    val skill = SkillParser.parse(content).copy(source = SkillSource.BUNDLED)
                    skills[skill.name] = skill
                    count++

                    Log.d(TAG, "✅ Bundled: ${skill.name} (${skill.estimateTokens()} tokens)")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Bundled Skill 失败: $dir - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Bundled Skills 失败", e)
        }

        return count
    }

    /**
     * Load managed Skills from /sdcard/.androidforclaw/skills/ (Managed)
     * Priority: Medium (overrides Bundled)
     *
     * Aligns with OpenClaw: ~/.openclaw/skills/
     */
    private fun loadManagedSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0
        val managedDir = File(MANAGED_SKILLS_DIR)

        if (!managedDir.exists()) {
            Log.d(TAG, "Managed Skills 目录不存在: $MANAGED_SKILLS_DIR")
            return 0
        }

        try {
            val skillDirs = managedDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            Log.d(TAG, "扫描 Managed Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillFile = File(dir, SKILL_FILE_NAME)
                if (!skillFile.exists()) {
                    Log.w(TAG, "Managed Skill 文件不存在: ${dir.name}/$SKILL_FILE_NAME")
                    continue
                }

                try {
                    val content = skillFile.readText()
                    val skill = SkillParser.parse(content).copy(source = SkillSource.MANAGED)

                    val isOverride = skills.containsKey(skill.name)
                    skills[skill.name] = skill
                    count++

                    val action = if (isOverride) "覆盖" else "新增"
                    Log.d(TAG, "✅ Managed ($action): ${skill.name} (${skill.estimateTokens()} tokens)")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Managed Skill 失败: ${dir.name} - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Managed Skills 失败", e)
        }

        return count
    }

    /**
     * Load workspace Skills from /sdcard/.androidforclaw/workspace/skills/ (Workspace)
     * Priority: Highest (overrides Bundled and Managed)
     *
     * Aligns with OpenClaw architecture:
     * - OpenClaw: ~/.openclaw/workspace/ (Git repo)
     * - AndroidForClaw: /sdcard/.androidforclaw/workspace/ (Git-enabled)
     * - workspace/ is the user's main workspace, supports version control
     */
    private fun loadWorkspaceSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0
        val workspaceDir = File(WORKSPACE_SKILLS_DIR)

        if (!workspaceDir.exists()) {
            Log.d(TAG, "Workspace Skills 目录不存在: $WORKSPACE_SKILLS_DIR")
            return 0
        }

        try {
            val skillDirs = workspaceDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            Log.d(TAG, "扫描 Workspace Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillFile = File(dir, SKILL_FILE_NAME)
                if (!skillFile.exists()) {
                    Log.w(TAG, "Workspace Skill 文件不存在: ${dir.name}/$SKILL_FILE_NAME")
                    continue
                }

                try {
                    val content = skillFile.readText()
                    val skill = SkillParser.parse(content).copy(source = SkillSource.WORKSPACE)

                    val isOverride = skills.containsKey(skill.name)
                    skills[skill.name] = skill
                    count++

                    val action = if (isOverride) "覆盖" else "新增"
                    Log.d(TAG, "✅ Workspace ($action): ${skill.name} (${skill.estimateTokens()} tokens)")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Workspace Skill 失败: ${dir.name} - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Workspace Skills 失败", e)
        }

        return count
    }

    /**
     * Keyword matching (Block 5 improvement)
     * Used to determine if Skill is relevant to user goal
     */
    private fun matchesKeywords(skill: SkillDocument, keywords: String): Boolean {
        // Predefined keyword mappings
        return when (skill.name) {
            "app-testing" -> {
                keywords.contains("测试") || keywords.contains("test") ||
                keywords.contains("检查") || keywords.contains("验证") ||
                keywords.contains("功能") || keywords.contains("用例")
            }
            "debugging" -> {
                keywords.contains("调试") || keywords.contains("debug") ||
                keywords.contains("bug") || keywords.contains("错误") ||
                keywords.contains("问题") || keywords.contains("异常") ||
                keywords.contains("崩溃")
            }
            "accessibility" -> {
                keywords.contains("无障碍") || keywords.contains("accessibility") ||
                keywords.contains("wcag") || keywords.contains("适配") ||
                keywords.contains("可读性") || keywords.contains("对比度")
            }
            "performance" -> {
                keywords.contains("性能") || keywords.contains("performance") ||
                keywords.contains("优化") || keywords.contains("卡顿") ||
                keywords.contains("流畅") || keywords.contains("启动") ||
                keywords.contains("加载") || keywords.contains("慢")
            }
            "ui-validation" -> {
                keywords.contains("ui") || keywords.contains("界面") ||
                keywords.contains("布局") || keywords.contains("显示") ||
                keywords.contains("页面") || keywords.contains("视觉")
            }
            "network-testing" -> {
                keywords.contains("网络") || keywords.contains("network") ||
                keywords.contains("联网") || keywords.contains("在线") ||
                keywords.contains("离线") || keywords.contains("断网") ||
                keywords.contains("api") || keywords.contains("请求")
            }
            else -> false
        }
    }

    /**
     * Task type identification (Block 5 addition)
     * Identify task type based on user goal, return recommended Skills
     */
    private fun identifyTaskType(userGoal: String): List<String> {
        val keywords = userGoal.lowercase()
        val recommendedSkills = mutableListOf<String>()

        // Testing tasks
        if (keywords.contains("测试") || keywords.contains("test") ||
            keywords.contains("验证") || keywords.contains("检查")) {
            recommendedSkills.add("app-testing")
        }

        // Debugging tasks
        if (keywords.contains("调试") || keywords.contains("debug") ||
            keywords.contains("bug") || keywords.contains("问题") ||
            keywords.contains("错误") || keywords.contains("崩溃")) {
            recommendedSkills.add("debugging")
        }

        // UI validation tasks
        if (keywords.contains("界面") || keywords.contains("ui") ||
            keywords.contains("布局") || keywords.contains("显示") ||
            keywords.contains("页面")) {
            recommendedSkills.add("ui-validation")
        }

        // Performance testing tasks
        if (keywords.contains("性能") || keywords.contains("卡顿") ||
            keywords.contains("慢") || keywords.contains("优化") ||
            keywords.contains("启动") || keywords.contains("流畅")) {
            recommendedSkills.add("performance")
        }

        // Accessibility testing tasks
        if (keywords.contains("无障碍") || keywords.contains("accessibility") ||
            keywords.contains("适配") || keywords.contains("可读性")) {
            recommendedSkills.add("accessibility")
        }

        // Network testing tasks
        if (keywords.contains("网络") || keywords.contains("联网") ||
            keywords.contains("离线") || keywords.contains("断网") ||
            keywords.contains("api")) {
            recommendedSkills.add("network-testing")
        }

        Log.d(TAG, "识别任务类型: ${recommendedSkills.joinToString(", ")}")
        return recommendedSkills
    }

    /**
     * Check if binary tool is available
     */
    private fun isBinaryAvailable(bin: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which $bin")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "检查二进制工具失败: $bin", e)
            false
        }
    }

    /**
     * Check if config item is available
     */
    private fun isConfigAvailable(configKey: String): Boolean {
        return try {
            MMKV.defaultMMKV()?.containsKey(configKey) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "检查配置项失败: $configKey", e)
            false
        }
    }
}

/**
 * Requirements check result
 */
sealed class RequirementsCheckResult {
    /**
     * Requirements satisfied
     */
    object Satisfied : RequirementsCheckResult()

    /**
     * Requirements not satisfied
     */
    data class Unsatisfied(
        val missingBins: List<String>,
        val missingEnv: List<String>,
        val missingConfig: List<String>
    ) : RequirementsCheckResult() {
        fun getErrorMessage(): String {
            val parts = mutableListOf<String>()
            if (missingBins.isNotEmpty()) {
                parts.add("缺少二进制工具: ${missingBins.joinToString()}")
            }
            if (missingEnv.isNotEmpty()) {
                parts.add("缺少环境变量: ${missingEnv.joinToString()}")
            }
            if (missingConfig.isNotEmpty()) {
                parts.add("缺少配置项: ${missingConfig.joinToString()}")
            }
            return parts.joinToString("; ")
        }
    }
}

/**
 * Skills statistics
 */
data class SkillsStatistics(
    val totalSkills: Int,
    val alwaysSkills: Int,
    val onDemandSkills: Int,
    val totalTokens: Int,
    val alwaysTokens: Int
) {
    fun getReport(): String {
        return """
Skills 统计:
  - 总计: $totalSkills 个
  - Always: $alwaysSkills 个
  - On-Demand: $onDemandSkills 个
  - Token 总量: $totalTokens tokens
  - Always Token: $alwaysTokens tokens
        """.trimIndent()
    }
}
