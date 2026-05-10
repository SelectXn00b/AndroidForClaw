package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.application.OperitApplication
import com.xiaomo.hermes.hermes.getHermesHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FeedbackLogCollector {

    data class CollectedLogs(
        val appLogs: String,
        val appLogLineCount: Int,
        val hermesErrorLogs: String,
        val hermesErrorLogLineCount: Int,
        val hermesAgentLogs: String,
        val hermesAgentLogLineCount: Int,
        val packageLogs: String,
        val hasPackageLogs: Boolean,
        val anrReport: String,
        val hasAnrReport: Boolean
    )

    suspend fun collectAll(
        appLogLines: Int = 200,
        hermesErrorLogLines: Int = 100,
        hermesAgentLogLines: Int = 100,
        packageLogLines: Int = 100,
        anrReportLines: Int = 200
    ): CollectedLogs = withContext(Dispatchers.IO) {
        // 1. App log (operit.log)
        val appLogFile = AppLogger.getLogFile()
        val (appLogs, appCount) = readTailOfFile(appLogFile, appLogLines)

        // 2. Hermes errors.log
        val hermesHome = try { getHermesHome() } catch (_: Exception) { null }
        val errorsFile = hermesHome?.let { File(it, "logs/errors.log") }
        val (hermesErrors, hermesErrorCount) = readTailOfFile(errorsFile, hermesErrorLogLines)

        // 3. Hermes agent.log
        val agentFile = hermesHome?.let { File(it, "logs/agent.log") }
        val (agentLogs, agentCount) = readTailOfFile(agentFile, hermesAgentLogLines)

        // 4. Package logs (latest file)
        val pkgLogDir = File(OperitPaths.operitRootDir(), "packageLogs")
        val latestPkgLog = try {
            pkgLogDir.listFiles()
                ?.filter { it.extension == "log" }
                ?.maxByOrNull { it.lastModified() }
        } catch (_: Exception) { null }
        val (pkgLogs, _) = readTailOfFile(latestPkgLog, packageLogLines)

        // 5. Latest ANR report (saved by AnrMonitor on stop)
        val anrDir = try {
            OperitApplication.instance.getExternalFilesDir("anr_reports")
        } catch (_: Throwable) { null }
        val latestAnrReport = try {
            anrDir?.listFiles()
                ?.filter { it.extension == "txt" && it.name.startsWith("anr_report_") }
                ?.maxByOrNull { it.lastModified() }
        } catch (_: Exception) { null }
        val (anrReport, anrCount) = readTailOfFile(latestAnrReport, anrReportLines)

        CollectedLogs(
            appLogs = appLogs,
            appLogLineCount = appCount,
            hermesErrorLogs = hermesErrors,
            hermesErrorLogLineCount = hermesErrorCount,
            hermesAgentLogs = agentLogs,
            hermesAgentLogLineCount = agentCount,
            packageLogs = pkgLogs,
            hasPackageLogs = pkgLogs.isNotBlank(),
            anrReport = anrReport,
            hasAnrReport = anrCount > 0
        )
    }

    private fun readTailOfFile(file: File?, lines: Int): Pair<String, Int> {
        if (file == null || !file.exists()) return "" to 0
        return try {
            val allLines = file.readLines()
            val tail = allLines.takeLast(lines)
            tail.joinToString("\n") to tail.size
        } catch (e: Exception) {
            "Failed to read ${file.name}: ${e.message}" to 0
        }
    }
}
