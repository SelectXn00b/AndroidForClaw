package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import java.io.File

/**
 * Skill Status Builder
 *
 * Aligns with OpenClaw's buildWorkspaceSkillStatus()
 * Scans skill directories, evaluates skill eligibility, generates status report
 */
class SkillStatusBuilder(private val context: Context) {
    companion object {
        private const val TAG = "SkillStatusBuilder"
    }

    private val configLoader = ConfigLoader(context)
    private val parser = SkillFrontmatterParser()

    /**
     * Build skill status report
     *
     * @param workspacePath Workspace path (default: /sdcard/.androidforclaw/workspace)
     * @return SkillStatusReport
     */
    fun buildStatus(workspacePath: String = "/sdcard/.androidforclaw/workspace"): SkillStatusReport {
        val managedSkillsDir = "/sdcard/.androidforclaw/skills"
        val bundledSkillsPath = "skills" // assets path

        val skillEntries = mutableListOf<SkillStatusEntry>()

        // 1. Load bundled skills
        Log.d(TAG, "Loading bundled skills from assets://$bundledSkillsPath")
        loadSkillsFromAssets(bundledSkillsPath).forEach { entry ->
            skillEntries.add(buildStatusEntry(entry, SkillSource.BUNDLED))
        }

        // 2. Load managed skills
        val managedDir = File(managedSkillsDir)
        if (managedDir.exists() && managedDir.isDirectory) {
            Log.d(TAG, "Loading managed skills from $managedSkillsDir")
            loadSkillsFromDirectory(managedDir).forEach { entry ->
                skillEntries.add(buildStatusEntry(entry, SkillSource.MANAGED))
            }
        }

        // 3. Load workspace skills
        val workspaceSkillsDir = File(workspacePath, "skills")
        if (workspaceSkillsDir.exists() && workspaceSkillsDir.isDirectory) {
            Log.d(TAG, "Loading workspace skills from ${workspaceSkillsDir.absolutePath}")
            loadSkillsFromDirectory(workspaceSkillsDir).forEach { entry ->
                skillEntries.add(buildStatusEntry(entry, SkillSource.WORKSPACE))
            }
        }

        Log.i(TAG, "✅ Loaded ${skillEntries.size} skills total")

        return SkillStatusReport(
            workspaceDir = workspacePath,
            managedSkillsDir = managedSkillsDir,
            skills = skillEntries
        )
    }

    /**
     * Load skills from assets
     */
    private fun loadSkillsFromAssets(assetsPath: String): List<SkillEntry> {
        val entries = mutableListOf<SkillEntry>()

        try {
            val assetManager = context.assets
            val skillDirs = assetManager.list(assetsPath) ?: emptyArray()

            for (skillDir in skillDirs) {
                val skillPath = "$assetsPath/$skillDir"
                val files = assetManager.list(skillPath) ?: emptyArray()

                if ("SKILL.md" in files) {
                    val skillMdPath = "$skillPath/SKILL.md"
                    val content = assetManager.open(skillMdPath).bufferedReader().use { it.readText() }

                    val parseResult = parser.parse(content)
                    if (parseResult is SkillFrontmatterParser.ParseResult.Success) {
                        entries.add(
                            SkillEntry(
                                skill = Skill(
                                    name = parseResult.frontmatter.name,
                                    description = parseResult.frontmatter.description,
                                    content = parseResult.content,
                                    filePath = "assets://$skillMdPath"
                                ),
                                frontmatter = parseResult.frontmatter,
                                metadata = parseResult.openclawMetadata
                            )
                        )
                    } else if (parseResult is SkillFrontmatterParser.ParseResult.Error) {
                        Log.w(TAG, "Failed to parse $skillMdPath: ${parseResult.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load skills from assets", e)
        }

        return entries
    }

    /**
     * Load skills from filesystem directory
     */
    private fun loadSkillsFromDirectory(directory: File): List<SkillEntry> {
        val entries = mutableListOf<SkillEntry>()

        directory.listFiles()?.forEach { skillDir ->
            if (skillDir.isDirectory) {
                val skillMdFile = File(skillDir, "SKILL.md")
                if (skillMdFile.exists()) {
                    try {
                        val content = skillMdFile.readText()
                        val parseResult = parser.parse(content)

                        if (parseResult is SkillFrontmatterParser.ParseResult.Success) {
                            entries.add(
                                SkillEntry(
                                    skill = Skill(
                                        name = parseResult.frontmatter.name,
                                        description = parseResult.frontmatter.description,
                                        content = parseResult.content,
                                        filePath = skillMdFile.absolutePath
                                    ),
                                    frontmatter = parseResult.frontmatter,
                                    metadata = parseResult.openclawMetadata
                                )
                            )
                        } else if (parseResult is SkillFrontmatterParser.ParseResult.Error) {
                            Log.w(TAG, "Failed to parse ${skillMdFile.absolutePath}: ${parseResult.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to read ${skillMdFile.absolutePath}", e)
                    }
                }
            }
        }

        return entries
    }

    /**
     * Build status entry for a single skill
     */
    private fun buildStatusEntry(entry: SkillEntry, source: SkillSource): SkillStatusEntry {
        val config = configLoader.loadOpenClawConfig()
        val skillConfig = config.skills.entries[entry.skill.name]

        // Check if disabled by config
        val disabled = skillConfig?.enabled == false

        // Check if blocked by allowlist (bundled skills need to be in allowlist)
        val blockedByAllowlist = if (source == SkillSource.BUNDLED) {
            val allowBundled = config.skills.allowBundled
            allowBundled != null && entry.skill.name !in allowBundled
        } else {
            false
        }

        // Check requirements and missing items
        val (requirements, missing) = checkRequirements(entry.metadata?.requires)

        // Check config paths
        val configChecks = checkConfigPaths(entry.metadata?.requires?.config, config)

        // Check platform compatibility
        val platformCompatible = checkPlatformCompatibility(entry.metadata?.os)

        // Evaluate eligibility
        val eligible = !disabled &&
                !blockedByAllowlist &&
                platformCompatible &&
                (missing == null || (missing.bins.isNullOrEmpty() &&
                        missing.anyBins.isNullOrEmpty() &&
                        missing.env.isNullOrEmpty() &&
                        missing.config.isNullOrEmpty()))

        // Build install options
        val installOptions = buildInstallOptions(entry.metadata?.install)

        return SkillStatusEntry(
            name = entry.skill.name,
            description = entry.skill.description,
            source = source,
            bundled = source == SkillSource.BUNDLED,
            filePath = entry.skill.filePath,
            baseDir = File(entry.skill.filePath).parent ?: "",
            skillKey = entry.metadata?.skillKey ?: entry.skill.name,
            primaryEnv = entry.metadata?.primaryEnv,
            emoji = entry.metadata?.emoji,
            homepage = entry.metadata?.homepage,
            always = entry.metadata?.always ?: false,
            disabled = disabled,
            blockedByAllowlist = blockedByAllowlist,
            eligible = eligible,
            requirements = requirements,
            missing = missing,
            configChecks = configChecks,
            install = installOptions
        )
    }

    /**
     * Check skill requirements
     *
     * @return Pair<requirements, missing>
     */
    private fun checkRequirements(requires: SkillRequirements?): Pair<SkillRequirements?, SkillRequirements?> {
        if (requires == null) {
            return Pair(null, null)
        }

        val missingBins = mutableListOf<String>()
        val missingAnyBins = mutableListOf<String>()
        val missingEnv = mutableListOf<String>()
        val missingConfig = mutableListOf<String>()

        // Check binaries (not applicable on Android, skip)
        // Android doesn't use PATH binaries, uses permissions and APKs instead

        // Check environment variables
        requires.env?.forEach { envVar ->
            if (System.getenv(envVar) == null) {
                missingEnv.add(envVar)
            }
        }

        // anyBins - at least one must exist (not applicable on Android)

        // config path checks handled in checkConfigPaths

        val missing = if (missingBins.isNotEmpty() ||
            missingAnyBins.isNotEmpty() ||
            missingEnv.isNotEmpty() ||
            missingConfig.isNotEmpty()
        ) {
            SkillRequirements(
                bins = missingBins.takeIf { it.isNotEmpty() },
                anyBins = missingAnyBins.takeIf { it.isNotEmpty() },
                env = missingEnv.takeIf { it.isNotEmpty() },
                config = missingConfig.takeIf { it.isNotEmpty() }
            )
        } else {
            null
        }

        return Pair(requires, missing)
    }

    /**
     * Check config paths
     */
    private fun checkConfigPaths(
        configPaths: List<String>?,
        config: com.xiaomo.androidforclaw.config.OpenClawConfig
    ): List<SkillConfigCheck> {
        if (configPaths == null) {
            return emptyList()
        }

        return configPaths.map { path ->
            val value = getConfigValue(path, config)
            SkillConfigCheck(
                path = path,
                exists = value != null,
                value = value
            )
        }
    }

    /**
     * Get config value (simplified implementation)
     */
    private fun getConfigValue(path: String, config: com.xiaomo.androidforclaw.config.OpenClawConfig): Any? {
        // Simple path parsing (supports "gateway.enabled", "agent.maxIterations", etc.)
        val parts = path.split(".")
        return when {
            parts.size == 2 && parts[0] == "gateway" && parts[1] == "enabled" -> config.gateway.enabled
            parts.size == 2 && parts[0] == "agent" && parts[1] == "maxIterations" -> config.agent.maxIterations
            // Can be extended for more paths
            else -> null
        }
    }

    /**
     * Check platform compatibility
     */
    private fun checkPlatformCompatibility(osList: List<String>?): Boolean {
        if (osList == null) {
            return true // No restrictions
        }

        // Android platform identifier
        return "android" in osList.map { it.lowercase() }
    }

    /**
     * Build install options
     */
    private fun buildInstallOptions(installSpecs: List<SkillInstallSpec>?): List<SkillInstallOption> {
        if (installSpecs == null) {
            return emptyList()
        }

        return installSpecs.map { spec ->
            val (available, reason) = checkInstallAvailability(spec)

            SkillInstallOption(
                installId = spec.id ?: "${spec.kind.name.lowercase()}-default",
                kind = spec.kind,
                label = spec.label ?: "Install via ${spec.kind.name}",
                available = available,
                reason = reason
            )
        }
    }

    /**
     * Check installer availability
     */
    private fun checkInstallAvailability(spec: SkillInstallSpec): Pair<Boolean, String?> {
        // Android platform check
        val platformCompatible = spec.os?.let { osList ->
            "android" in osList.map { it.lowercase() }
        } ?: true

        if (!platformCompatible) {
            return Pair(false, "Platform not supported")
        }

        // Check based on installer type
        return when (spec.kind) {
            InstallKind.APK -> {
                // APK install (Android-specific)
                if (spec.url != null) {
                    Pair(true, null)
                } else {
                    Pair(false, "Missing APK URL")
                }
            }

            InstallKind.DOWNLOAD -> {
                // Direct download
                if (spec.url != null) {
                    Pair(true, null)
                } else {
                    Pair(false, "Missing download URL")
                }
            }

            else -> {
                // brew, node, go, uv not available on Android
                Pair(false, "${spec.kind.name} not available on Android")
            }
        }
    }
}
