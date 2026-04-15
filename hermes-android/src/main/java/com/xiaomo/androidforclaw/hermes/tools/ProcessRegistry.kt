package com.xiaomo.androidforclaw.hermes.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * In-process managed process registry for tracking long-running child processes.
 * Ported from process_registry.py
 */
object ProcessRegistry {

    data class ProcessInfo(
        val id: String,
        val command: String,
        val process: Process,
        val logFile: String? = null,
    )

    private val _processes = ConcurrentHashMap<String, ProcessInfo>()

    fun register(id: String, command: String, process: Process, logFile: String? = null): ProcessInfo {
        val info = ProcessInfo(id, command, process, logFile)
        _processes[id] = info
        return info
    }

    fun get(id: String): ProcessInfo? = _processes[id]

    fun unregister(id: String): ProcessInfo? = _processes.remove(id)

    fun getAll(): Map<String, ProcessInfo> = _processes.toMap()

    fun getActive(): List<ProcessInfo> = _processes.values.filter { it.process.isAlive }

    fun killAll() {
        for ((id, info) in _processes) {
            if (info.process.isAlive) {
                try {
                    info.process.destroy()
                    info.process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    if (info.process.isAlive) info.process.destroyForcibly()
                } catch (_unused: Exception) {}
            }
        }
        _processes.clear()
    }

    fun cleanup() {
        val dead = _processes.filter { !it.value.process.isAlive }.map { it.key }
        dead.forEach { _processes.remove(it) }
    }



    /** Strip shell startup warnings from the beginning of output. */
    fun _cleanShellNoise(text: String): String {
        return ""
    }
    /** Scan new output for watch patterns and queue notifications. */
    fun _checkWatchPatterns(session: Any?, newText: String): Unit {
        // TODO: implement _checkWatchPatterns
    }
    /** Best-effort liveness check for host-visible PIDs. */
    fun _isHostPidAlive(pid: Int?): Boolean {
        return false
    }
    /** Update recovered host-PID sessions when the underlying process has exited. */
    fun _refreshDetachedSession(session: Any??): Any?? {
        return null
    }
    /** Terminate a host-visible PID without requiring the original process handle. */
    fun _terminateHostPid(pid: Int): Unit {
        // TODO: implement _terminateHostPid
    }
    /** Return the writable sandbox temp dir for env-backed background tasks. */
    fun _envTempDir(env: Any): String {
        return ""
    }
    /** Spawn a background process locally. */
    fun spawnLocal(command: String, cwd: String? = null, taskId: String = "", sessionKey: String = "", envVars: Any? = null, usePty: Boolean = false): Any? {
        return null
    }
    /** Spawn a background process through a non-local environment backend. */
    fun spawnViaEnv(env: Any, command: String, cwd: String? = null, taskId: String = "", sessionKey: String = "", timeout: Int = 10): Any? {
        return null
    }
    /** Background thread: read stdout from a local Popen process. */
    fun _readerLoop(session: Any?): Any? {
        return null
    }
    /** Background thread: poll a sandbox log file for non-local backends. */
    fun _envPollerLoop(session: Any?, env: Any, logPath: String, pidPath: String, exitPath: String): Any? {
        return null
    }
    /** Background thread: read output from a PTY process. */
    fun _ptyReaderLoop(session: Any?): Any? {
        return null
    }
    /** Move a session from running to finished. */
    fun _moveToFinished(session: Any?): Any? {
        return null
    }
    /** Check if a completion notification was already consumed via wait/poll/log. */
    fun isCompletionConsumed(sessionId: String): Boolean {
        return false
    }
    /** Check status and get new output for a background process. */
    fun poll(sessionId: String): Any? {
        return null
    }
    /** Read the full output log with optional pagination by lines. */
    fun readLog(sessionId: String, offset: Int = 0, limit: Int = 200): Any? {
        return null
    }
    /** Block until a process exits, timeout, or interrupt. */
    fun wait(sessionId: String, timeout: Int? = null): Any? {
        return null
    }
    /** Kill a background process. */
    fun killProcess(sessionId: String): Any? {
        return null
    }
    /** Send raw data to a running process's stdin (no newline appended). */
    fun writeStdin(sessionId: String, data: String): Any? {
        return null
    }
    /** Send data + newline to a running process's stdin (like pressing Enter). */
    fun submitStdin(sessionId: String, data: String = ""): Any? {
        return null
    }
    /** Close a running process's stdin / send EOF without killing the process. */
    fun closeStdin(sessionId: String): Any? {
        return null
    }
    /** List all running and recently-finished processes. */
    fun listSessions(taskId: String? = null): Any? {
        return null
    }
    /** Check if there are active (running) processes for a task_id. */
    fun hasActiveProcesses(taskId: String): Boolean {
        return false
    }
    /** Check if there are active processes for a gateway session key. */
    fun hasActiveForSession(sessionKey: String): Boolean {
        return false
    }
    /** Remove oldest finished sessions if over MAX_PROCESSES. Must hold _lock. */
    fun _pruneIfNeeded(): Any? {
        return null
    }
    /** Write running process metadata to checkpoint file atomically. */
    fun _writeCheckpoint(): Any? {
        return null
    }
    /** On gateway startup, probe PIDs from checkpoint file. */
    fun recoverFromCheckpoint(): Int {
        return 0
    }

}
