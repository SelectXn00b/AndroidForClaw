/**
 * OpenClaw Source Reference:
 * - src/agents/tools/memory-tool.ts
 * - src/agents/memory-search.ts
 */
package com.xiaomo.hermes.agent.tools.memory

import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.agent.memory.MemoryIndex
import com.xiaomo.hermes.agent.memory.MemoryManager
import com.xiaomo.hermes.agent.tools.Skill
import com.xiaomo.hermes.agent.tools.SkillResult
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition
import java.io.File

/**
 * memory_search tool — aligned with OpenClaw memory-tool.ts
 *
 * Hybrid search: SQLite FTS5 + vector embedding cosine similarity.
 * Falls back to FTS5-only when no embedding provider is configured.
 */
class MemorySearchSkill(
    private val memoryManager: MemoryManager,
    private val workspacePath: String
) : Skill {
    companion object {
        private const val TAG = "MemorySearchSkill"
        private const val SNIPPET_MAX_CHARS = 700
    }

    override val name = "memory_search"
    override val description = "Search through memory files for relevant information using hybrid vector + keyword search. Returns matching text snippets with context."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to PropertySchema(
                            type = "string",
                            description = "Search query (keywords or phrases)"
                        ),
                        "maxResults" to PropertySchema(
                            type = "number",
                            description = "Maximum number of results to return (default: 6)"
                        ),
                        "minScore" to PropertySchema(
                            type = "number",
                            description = "Minimum match score threshold (default: 0.35)"
                        )
                    ),
                    required = listOf("query")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val query = args["query"] as? String
            ?: return SkillResult.error("Missing required parameter: query")

        val maxResults = (args["maxResults"] as? Number)?.toInt() ?: MemoryIndex.DEFAULT_MAX_RESULTS
        val minScore = (args["minScore"] as? Number)?.toFloat() ?: MemoryIndex.DEFAULT_MIN_SCORE

        return try {
            val memoryIndex = memoryManager.getMemoryIndex()
            if (memoryIndex == null) {
                return SkillResult.error("Memory index not initialized")
            }

            // Ensure index is up to date
            memoryManager.syncIndex()

            val results = memoryIndex.hybridSearch(query, maxResults, minScore)

            if (results.isEmpty()) {
                return SkillResult.success(
                    content = "No matching memories found for query: \"$query\"",
                    metadata = mapOf(
                        "query" to query,
                        "results_count" to 0,
                        "mode" to getSearchMode()
                    )
                )
            }

            // Format results — aligned with OpenClaw output
            val workspaceDir = File(workspacePath)
            val formatted = results.mapIndexed { index, result ->
                val relativePath = try {
                    File(result.path).relativeTo(workspaceDir).path
                } catch (_: Exception) { result.path }
                val snippet = if (result.text.length > SNIPPET_MAX_CHARS) {
                    result.text.take(SNIPPET_MAX_CHARS) + "..."
                } else result.text

                """## Result ${index + 1} ($relativePath, lines ${result.startLine}-${result.endLine}, score: ${"%.2f".format(result.score)})
$snippet"""
            }.joinToString("\n\n")

            val embeddingProvider = memoryManager.getEmbeddingProvider()
            SkillResult.success(
                content = formatted,
                metadata = mapOf(
                    "query" to query,
                    "results_count" to results.size,
                    "mode" to getSearchMode(),
                    "provider" to (embeddingProvider?.providerName ?: "none"),
                    "model" to (embeddingProvider?.modelName ?: "fts5-only"),
                    "citations" to results.map { r ->
                        val rp = try { File(r.path).relativeTo(File(workspacePath)).path } catch (_: Exception) { r.path }
                        mapOf("file" to rp, "startLine" to r.startLine, "endLine" to r.endLine)
                    }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Memory search failed", e)
            SkillResult.error("Failed to search memory: ${e.message}")
        }
    }

    private fun getSearchMode(): String {
        val ep = memoryManager.getEmbeddingProvider()
        return if (ep?.isAvailable == true) "hybrid" else "keyword"
    }
}
