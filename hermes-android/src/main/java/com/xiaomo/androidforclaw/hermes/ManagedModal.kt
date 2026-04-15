package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class _ManagedModalExecHandle {
    // Hermes: _ManagedModalExecHandle
}

class ManagedModalEnvironment(
    val image: String,
    val cwd: String,
    val timeout: Long,
    val modal_sandbox_kwargs: List<String>,
    val persistent_filesystem: String,
    val task_id: String
) {
    private fun startModalExec(prepared: String): Unit {
    // Hermes: _start_modal_exec
        // Hermes: startModalExec
    }
    private fun pollModalExec(handle: String): Unit {
    // Hermes: _poll_modal_exec
        // Hermes: pollModalExec
    }
    private fun cancelModalExec(handle: String): Unit {
    // Hermes: _cancel_modal_exec
        // Hermes: cancelModalExec
    }
    private fun timeoutResultForModal(timeout: Long): Unit {
    // Hermes: _timeout_result_for_modal
        // Hermes: timeoutResultForModal
    }
    fun cleanup(): Unit {
    // Hermes: cleanup
        // Hermes: cleanup
    }
    private fun createSandbox(): Any? {
    // Hermes: _create_sandbox
        return null
        // Hermes: createSandbox
        return null
    }
    private fun guardUnsupportedCredentialPassthrough(): Unit {
    // Hermes: _guard_unsupported_credential_passthrough
        // Hermes: guardUnsupportedCredentialPassthrough
    }
    private fun request(method: String, path: String): Unit {
    // Hermes: _request
        // Hermes: request
    }
    private fun cancelExec(exec_id: String): Unit {
    // Hermes: _cancel_exec
        // Hermes: cancelExec
    }
    private fun coerceNumber(value: String, default: String): Unit {
    // Hermes: _coerce_number
        // Hermes: coerceNumber
    }
    private fun formatError(prefix: String, response: String): Unit {
    // Hermes: _format_error
        // Hermes: formatError
    }

    fun _startModalExec(prepared: PreparedModalExec): ModalExecStart {
        throw NotImplementedError("_startModalExec")
    }
    fun _pollModalExec(handle: Any?): Any? {
        return null
    }
    fun _cancelModalExec(handle: Any?): Unit {
        // TODO: implement _cancelModalExec
    }
    fun _timeoutResultForModal(timeout: Int): Any? {
        return null
    }
    fun _createSandbox(): String {
        return ""
    }
    /** Managed Modal does not sync or mount host credential files. */
    fun _guardUnsupportedCredentialPassthrough(): Unit {
        // TODO: implement _guardUnsupportedCredentialPassthrough
    }
    fun _request(method: String, path: String): Any? {
        throw NotImplementedError("_request")
    }
    fun _cancelExec(execId: String): Unit {
        // TODO: implement _cancelExec
    }
    fun _coerceNumber(value: Any, default: Double): Double {
        return 0.0
    }
    fun _formatError(prefix: String, response: Any?): String {
        return ""
    }

}
