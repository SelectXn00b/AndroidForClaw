package com.xiaomo.hermes.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/skills.ts
 */


/**
 * Skill Install Specification (aligns with OpenClaw SkillInstallSpec)
 */
data class SkillInstallSpec(
    val id: String? = null,
    val kind: InstallKind,
    val label: String? = null,
    val bins: List<String>? = null,
    val os: List<String>? = null,

    // brew install
    val formula: String? = null,

    // npm/yarn/pnpm/bun install
    val `package`: String? = null,

    // go install
    val module: String? = null,

    // download install
    val url: String? = null,
    val archive: String? = null,               // tar.gz, tar.bz2, zip
    val extract: Boolean? = null,
    val stripComponents: Int? = null,
    val targetDir: String? = null
)

/**
 * Installer Type
 */
enum class InstallKind {
    BREW,       // Homebrew (macOS/Linux)
    NODE,       // npm/yarn/pnpm/bun
    GO,         // go install
    UV,         // uv (Python)
    DOWNLOAD,   // Direct download
    APK         // Android APK (Android-specific)
}

// Status-related types (SkillStatusReport, SkillStatusEntry, SkillConfigCheck,
// SkillInstallOption) are in SkillStatus.kt (aligns with skills-status.ts)

/**
 * Skills Limits Configuration (aligns with OpenClaw default limits)
 */
data class SkillsLimits(
    val maxCandidatesPerRoot: Int = 300,
    val maxSkillsLoadedPerSource: Int = 200,
    val maxSkillsInPrompt: Int = 150,
    val maxSkillsPromptChars: Int = 30_000,
    val maxSkillFileBytes: Int = 256_000
)
