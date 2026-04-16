package com.xiaomo.hermes.compat

/**
 * OpenClaw module: compat
 * Source: OpenClaw/src/compat/legacy-names.ts
 *
 * Legacy project name constants and manifest key aliases for backwards compatibility.
 */
object CompatLayer {
    const val PROJECT_NAME = "openclaw"
    val LEGACY_PROJECT_NAMES = listOf("pi", "clawbot")
    const val MANIFEST_KEY = PROJECT_NAME
    val LEGACY_MANIFEST_KEYS = LEGACY_PROJECT_NAMES
    val LEGACY_PLUGIN_MANIFEST_FILENAMES = listOf("pi.plugin.json", "clawbot.plugin.json")
    const val ANDROID_APP_ID = "com.xiaomo.hermes"
}
