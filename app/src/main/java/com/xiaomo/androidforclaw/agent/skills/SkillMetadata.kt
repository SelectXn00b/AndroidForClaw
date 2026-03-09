package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Skills Metadata Definition
 *
 * Fully aligns with OpenClaw's skills/types.ts
 * Used to parse metadata.openclaw field in SKILL.md
 */

/**
 * Skill Entry (aligns with SkillEntry)
 */
data class SkillEntry(
    val skill: Skill,
    val frontmatter: ParsedSkillFrontmatter,
    val metadata: OpenClawSkillMetadata? = null,
    val invocation: SkillInvocationPolicy? = null
)

/**
 * Basic Skill Information (from pi-coding-agent)
 */
data class Skill(
    val name: String,
    val description: String,
    val content: String,
    val filePath: String
)

/**
 * Parsed Frontmatter
 */
data class ParsedSkillFrontmatter(
    val name: String,
    val description: String,
    val metadata: Map<String, Any?>? = null
)

/**
 * OpenClaw Skill Metadata (aligns with OpenClawSkillMetadata)
 */
data class OpenClawSkillMetadata(
    val always: Boolean = false,
    val skillKey: String? = null,
    val primaryEnv: String? = null,
    val emoji: String? = null,
    val homepage: String? = null,
    val os: List<String>? = null,              // darwin, linux, win32, android
    val requires: SkillRequirements? = null,
    val install: List<SkillInstallSpec>? = null
)

/**
 * Skill Requirements (aligns with requires field)
 */
data class SkillRequirements(
    val bins: List<String>? = null,            // Required binaries
    val anyBins: List<String>? = null,         // At least one must exist
    val env: List<String>? = null,             // Required environment variables
    val config: List<String>? = null           // openclaw.json path checks
)

/**
 * Skill Install Specification (aligns with SkillInstallSpec)
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

/**
 * Skill Invocation Policy (aligns with SkillInvocationPolicy)
 */
data class SkillInvocationPolicy(
    val invocation: InvocationType? = null,
    val acceptFiles: List<String>? = null,
    val outputPaths: List<String>? = null
)

enum class InvocationType {
    NEVER,
    USER,
    ALWAYS
}

/**
 * Skill Status Report (aligns with SkillStatusReport)
 */
data class SkillStatusReport(
    val workspaceDir: String,
    val managedSkillsDir: String,
    val skills: List<SkillStatusEntry>
)

/**
 * Skill Status Entry (aligns with SkillStatusEntry)
 */
data class SkillStatusEntry(
    val name: String,
    val description: String,
    val source: SkillSource,
    val bundled: Boolean,
    val filePath: String,
    val baseDir: String,
    val skillKey: String,
    val primaryEnv: String? = null,
    val emoji: String? = null,
    val homepage: String? = null,
    val always: Boolean,
    val disabled: Boolean,
    val blockedByAllowlist: Boolean,
    val eligible: Boolean,
    val requirements: SkillRequirements? = null,
    val missing: SkillRequirements? = null,
    val configChecks: List<SkillConfigCheck>,
    val install: List<SkillInstallOption>
)

// SkillSource already defined in SkillDocument.kt, removed here to avoid duplication

/**
 * Config Check Result
 */
data class SkillConfigCheck(
    val path: String,
    val exists: Boolean,
    val value: Any? = null
)

/**
 * Available Install Option
 */
data class SkillInstallOption(
    val installId: String,
    val kind: InstallKind,
    val label: String,
    val available: Boolean,
    val reason: String? = null
)

/**
 * Skills Limits Configuration (aligns with OpenClaw default limits)
 */
data class SkillsLimits(
    val maxCandidatesPerRoot: Int = 300,
    val maxSkillsLoadedPerSource: Int = 200,
    val maxSkillsInPrompt: Int = 150,          // Can be reduced to 50-100 on Android
    val maxSkillsPromptChars: Int = 30_000,
    val maxSkillFileBytes: Int = 256_000
)
