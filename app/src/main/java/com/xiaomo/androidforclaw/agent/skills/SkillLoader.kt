package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Skill Loader - Load skills from markdown files
 *
 * Supports OpenClaw-compatible skill format:
 * - YAML frontmatter (name, description, metadata)
 * - Markdown content (skill instructions, best practices, etc.)
 *
 * Loading order (priority from high to low):
 * 1. Workspace Skills: /sdcard/.androidforclaw/workspace/skills/
 * 2. Managed Skills: /sdcard/.androidforclaw/.skills/
 * 3. Bundled Skills: assets/skills/
 */
class SkillLoader(private val context: Context) {

    companion object {
        private const val TAG = "SkillLoader"

        // Skill file paths
        private const val WORKSPACE_SKILLS_DIR = "/sdcard/.androidforclaw/workspace/skills"
        private const val MANAGED_SKILLS_DIR = "/sdcard/.androidforclaw/.skills"
        private const val BUNDLED_SKILLS_PATH = "skills"

        // File name
        private const val SKILL_FILE_NAME = "SKILL.md"
    }

    /**
     * Skill Entry - Loaded skill entry
     */
    data class SkillEntry(
        val name: String,
        val description: String,
        val content: String,
        val metadata: Map<String, Any?>,
        val filePath: String,
        val source: SkillSource
    )

    enum class SkillSource {
        BUNDLED,    // assets/skills/
        MANAGED,    // /sdcard/.androidforclaw/.skills/
        WORKSPACE   // /sdcard/.androidforclaw/workspace/skills/
    }

    /**
     * Load all skills
     */
    fun loadAllSkills(): List<SkillEntry> {
        val allSkills = mutableMapOf<String, SkillEntry>()

        // 1. Load bundled skills (lowest priority)
        loadBundledSkills().forEach { skill ->
            allSkills[skill.name] = skill
        }

        // 2. Load managed skills (overrides bundled)
        loadManagedSkills().forEach { skill ->
            allSkills[skill.name] = skill
        }

        // 3. Load workspace skills (highest priority)
        loadWorkspaceSkills().forEach { skill ->
            allSkills[skill.name] = skill
        }

        Log.d(TAG, "Loaded ${allSkills.size} skills total")
        return allSkills.values.toList()
    }

    /**
     * Load bundled skills (assets)
     */
    private fun loadBundledSkills(): List<SkillEntry> {
        val skills = mutableListOf<SkillEntry>()

        try {
            val assetManager = context.assets
            val skillDirs = assetManager.list(BUNDLED_SKILLS_PATH) ?: emptyArray()

            for (skillDir in skillDirs) {
                val skillPath = "$BUNDLED_SKILLS_PATH/$skillDir"
                try {
                    val skillFile = "$skillPath/$SKILL_FILE_NAME"
                    val content = readAssetFile(assetManager, skillFile)
                    val entry = parseSkillFile(content, skillFile, SkillSource.BUNDLED)
                    if (entry != null) {
                        skills.add(entry)
                        Log.d(TAG, "Loaded bundled skill: ${entry.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load bundled skill from $skillPath: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled skills", e)
        }

        return skills
    }

    /**
     * Load managed skills (external storage)
     */
    private fun loadManagedSkills(): List<SkillEntry> {
        return loadSkillsFromDirectory(MANAGED_SKILLS_DIR, SkillSource.MANAGED)
    }

    /**
     * Load workspace skills (external storage)
     */
    private fun loadWorkspaceSkills(): List<SkillEntry> {
        return loadSkillsFromDirectory(WORKSPACE_SKILLS_DIR, SkillSource.WORKSPACE)
    }

    /**
     * Load skills from directory
     */
    private fun loadSkillsFromDirectory(dirPath: String, source: SkillSource): List<SkillEntry> {
        val skills = mutableListOf<SkillEntry>()
        val dir = File(dirPath)

        if (!dir.exists() || !dir.isDirectory) {
            Log.d(TAG, "Skill directory not found: $dirPath")
            return skills
        }

        val skillDirs = dir.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (skillDir in skillDirs) {
            try {
                val skillFile = File(skillDir, SKILL_FILE_NAME)
                if (skillFile.exists()) {
                    val content = skillFile.readText()
                    val entry = parseSkillFile(content, skillFile.absolutePath, source)
                    if (entry != null) {
                        skills.add(entry)
                        Log.d(TAG, "Loaded ${source.name.lowercase()} skill: ${entry.name}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load skill from ${skillDir.name}: ${e.message}")
            }
        }

        return skills
    }

    /**
     * Parse skill file
     */
    private fun parseSkillFile(content: String, filePath: String, source: SkillSource): SkillEntry? {
        try {
            val (frontmatter, markdownContent) = parseFrontmatter(content)

            val name = frontmatter["name"] as? String
            val description = frontmatter["description"] as? String

            if (name == null || description == null) {
                Log.w(TAG, "Skill file missing name or description: $filePath")
                return null
            }

            // Parse metadata
            val metadata = parseFrontmatterMetadata(frontmatter)

            return SkillEntry(
                name = name,
                description = description,
                content = markdownContent,
                metadata = metadata,
                filePath = filePath,
                source = source
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse skill file: $filePath", e)
            return null
        }
    }

    /**
     * Parse frontmatter (YAML format)
     *
     * Format:
     * ---
     * name: skill_name
     * description: Skill description
     * metadata: { "openclaw": { "always": true } }
     * ---
     * # Markdown content
     */
    private fun parseFrontmatter(content: String): Pair<Map<String, Any?>, String> {
        val lines = content.lines()
        val frontmatterLines = mutableListOf<String>()
        var inFrontmatter = false
        var frontmatterEnd = 0

        for ((index, line) in lines.withIndex()) {
            if (line.trim() == "---") {
                if (!inFrontmatter) {
                    inFrontmatter = true
                } else {
                    frontmatterEnd = index
                    break
                }
            } else if (inFrontmatter) {
                frontmatterLines.add(line)
            }
        }

        // Parse frontmatter key-value pairs
        val frontmatter = mutableMapOf<String, Any?>()
        for (line in frontmatterLines) {
            if (line.contains(":")) {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    // Try to parse JSON-formatted values
                    frontmatter[key] = parseValue(value)
                }
            }
        }

        // Markdown content
        val markdownContent = lines
            .drop(frontmatterEnd + 1)
            .joinToString("\n")
            .trim()

        return Pair(frontmatter, markdownContent)
    }

    /**
     * Parse frontmatter value
     */
    private fun parseValue(value: String): Any? {
        return when {
            // JSON object or array
            value.startsWith("{") || value.startsWith("[") -> {
                try {
                    if (value.startsWith("{")) {
                        jsonObjectToMap(JSONObject(value))
                    } else {
                        // Simple handling of JSON array, return raw string
                        value
                    }
                } catch (e: Exception) {
                    value  // Parse failed, return raw string
                }
            }
            // Boolean
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            // Number
            value.toIntOrNull() != null -> value.toInt()
            value.toLongOrNull() != null -> value.toLong()
            value.toDoubleOrNull() != null -> value.toDouble()
            // String
            else -> value
        }
    }

    /**
     * Parse frontmatter metadata
     */
    private fun parseFrontmatterMetadata(frontmatter: Map<String, Any?>): Map<String, Any?> {
        val metadata = frontmatter["metadata"]
        return when (metadata) {
            is Map<*, *> -> metadata as Map<String, Any?>
            is String -> {
                try {
                    jsonObjectToMap(JSONObject(metadata))
                } catch (e: Exception) {
                    emptyMap()
                }
            }
            else -> emptyMap()
        }
    }

    /**
     * Convert JSONObject to Map
     */
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                else -> value
            }
        }
        return map
    }

    /**
     * Read asset file
     */
    private fun readAssetFile(assetManager: AssetManager, path: String): String {
        return assetManager.open(path).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }
}
