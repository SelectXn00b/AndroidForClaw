package com.xiaomo.hermes.agent.tools

/**
 * Termux setup status types.
 *
 * OpenClaw Source Reference:
 * - Android 平台特有，无直接 OpenClaw 对应（OpenClaw 用 PTY/Sandbox，不用 Termux）
 */

enum class TermuxSetupStep {
    TERMUX_NOT_INSTALLED,
    KEYPAIR_MISSING,
    SSHD_NOT_REACHABLE,
    SSH_AUTH_FAILED,
    READY,
    UNKNOWN
}

data class TermuxStatus(
    val termuxInstalled: Boolean,
    val sshReachable: Boolean,
    val sshAuthOk: Boolean,
    val keypairPresent: Boolean,
    val lastStep: TermuxSetupStep,
    val message: String
) {
    val ready: Boolean
        get() = termuxInstalled && sshReachable && sshAuthOk && lastStep == TermuxSetupStep.READY
}

object TermuxStatusFormatter {
    fun fallbackMessage(status: TermuxStatus): String {
        return when (status.lastStep) {
            TermuxSetupStep.TERMUX_NOT_INSTALLED -> "Termux is not installed."
            TermuxSetupStep.KEYPAIR_MISSING -> "SSH keypair is missing. Open Settings → Termux Setup to generate."
            TermuxSetupStep.SSHD_NOT_REACHABLE -> "sshd is not running. Open Termux and run: sshd"
            TermuxSetupStep.SSH_AUTH_FAILED -> "SSH auth failed. Open Termux and re-run the key setup command from Settings → Termux Setup."
            else -> "Open Settings → Termux Setup to configure."
        }
    }

    fun userFacingMessage(status: TermuxStatus): String {
        return "Termux is not ready: ${status.message} ${fallbackMessage(status)}".trim()
    }
}
