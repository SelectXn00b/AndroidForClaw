package com.xiaomo.androidforclaw.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenClaw module: process
 * Source: OpenClaw/src/process/manager.ts
 *
 * Adapted for Android: wraps shell command execution via
 * Runtime.exec / ProcessBuilder rather than Node child_process.
 */
object ProcessManager {

    suspend fun exec(command: String, options: ExecOptions = ExecOptions()): ExecResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val pb = ProcessBuilder("sh", "-c", command)
            options.workingDir?.let { pb.directory(File(it)) }
            pb.environment().putAll(options.env)
            if (options.captureStderr) {
                pb.redirectErrorStream(false)
            }
            val process = pb.start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = if (options.captureStderr) {
                process.errorStream.bufferedReader().readText()
            } else ""

            val finished = process.waitFor(options.timeoutMs, TimeUnit.MILLISECONDS)
            val durationMs = System.currentTimeMillis() - startTime

            if (!finished) {
                process.destroyForcibly()
                ExecResult(
                    exitCode = -1,
                    stdout = stdout,
                    stderr = stderr,
                    timedOut = true,
                    durationMs = durationMs
                )
            } else {
                ExecResult(
                    exitCode = process.exitValue(),
                    stdout = stdout,
                    stderr = stderr,
                    timedOut = false,
                    durationMs = durationMs
                )
            }
        }

    suspend fun execCapture(command: String, options: ExecOptions = ExecOptions()): String {
        val result = exec(command, options)
        if (result.timedOut) {
            throw RuntimeException("Command timed out: $command")
        }
        if (result.exitCode != 0) {
            throw RuntimeException(
                "Command failed (exit ${result.exitCode}): $command\nstderr: ${result.stderr}"
            )
        }
        return result.stdout
    }

    fun killTree(pid: Int) {
        try {
            // On Android/Linux, kill the process group
            Runtime.getRuntime().exec(arrayOf("kill", "-TERM", "-$pid"))
        } catch (_: Exception) {
            // Fallback: kill just the single process
            try {
                Runtime.getRuntime().exec(arrayOf("kill", "-TERM", "$pid"))
            } catch (_: Exception) {
                // Best-effort; process may already be gone
            }
        }
    }

    fun isProcessAlive(pid: Int): Boolean {
        return try {
            File("/proc/$pid").exists()
        } catch (_: Exception) {
            false
        }
    }
}
