package com.xiaomo.hermes.shared

import java.io.File

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/requirements.ts
 *
 * Evaluate runtime requirements: check for binaries, env vars, and config paths.
 */

data class RequirementCheck(
    val type: String, // "bin", "anyBin", "env", "config"
    val key: String,
    val met: Boolean,
    val detail: String? = null
)

data class RequirementsResult(
    val allMet: Boolean,
    val checks: List<RequirementCheck>
)

/**
 * Evaluate requirements against the runtime environment.
 * Aligned with TS evaluateRequirementsFromMetadataWithRemote().
 */
fun evaluateRequirements(
    requires: RuntimeRequires?,
    config: Any? = null,
    pathDirs: List<String> = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
): RequirementsResult {
    if (requires == null) return RequirementsResult(allMet = true, checks = emptyList())

    val checks = mutableListOf<RequirementCheck>()

    // Required binaries (all must exist)
    requires.bins?.forEach { bin ->
        val found = findBinaryInPath(bin, pathDirs)
        checks.add(RequirementCheck("bin", bin, found, if (found) null else "Binary not found: $bin"))
    }

    // Any-of binaries (at least one must exist)
    requires.anyBins?.let { anyBins ->
        if (anyBins.isNotEmpty()) {
            val anyFound = anyBins.any { findBinaryInPath(it, pathDirs) }
            checks.add(RequirementCheck(
                "anyBin",
                anyBins.joinToString("|"),
                anyFound,
                if (anyFound) null else "None of these binaries found: ${anyBins.joinToString(", ")}"
            ))
        }
    }

    // Required environment variables
    requires.env?.forEach { envVar ->
        val value = System.getenv(envVar)
        val met = !value.isNullOrBlank()
        checks.add(RequirementCheck("env", envVar, met, if (met) null else "Env var not set: $envVar"))
    }

    // Required config paths
    requires.config?.forEach { configPath ->
        val value = resolveConfigPath(config, configPath)
        val met = value != null && isTruthy(value)
        checks.add(RequirementCheck("config", configPath, met, if (met) null else "Config not set: $configPath"))
    }

    return RequirementsResult(
        allMet = checks.all { it.met },
        checks = checks
    )
}

private fun findBinaryInPath(binary: String, pathDirs: List<String>): Boolean {
    for (dir in pathDirs) {
        val file = File(dir, binary)
        if (file.exists() && file.canExecute()) return true
    }
    return false
}
