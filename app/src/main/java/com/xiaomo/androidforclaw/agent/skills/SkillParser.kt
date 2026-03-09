package com.xiaomo.androidforclaw.agent.skills

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Skill Document Parser
 * Supports AgentSkills.io format
 *
 * Format specification:
 * ---
 * name: skill-name
 * description: Skill description
 * metadata: { "openclaw": { ... } }
 * ---
 * # Markdown Content
 */
object SkillParser {
    private const val TAG = "SkillParser"
    private val gson = Gson()

    /**
     * Parse Skill document
     *
     * @param content Full content of SKILL.md file
     * @return SkillDocument
     * @throws IllegalArgumentException If format is incorrect
     */
    fun parse(content: String): SkillDocument {
        try {
            // 1. Split frontmatter and body
            val (frontmatter, body) = splitFrontmatter(content)

            // 2. Parse frontmatter fields
            val name = extractYamlField(frontmatter, "name")
            val description = extractYamlField(frontmatter, "description")
            val metadataJson = extractYamlField(frontmatter, "metadata")

            // 3. Validate required fields
            if (name.isEmpty()) {
                throw IllegalArgumentException("Missing required field: name")
            }
            if (description.isEmpty()) {
                throw IllegalArgumentException("Missing required field: description")
            }

            // 4. Parse metadata
            val metadata = parseMetadata(metadataJson)

            return SkillDocument(
                name = name,
                description = description,
                metadata = metadata,
                content = body
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse skill document", e)
            throw IllegalArgumentException("Invalid skill format: ${e.message}", e)
        }
    }

    /**
     * Split YAML frontmatter and Markdown body
     *
     * Input:
     * ---
     * name: test
     * ---
     * # Content
     *
     * Output: ("name: test", "# Content")
     */
    private fun splitFrontmatter(content: String): Pair<String, String> {
        // Split using "---" delimiter regex
        val parts = content.split(Regex("^---\\s*$", RegexOption.MULTILINE))

        if (parts.size < 3) {
            throw IllegalArgumentException(
                "Invalid format: missing frontmatter delimiters (---)"
            )
        }

        val frontmatter = parts[1].trim()
        val body = parts.drop(2).joinToString("---").trim()

        return Pair(frontmatter, body)
    }

    /**
     * Extract YAML field value
     *
     * Supported formats:
     * 1. Single line: name: value
     * 2. Multi-line JSON: metadata: { "key": "value" }
     * 3. Multi-line JSON (spanning lines): metadata:
     *                        {
     *                          "key": "value"
     *                        }
     */
    private fun extractYamlField(yaml: String, field: String): String {
        // Try to match single-line format: field: value
        val singleLineRegex = Regex("$field:\\s*([^\\n{]+)")
        val singleLineMatch = singleLineRegex.find(yaml)
        if (singleLineMatch != null) {
            val value = singleLineMatch.groupValues[1].trim()
            // If not JSON start, return the value
            if (!value.isEmpty() && !yaml.substring(singleLineMatch.range.last).trimStart().startsWith("{")) {
                return value
            }
        }

        // Try to match multi-line JSON: field: { ... } or field:\n  { ... }
        // Use brace counting to correctly extract nested JSON
        val fieldPattern = "$field:\\s*"
        val fieldStart = yaml.indexOf(fieldPattern)
        if (fieldStart == -1) {
            return ""
        }

        val jsonStart = yaml.indexOf('{', fieldStart + fieldPattern.length)
        if (jsonStart == -1) {
            return ""
        }

        // Start from {, count braces until matched
        var braceCount = 0
        var jsonEnd = jsonStart
        while (jsonEnd < yaml.length) {
            when (yaml[jsonEnd]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        // Found matching closing brace
                        val jsonStr = yaml.substring(jsonStart, jsonEnd + 1)
                        // Remove newlines and extra spaces from JSON, keep compact format
                        return jsonStr.replace(Regex("\\s+"), " ").trim()
                    }
                }
            }
            jsonEnd++
        }

        return ""
    }

    /**
     * Parse metadata JSON
     *
     * Format:
     * {
     *   "openclaw": {
     *     "always": true,
     *     "emoji": "📱",
     *     "requires": {
     *       "bins": ["adb"],
     *       "env": ["ANDROID_HOME"],
     *       "config": ["api.key"]
     *     }
     *   }
     * }
     */
    private fun parseMetadata(json: String): SkillMetadata {
        if (json.isEmpty()) {
            return SkillMetadata()
        }

        return try {
            Log.d(TAG, "Parsing metadata JSON (length=${json.length}): $json")
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            val openclaw = jsonObj.getAsJsonObject("openclaw")

            if (openclaw == null) {
                Log.w(TAG, "metadata.openclaw not found, using defaults")
                return SkillMetadata()
            }

            SkillMetadata(
                always = openclaw.get("always")?.asBoolean ?: false,
                emoji = openclaw.get("emoji")?.asString,
                requires = parseRequires(openclaw)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse metadata JSON (length=${json.length}): $json", e)
            SkillMetadata()
        }
    }

    /**
     * Parse requires field
     */
    private fun parseRequires(openclaw: JsonObject): SkillRequires? {
        val requiresObj = openclaw.getAsJsonObject("requires") ?: return null

        return try {
            SkillRequires(
                bins = jsonArrayToList(requiresObj.getAsJsonArray("bins")),
                env = jsonArrayToList(requiresObj.getAsJsonArray("env")),
                config = jsonArrayToList(requiresObj.getAsJsonArray("config"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse requires", e)
            null
        }
    }

    /**
     * Convert JsonArray to List<String>
     */
    private fun jsonArrayToList(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        return array.mapNotNull { it.asString }
    }

    /**
     * Validate Skill document format
     *
     * @return Validation result, returns null on success, error message on failure
     */
    fun validate(content: String): String? {
        return try {
            parse(content)
            null  // Validation successful
        } catch (e: Exception) {
            e.message  // Return error message
        }
    }
}
