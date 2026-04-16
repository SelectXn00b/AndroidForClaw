package com.xiaomo.hermes.process

/**
 * OpenClaw module: process
 * Source: OpenClaw/src/process/exec.ts (SpawnResult type) + types
 *
 * Adapted for Android: ProcessBuilder-based execution.
 */

data class ExecOptions(
    val timeoutMs: Long = 30_000,
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
    val captureStderr: Boolean = true,
    /** If set, kill the process if it produces no output for this many ms. */
    val noOutputTimeoutMs: Long = 0,
    /** Optional stdin input to pipe to the process. */
    val stdinInput: String? = null
)

/** How the process terminated. Aligned with TS SpawnResult terminationType. */
enum class TerminationType {
    EXIT,              // normal exit
    TIMEOUT,           // wallclock timeout exceeded
    NO_OUTPUT_TIMEOUT, // no-output timeout exceeded
    SIGNAL             // killed by signal (SIGTERM/SIGKILL)
}

data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0,
    val terminationType: TerminationType = if (timedOut) TerminationType.TIMEOUT else TerminationType.EXIT
)
