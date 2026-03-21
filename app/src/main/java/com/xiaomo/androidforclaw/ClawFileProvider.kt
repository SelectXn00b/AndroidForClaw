package com.xiaomo.androidforclaw

import androidx.core.content.FileProvider

/** Thin subclass so the manifest merger can distinguish this from OpenClaw's FileProvider. */
class ClawFileProvider : FileProvider()
