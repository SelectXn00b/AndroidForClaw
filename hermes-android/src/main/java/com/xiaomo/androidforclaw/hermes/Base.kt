package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class MessageType {
    // Hermes: MessageType
}

class ProcessingOutcome {
    // Hermes: ProcessingOutcome
}

class MessageEvent {
    // Hermes: MessageEvent
    fun isCommand(): Boolean {
        return false
    // Hermes: is_command
}

    /** Short, human-readable name shown in logs and diagnostics. */
    fun providerName(): String {
        return ""
    }
    /** Return True when all required env vars / credentials are present. */
    fun isConfigured(): Boolean {
        return false
    }
    /** Create a cloud browser session and return session metadata. */
    fun createSession(taskId: String): Map<String, Any?> {
        return emptyMap()
    }
    /** Release / terminate a cloud session by its provider session ID. */
    fun closeSession(sessionId: String): Boolean {
        return false
    }
    /** Best-effort session teardown during process exit. */
    fun emergencyCleanup(sessionId: String): Unit {
        // TODO: implement emergencyCleanup
    }
    fun poll(): Any? {
        return null
    }
    fun kill(): Unit {
        // TODO: implement kill
    }
    fun wait(timeout: Any? = null): Int {
        return 0
    }
    fun stdout(): Any? {
        return null
    }
    fun returncode(): Any? {
        return null
    }
    /** Return the backend temp directory used for session artifacts. */
    fun getTempDir(): String {
        return ""
    }
    /** Spawn a bash process to run *cmd_string*. */
    fun _runBash(cmdString: String): Any? {
        throw NotImplementedError("_runBash")
    }
    /** Release backend resources (container, instance, connection). */
    fun cleanup(): Any? {
        return null
    }
    /** Capture login shell environment into a snapshot file. */
    fun initSession(): Any? {
        return null
    }
    /** Build the full bash script that sources snapshot, cd's, runs command, */
    fun _wrapCommand(command: String, cwd: String): String {
        return ""
    }
    /** Append stdin_data as a shell heredoc to the command string. */
    fun _embedStdinHeredoc(command: String, stdinData: String): String {
        return ""
    }
    /** Poll-based wait with interrupt checking and stdout draining. */
    fun _waitForProcess(proc: Any?, timeout: Int = 120): Any? {
        return null
    }
    /** Terminate a process. Subclasses may override for process-group kill. */
    fun _killProcess(proc: Any?): Any? {
        return null
    }
    /** Extract CWD from command output. Override for local file-based read. */
    fun _updateCwd(result: Any?): Any? {
        return null
    }
    /** Parse the __HERMES_CWD_{session}__ marker from stdout output. */
    fun _extractCwdFromOutput(result: Any?): Any? {
        return null
    }
    /** Hook called before each command execution. */
    fun _beforeExecute(): Unit {
        // TODO: implement _beforeExecute
    }
    /** Execute a command, return {"output": str, "returncode": int}. */
    fun execute(command: String, cwd: String = ""): Any? {
        return null
    }
    /** Alias for cleanup (compat with older callers). */
    fun stop(): Any? {
        return null
    }
    /** Transform sudo commands if SUDO_PASSWORD is available. */
    fun _prepareCommand(command: String): Pair<String, Any?> {
        throw NotImplementedError("_prepareCommand")
    }

}
