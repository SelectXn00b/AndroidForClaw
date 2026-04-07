package com.xiaomo.androidforclaw.infra

import java.io.File

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/json-file.ts
 *
 * Typed JSON file reader/writer backed by kotlinx.serialization or Gson.
 */
object JsonFile {

    fun readJsonString(file: File): String? {
        if (!file.exists()) return null
        return file.readText(Charsets.UTF_8)
    }

    fun writeJsonString(file: File, json: String) {
        file.parentFile?.mkdirs()
        file.writeText(json, Charsets.UTF_8)
    }

    fun exists(file: File): Boolean = file.exists()

    fun delete(file: File): Boolean = file.delete()
}
