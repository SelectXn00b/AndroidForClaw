/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw

import androidx.core.content.FileProvider

/** Thin subclass so the manifest merger can distinguish this from OpenClaw's FileProvider. */
class ClawFileProvider : FileProvider()
