package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * R-AGENT-038 phase 1：把"对话压缩摘要 (#auto_summary) + 自动抽取碎片 (#auto_extracted) +
 * 历史编号碎片 (#auto_summary_id:NNN)"三大碎片来源结构化合并为 3 个根节点 + 冷归档。
 *
 * 写入规则（每个 bucket 共享同一套）：
 *  1. 第一次写：lazily 新建一条 Memory，content="<formatted line>"，tags={#<bucket>_root, #auto_root}
 *  2. 后续写：在 root content 顶部 prepend 新一行（newest-first），保持单条 Memory，**不**新建节点
 *  3. dedup：与 root 内最新若干行做 3-gram jaccard 0.75 比对，命中即丢弃（返回 SkippedDuplicate）
 *  4. rollover：当 lineCount > MAX_HOT_LINES_<bucket> 时，把**最旧** 20 行切出去 append 到
 *     `<filesDir>/hermes/memory_archive/<bucket>/<YYYY-MM-DD>.jsonl`，root content 截留剩余行
 *  5. IO 失败：try/catch 包住 jsonl 写入；写失败时 root content 保持 rollover 前状态，返回 Failed
 *
 * 行格式：`[<ISO-8601 ts>] (chat=<chatId>) <content>` （单行；多行内容已在调用侧合并/裁剪）
 *
 * jsonl 行格式：`{"ts": <epoch_ms>, "chat_id": "<id>", "content": "<text>", "source": "<bucket>"}`
 *
 * 不变量（phase 1）：
 *  - 不删既有 #auto_summary / #auto_extracted / #auto_summary_id 节点（迁移由 phase 2 R-AGENT-039 做）
 *  - 不改召回路径（findMemoriesByTag("#auto_summary") 等仍然返回旧节点）
 *  - 调用侧（MessageCoordinationDelegate.forcePersistSummaryToMemory / extractAndPersistFacts）
 *    切到本类即可，旧节点仍存在但不再增长
 */
class MemoryArchiver(
    private val context: Context,
    private val repository: MemoryRepository,
) {

    /**
     * 三大碎片源各自占一个 root；MAX 是单 root 在 hot 区（即根节点 content）保留的最大行数。
     * 超过 MAX 时滚到 jsonl 冷归档。
     */
    enum class ArchiveBucket(
        val rootTag: String,
        val maxHotLines: Int,
        val sourceLabel: String,
        val dirName: String,
    ) {
        SUMMARY(
            rootTag = "#auto_summary_root",
            maxHotLines = MAX_HOT_LINES_SUMMARY,
            sourceLabel = "auto_summary",
            dirName = "auto_summary",
        ),
        EXTRACTED(
            rootTag = "#auto_extracted_root",
            maxHotLines = MAX_HOT_LINES_EXTRACTED,
            sourceLabel = "auto_extracted",
            dirName = "auto_extracted",
        ),
        SUMMARY_ID(
            rootTag = "#auto_summary_id_root",
            maxHotLines = MAX_HOT_LINES_SUMMARY_ID,
            sourceLabel = "auto_summary_id",
            dirName = "auto_summary_id",
        ),
    }

    sealed class AppendResult {
        /** 第一次写：新建了 root，并写入第一行。返回 root memory id。 */
        data class Created(val rootId: Long) : AppendResult()
        /** 后续写：prepend 一行，**未**触发 rollover。返回 root memory id。 */
        data class Appended(val rootId: Long) : AppendResult()
        /** 后续写：prepend 一行后超过 maxHotLines，把 oldest [archivedLines] 行写到 jsonl。 */
        data class AppendedWithRollover(val rootId: Long, val archivedLines: Int) : AppendResult()
        /** dedup 命中（与 root 内现有行 3-gram jaccard ≥ 0.75），未写入。 */
        object SkippedDuplicate : AppendResult()
        /** rollover 时 jsonl 写入失败；root content 已回滚为 rollover 前状态，未丢内容。 */
        data class Failed(val reason: String) : AppendResult()
    }

    /**
     * 主入口：往指定 bucket 的 root 节点 prepend 一行，必要时 rollover。
     *
     * @param bucket 目的桶
     * @param chatId 来源 chat（写入行内 `(chat=<chatId>)` 段 + jsonl 的 `chat_id` 字段）
     * @param content 单条原始文本（调用侧已做 trim / take 截断）
     * @param timestamp 事件时间，毫秒 epoch（默认当前）
     */
    suspend fun appendToRoot(
        bucket: ArchiveBucket,
        chatId: String,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
    ): AppendResult {
        if (content.isBlank()) {
            return AppendResult.SkippedDuplicate
        }
        val newLine = formatLine(timestamp, chatId, content)
        val root = ensureRoot(bucket)

        val existingLines = if (root.content.isBlank()) emptyList()
            else root.content.split('\n').filter { it.isNotEmpty() }

        // dedup：3-gram jaccard 0.75 比对最近 maxHotLines 行（实际上就是 hot 区全部）。
        val newNgrams = ngrams(content)
        val isDuplicate = existingLines.any { line ->
            val existingContent = stripLineMetadata(line)
            val sim = jaccard(newNgrams, ngrams(existingContent))
            sim >= 0.75f
        }
        if (isDuplicate) {
            return AppendResult.SkippedDuplicate
        }

        // newest-first：新行放最前
        val updatedLines = ArrayList<String>(existingLines.size + 1)
        updatedLines.add(newLine)
        updatedLines.addAll(existingLines)

        val isFirstWrite = root.content.isBlank()

        return if (updatedLines.size > bucket.maxHotLines) {
            // rollover：把末尾 ROLLOVER_SLICE_SIZE 行切到 jsonl，root 留最新 (size - slice) 行
            val slice = updatedLines.takeLast(ROLLOVER_SLICE_SIZE)
            val keepLines = updatedLines.dropLast(ROLLOVER_SLICE_SIZE)
            try {
                appendSliceToArchive(bucket, slice)
            } catch (t: Throwable) {
                AppLogger.w(
                    TAG,
                    "R-AGENT-038: archive io failed for bucket=${bucket.name}: ${t.message}",
                )
                // root content 不动；新行也不写入（保持调用前一致状态）
                return AppendResult.Failed(t.message ?: "io failure")
            }
            persistRootContent(root, keepLines)
            AppendResult.AppendedWithRollover(root.id, slice.size)
        } else {
            persistRootContent(root, updatedLines)
            if (isFirstWrite) AppendResult.Created(root.id) else AppendResult.Appended(root.id)
        }
    }

    /**
     * 找到（或新建）指定 bucket 的 root memory。Lazily 第一次调用时新建：
     *  - title = bucket.rootTag 去掉 `#` 前缀
     *  - content = "" （等 appendToRoot 写入第一行）
     *  - source = bucket.sourceLabel
     *  - tags = {bucket.rootTag, "#auto_root"}
     */
    suspend fun ensureRoot(bucket: ArchiveBucket): Memory {
        val existing = repository.findMemoriesByTag(bucket.rootTag).firstOrNull()
        if (existing != null) return existing
        val mem = Memory(
            title = bucket.rootTag.removePrefix("#"),
            content = "",
            contentType = "text/plain",
            source = bucket.sourceLabel,
            credibility = 0.85f,
            importance = 0.6f,
            folderPath = null,
        )
        repository.saveMemory(mem)
        repository.addTagToMemory(mem, bucket.rootTag)
        repository.addTagToMemory(mem, "#auto_root")
        return mem
    }

    private suspend fun persistRootContent(root: Memory, lines: List<String>) {
        val newContent = lines.joinToString("\n")
        repository.updateMemory(
            memory = root,
            newTitle = root.title,
            newContent = newContent,
        )
    }

    private fun appendSliceToArchive(bucket: ArchiveBucket, lines: List<String>) {
        val dir = archiveDir(bucket)
        if (!dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                throw java.io.IOException("cannot create archive dir ${dir.absolutePath}")
            }
        }
        val today = DATE_FMT.get()!!.format(Date())
        val file = File(dir, "$today.jsonl")
        // append-only 写入，每行一条 jsonl
        val builder = StringBuilder()
        for (line in lines) {
            val parsed = parseLineToJson(line, bucket)
            builder.append(parsed.toString()).append('\n')
        }
        file.appendText(builder.toString())
    }

    /**
     * 暴露给测试 / phase 2 召回路径用：拿到归档目录。
     * `<filesDir>/hermes/memory_archive/<bucket-dir-name>/`
     */
    fun archiveDir(bucket: ArchiveBucket): File {
        return File(context.filesDir, "hermes/memory_archive/${bucket.dirName}")
    }

    /**
     * R-AGENT-041-c: 读冷归档 jsonl。
     *
     * 列出 `archiveDir(bucket)` 下所有 `*.jsonl` 文件，按文件名 desc（yyyy-MM-DD 字典序 = 时间序，
     * 最近日期在前），逐个 readText 并 parseArchiveJsonl 解析，所有 entries 拼接返回。
     * 单文件 IO 失败吞掉走 try / catch，UI 平滑降级（不能拖垮详情页）。
     */
    fun loadColdArchive(bucket: ArchiveBucket): List<ArchiveEntry> {
        val dir = archiveDir(bucket)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = (dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") } ?: emptyArray())
            .toList()
            .sortedByDescending { it.name }
        val out = ArrayList<ArchiveEntry>()
        for (file in files) {
            try {
                val text = file.readText()
                out.addAll(parseArchiveJsonl(text))
            } catch (t: Throwable) {
                AppLogger.w(
                    TAG,
                    "R-AGENT-041-c: load cold archive failed for ${file.absolutePath}: ${t.message}",
                )
            }
        }
        return out
    }

    /**
     * R-AGENT-041-c: 反查 root memory 对应的 bucket。
     *
     * root 节点身上有两条 tag：bucket 专属 root tag（`#auto_summary_root` 等）+ 通用 `#auto_root`
     * 标识 tag。本函数检查 memory.tags 里命中哪个 bucket 的 rootTag，命中即返回；非 root 节点返回 null。
     */
    fun bucketForRootMemory(memory: Memory): ArchiveBucket? {
        val tagNames = memory.tags.map { it.name }.toSet()
        if (!tagNames.contains("#auto_root")) return null
        for (bucket in ArchiveBucket.values()) {
            if (tagNames.contains(bucket.rootTag)) return bucket
        }
        return null
    }

    /**
     * R-AGENT-041-c: 冷归档行实体。schema 来源 R-AGENT-038 锁定的 jsonl 字段：
     *  - `ts` epoch ms
     *  - `chat_id` 来源会话 id
     *  - `content` 单行原文
     *  - `source` bucket sourceLabel（auto_summary / auto_extracted / auto_summary_id）
     */
    data class ArchiveEntry(
        val ts: Long,
        val chatId: String,
        val content: String,
        val source: String,
    )

    private fun parseLineToJson(line: String, bucket: ArchiveBucket): JSONObject {
        // 解析回 ts / chat / content；如果格式异常则把整行当 content，用当前时间。
        val matcher = LINE_PATTERN.matchEntire(line)
        return if (matcher != null) {
            val isoTs = matcher.groupValues[1]
            val chat = matcher.groupValues[2]
            val body = matcher.groupValues[3]
            JSONObject().apply {
                put("ts", parseIsoToEpoch(isoTs))
                put("chat_id", chat)
                put("content", body)
                put("source", bucket.sourceLabel)
            }
        } else {
            JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("chat_id", "")
                put("content", line)
                put("source", bucket.sourceLabel)
            }
        }
    }

    private fun formatLine(timestampMs: Long, chatId: String, content: String): String {
        val iso = ISO_FMT.get()!!.format(Date(timestampMs))
        // 单行化：把内容里的换行替换为空格，避免 root content 行结构被破坏
        val singleLine = content.replace('\n', ' ').replace('\r', ' ').trim()
        return "[$iso] (chat=$chatId) $singleLine"
    }

    private fun stripLineMetadata(line: String): String {
        val matcher = LINE_PATTERN.matchEntire(line) ?: return line
        return matcher.groupValues[3]
    }

    private fun parseIsoToEpoch(iso: String): Long = try {
        ISO_FMT.get()!!.parse(iso)?.time ?: System.currentTimeMillis()
    } catch (t: Throwable) {
        System.currentTimeMillis()
    }

    /** 3-gram 集合，与 MessageCoordinationDelegate.computeAutoSummaryNgrams 等价。 */
    private fun ngrams(text: String, n: Int = 3): Set<String> {
        if (text.length < n) return emptySet()
        val normalized = text.replace(Regex("\\s+"), " ").trim().lowercase()
        if (normalized.length < n) return emptySet()
        val out = HashSet<String>(normalized.length)
        for (i in 0..normalized.length - n) {
            out.add(normalized.substring(i, i + n))
        }
        return out
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val intersect = a.count { it in b }
        val union = a.size + b.size - intersect
        if (union == 0) return 0f
        return intersect.toFloat() / union.toFloat()
    }

    companion object {
        private const val TAG = "MemoryArchiver"

        const val MAX_HOT_LINES_SUMMARY = 200
        const val MAX_HOT_LINES_EXTRACTED = 100
        const val MAX_HOT_LINES_SUMMARY_ID = 50

        /** 每次 rollover 切出去的行数（最旧的 N 行）。 */
        const val ROLLOVER_SLICE_SIZE = 20

        // [<iso ts>] (chat=<chatId>) <content>
        private val LINE_PATTERN = Regex("""^\[([^\]]+)\] \(chat=([^)]*)\) (.*)$""")

        private val ISO_FMT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }

        private val DATE_FMT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }
    }
}

/**
 * R-AGENT-041-c: 解析 jsonl 文本（每行一条 JSON）为 `List<ArchiveEntry>`。
 *
 * 容忍坏行：
 *  - 空白 / 空行：跳过
 *  - 非 JSON / JSON 解析失败：跳过
 *  - 缺关键字段（`ts` / `chat_id` / `content` / `source` 任一缺失）：跳过
 *
 * 顶层纯函数，无 Android 依赖，可直接单测覆盖（`MemoryArchiverColdArchiveParseTest`）。
 */
fun parseArchiveJsonl(text: String): List<MemoryArchiver.ArchiveEntry> {
    if (text.isBlank()) return emptyList()
    val out = ArrayList<MemoryArchiver.ArchiveEntry>()
    for (rawLine in text.split('\n')) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        try {
            val obj = JSONObject(line)
            if (!obj.has("ts") || !obj.has("chat_id") ||
                !obj.has("content") || !obj.has("source")
            ) {
                continue
            }
            out.add(
                MemoryArchiver.ArchiveEntry(
                    ts = obj.getLong("ts"),
                    chatId = obj.getString("chat_id"),
                    content = obj.getString("content"),
                    source = obj.getString("source"),
                )
            )
        } catch (t: Throwable) {
            // 坏行：跳过
        }
    }
    return out
}
