package com.xiaomo.androidforclaw.agent.skills

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * SKILL.md Frontmatter Parser
 *
 * Aligns with OpenClaw format:
 * - Supports YAML Frontmatter (--- delimiter)
 * - metadata.openclaw is single-line JSON
 * - description is single-line text
 */
class SkillFrontmatterParser {
    companion object {
        private const val TAG = "SkillFrontmatterParser"
        private val gson = Gson()
    }

    /**
     * Parse SKILL.md file content
     *
     * Format:
     * ```
     * ---
     * name: skill-name
     * description: Single line description
     * metadata: { "openclaw": { "always": true } }
     * ---
     *
     * # Skill Content
     * ...
     * ```
     */
    fun parse(content: String): ParseResult {
        try {
            // 1. Check if contains Frontmatter
            if (!content.trim().startsWith("---")) {
                return ParseResult.Error("Missing frontmatter (must start with ---)")
            }

            // 2. Extract Frontmatter and Content
            val lines = content.lines()
            var frontmatterEnd = -1
            var inFrontmatter = false

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (i == 0 && line == "---") {
                    inFrontmatter = true
                    continue
                }
                if (inFrontmatter && line == "---") {
                    frontmatterEnd = i
                    break
                }
            }

            if (frontmatterEnd == -1) {
                return ParseResult.Error("Frontmatter not closed (missing closing ---)")
            }

            // 3. Parse Frontmatter YAML
            val frontmatterLines = lines.subList(1, frontmatterEnd)
            val frontmatterText = frontmatterLines.joinToString("\n")
            val frontmatterData = parseYamlFrontmatter(frontmatterText)

            // 4. Extract name and description (required fields)
            val name = frontmatterData["name"] as? String
            if (name.isNullOrBlank()) {
                return ParseResult.Error("Missing required field: name")
            }

            val description = frontmatterData["description"] as? String
            if (description.isNullOrBlank()) {
                return ParseResult.Error("Missing required field: description")
            }

            // 5. Extract metadata (optional)
            val metadataMap = frontmatterData["metadata"] as? Map<*, *>

            // 6. Extract OpenClaw metadata
            val openclawMetadata = metadataMap?.get("openclaw")?.let { openclaw ->
                parseOpenClawMetadata(openclaw)
            }

            // 7. Extract Content (all content after Frontmatter)
            val contentLines = lines.subList(frontmatterEnd + 1, lines.size)
            val contentText = contentLines.joinToString("\n").trim()

            // 8. Return parse result
            return ParseResult.Success(
                frontmatter = ParsedSkillFrontmatter(
                    name = name,
                    description = description,
                    metadata = metadataMap as? Map<String, Any?>
                ),
                content = contentText,
                openclawMetadata = openclawMetadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SKILL.md", e)
            return ParseResult.Error("Parse failed: ${e.message}")
        }
    }

    /**
     * Parse YAML Frontmatter (simplified version)
     *
     * Supports:
     * - key: value (single line)
     * - key: { "json": "object" } (single line JSON)
     *
     * Not supported:
     * - Multi-line values
     * - Arrays
     * - Complex nesting
     */
    private fun parseYamlFrontmatter(text: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        var currentKey: String? = null
        var currentValue = StringBuilder()

        for (line in text.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue
            }

            // Check if it's a new key: value line
            val colonIndex = trimmedLine.indexOf(':')
            if (colonIndex > 0) {
                // Save previous key-value
                if (currentKey != null) {
                    result[currentKey] = parseYamlValue(currentValue.toString().trim())
                }

                // Start new key-value
                currentKey = trimmedLine.substring(0, colonIndex).trim()
                currentValue = StringBuilder(trimmedLine.substring(colonIndex + 1).trim())
            } else {
                // Continue current value (multi-line, but OpenClaw spec requires single line)
                currentValue.append(" ").append(trimmedLine)
            }
        }

        // Save last key-value
        if (currentKey != null) {
            result[currentKey] = parseYamlValue(currentValue.toString().trim())
        }

        return result
    }

    /**
     * Parse YAML value
     *
     * Supports:
     * - String (default)
     * - JSON object { ... }
     * - Boolean true/false
     * - Number
     */
    private fun parseYamlValue(value: String): Any? {
        if (value.isEmpty()) {
            return null
        }

        // JSON object
        if (value.startsWith("{") && value.endsWith("}")) {
            return try {
                val jsonObject = JsonParser.parseString(value).asJsonObject
                jsonObjectToMap(jsonObject)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse JSON value: $value", e)
                value
            }
        }

        // Boolean
        if (value == "true") return true
        if (value == "false") return false

        // Number
        value.toIntOrNull()?.let { return it }
        value.toDoubleOrNull()?.let { return it }

        // String
        return value
    }

    /**
     * Convert JsonObject to Map
     */
    private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in jsonObject.keySet()) {
            val value = jsonObject.get(key)
            map[key] = when {
                value.isJsonObject -> jsonObjectToMap(value.asJsonObject)
                value.isJsonArray -> {
                    value.asJsonArray.map { element ->
                        when {
                            element.isJsonPrimitive -> element.asJsonPrimitive.let {
                                when {
                                    it.isString -> it.asString
                                    it.isNumber -> it.asNumber
                                    it.isBoolean -> it.asBoolean
                                    else -> null
                                }
                            }
                            element.isJsonObject -> jsonObjectToMap(element.asJsonObject)
                            else -> null
                        }
                    }
                }
                value.isJsonPrimitive -> value.asJsonPrimitive.let {
                    when {
                        it.isString -> it.asString
                        it.isNumber -> it.asNumber
                        it.isBoolean -> it.asBoolean
                        else -> null
                    }
                }
                value.isJsonNull -> null
                else -> null
            }
        }
        return map
    }

    /**
     * Parse OpenClaw metadata
     */
    private fun parseOpenClawMetadata(data: Any?): OpenClawSkillMetadata? {
        if (data == null) return null

        return try {
            val map = data as? Map<*, *> ?: return null

            OpenClawSkillMetadata(
                always = map["always"] as? Boolean ?: false,
                skillKey = map["skillKey"] as? String,
                primaryEnv = map["primaryEnv"] as? String,
                emoji = map["emoji"] as? String,
                homepage = map["homepage"] as? String,
                os = (map["os"] as? List<*>)?.mapNotNull { it as? String },
                requires = (map["requires"] as? Map<*, *>)?.let { req ->
                    SkillRequirements(
                        bins = (req["bins"] as? List<*>)?.mapNotNull { it as? String },
                        anyBins = (req["anyBins"] as? List<*>)?.mapNotNull { it as? String },
                        env = (req["env"] as? List<*>)?.mapNotNull { it as? String },
                        config = (req["config"] as? List<*>)?.mapNotNull { it as? String }
                    )
                },
                install = (map["install"] as? List<*>)?.mapNotNull { installSpec ->
                    parseInstallSpec(installSpec)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenClaw metadata", e)
            null
        }
    }

    /**
     * Parse install specification
     */
    private fun parseInstallSpec(data: Any?): SkillInstallSpec? {
        if (data == null) return null

        return try {
            val map = data as? Map<*, *> ?: return null

            val kindStr = map["kind"] as? String ?: return null
            val kind = when (kindStr.lowercase()) {
                "brew" -> InstallKind.BREW
                "node" -> InstallKind.NODE
                "go" -> InstallKind.GO
                "uv" -> InstallKind.UV
                "download" -> InstallKind.DOWNLOAD
                "apk" -> InstallKind.APK
                else -> return null
            }

            SkillInstallSpec(
                id = map["id"] as? String,
                kind = kind,
                label = map["label"] as? String,
                bins = (map["bins"] as? List<*>)?.mapNotNull { it as? String },
                os = (map["os"] as? List<*>)?.mapNotNull { it as? String },
                formula = map["formula"] as? String,
                `package` = map["package"] as? String,
                module = map["module"] as? String,
                url = map["url"] as? String,
                archive = map["archive"] as? String,
                extract = map["extract"] as? Boolean,
                stripComponents = (map["stripComponents"] as? Number)?.toInt(),
                targetDir = map["targetDir"] as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse install spec", e)
            null
        }
    }

    /**
     * Parse result
     */
    sealed class ParseResult {
        data class Success(
            val frontmatter: ParsedSkillFrontmatter,
            val content: String,
            val openclawMetadata: OpenClawSkillMetadata? = null
        ) : ParseResult()

        data class Error(val message: String) : ParseResult()
    }
}
