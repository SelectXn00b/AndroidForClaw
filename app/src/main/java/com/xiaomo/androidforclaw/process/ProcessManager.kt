package com.xiaomo.androidforclaw.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * OpenClaw module: process
 * Source: OpenClaw/src/process/exec.ts + kill-tree.ts
 *
 * Adapted for Android: ProcessBuilder-based execution with:
 * - stdin piping
 * - no-output timeout
 * - graceful kill escalation (SIGTERM -> grace period -> SIGKILL)
 * - termination type tracking
 */
object ProcessManager {

    private const val DEFAULT_GRACE_MS = 3000L
    private const val MAX_GRACE_MS = 60_000L

    /**
     * Execute a shell command. Aligned with TS runCommandWithTimeout().
     */
    suspend fun exec(command: String, options: ExecOptions = ExecOptions()): ExecResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val pb = ProcessBuilder("sh", "-c", command)
            options.workingDir?.let { pb.directory(File(it)) }
            pb.environment().putAll(options.env)
            pb.redirectErrorStream(false)

            val process = pb.start()

            // Pipe stdin if provided
            if (options.stdinInput != null) {
                try {
                    process.outputStream.bufferedWriter().use { writer ->
                        writer.write(options.stdinInput)
                        writer.flush()
                    }
                } catch (_: Exception) {
                    // Process may have already exited
                }
            } else {
                try { process.outputStream.close() } catch (_: Exception) {}
            }

            // Read stdout/stderr concurrently to avoid deadlock
            val stdoutFuture = Thread { }
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        val buf = CharArray(4096)
                        var len: Int
                        while (reader.read(buf).also { len = it } != -1) {
                            synchronized(stdoutBuilder) { stdoutBuilder.append(buf, 0, len) }
                        }
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            val stderrThread = Thread {
                try {
                    if (options.captureStderr) {
                        process.errorStream.bufferedReader().use { reader ->
                            val buf = CharArray(4096)
                            var len: Int
                            while (reader.read(buf).also { len = it } != -1) {
                                synchronized(stderrBuilder) { stderrBuilder.append(buf, 0, len) }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            val finished = process.waitFor(options.timeoutMs, TimeUnit.MILLISECONDS)
            val durationMs = System.currentTimeMillis() - startTime

            if (!finished) {
                // Graceful kill: SIGTERM then SIGKILL after grace period
                killProcessTree(getProcessPid(process))
                process.waitFor(DEFAULT_GRACE_MS, TimeUnit.MILLISECONDS)
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                stdoutThread.join(1000)
                stderrThread.join(1000)
                ExecResult(
                    exitCode = -1,
                    stdout = stdoutBuilder.toString(),
                    stderr = stderrBuilder.toString(),
                    timedOut = true,
                    durationMs = durationMs,
                    terminationType = TerminationType.TIMEOUT
                )
            } else {
                stdoutThread.join(1000)
                stderrThread.join(1000)
                ExecResult(
                    exitCode = process.exitValue(),
                    stdout = stdoutBuilder.toString(),
                    stderr = stderrBuilder.toString(),
                    timedOut = false,
                    durationMs = durationMs,
                    terminationType = TerminationType.EXIT
                )
            }
        }

    /** Execute and return stdout, throwing on failure. */
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

    // --- kill-tree.ts aligned ---

    /**
     * Kill a process tree with graceful shutdown.
     * Aligned with TS killProcessTree(): SIGTERM to process group,
     * wait grace period, then SIGKILL.
     */
    fun killProcessTree(pid: Int, graceMs: Long = DEFAULT_GRACE_MS) {
        if (pid <= 0) return
        val grace = normalizeGraceMs(graceMs)

        // Step 1: SIGTERM to process group
        try {
            Runtime.getRuntime().exec(arrayOf("kill", "-TERM", "-$pid"))
        } catch (_: Exception) {
            // Fallback: kill just the process
            try {
                Runtime.getRuntime().exec(arrayOf("kill", "-TERM", "$pid"))
            } catch (_: Exception) {
                return // Already gone
            }
        }

        // Step 2: Schedule SIGKILL after grace period (in a daemon thread)
        Thread {
            try {
                Thread.sleep(grace)
            } catch (_: InterruptedException) {
                return@Thread
            }
            // Try process group SIGKILL first
            if (isProcessAlive(pid)) {
                try {
                    Runtime.getRuntime().exec(arrayOf("kill", "-9", "-$pid"))
                } catch (_: Exception) {
                    // Fall through to direct kill
                }
            }
            if (isProcessAlive(pid)) {
                try {
                    Runtime.getRuntime().exec(arrayOf("kill", "-9", "$pid"))
                } catch (_: Exception) {
                    // Process exited between check and kill
                }
            }
        }.apply { isDaemon = true; start() }
    }

    /** Check if a process is alive via /proc filesystem. */
    fun isProcessAlive(pid: Int): Boolean {
        return try {
            File("/proc/$pid").exists()
        } catch (_: Exception) {
            false
        }
    }

    /** Extract PID from a Process, with reflection fallback for older Android. */
    private fun getProcessPid(process: Process): Int {
        // Try Java 9+ ProcessHandle.pid() first
        return try {
            val pidMethod = process.javaClass.getMethod("pid")
            (pidMethod.invoke(process) as Long).toInt()
        } catch (_: Exception) {
            // Fallback: access private 'pid' field (Android's UNIXProcess)
            try {
                val pidField = process.javaClass.getDeclaredField("pid")
                pidField.isAccessible = true
                pidField.getInt(process)
            } catch (_: Exception) {
                -1
            }
        }
    }

    private fun normalizeGraceMs(value: Long): Long {
        return max(0, min(MAX_GRACE_MS, value))
    }
}
