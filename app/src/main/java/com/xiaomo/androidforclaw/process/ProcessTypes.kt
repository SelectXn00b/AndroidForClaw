package com.xiaomo.androidforclaw.process

/**
 * OpenClaw module: process
 * Source: OpenClaw/src/process/types.ts
 *
 * Adapted for Android: no OS-level child-process spawning;
 * models exec as coroutine-based command execution.
 */

data class ExecOptions(
    val timeoutMs: Long = 30_000,
    val workingDir: String? = null,
    val env: Map<String, String> = emptyMap(),
    val captureStderr: Boolean = true
)

data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0
)
