/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/store.ts
 *
 * Hermes adaptation: cron scheduling.
 */
package com.xiaomo.hermes.cron

import com.xiaomo.hermes.logging.Log
import com.google.gson.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CronStore(private val storePath: String) {
    companion object {
        private const val TAG = "CronStore"
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(CronSchedule::class.java, CronScheduleAdapter())
        .registerTypeAdapter(CronPayload::class.java, CronPayloadAdapter())
        .setPrettyPrinting()
        .create()

    private val lock = ReentrantLock()
    private val serializedCache = mutableMapOf<String, String>()

    fun load(): CronStoreFile {
        return lock.withLock {
            try {
                val file = File(storePath)
                if (!file.exists()) {
                    val empty = CronStoreFile()
                    save(empty)
                    return empty
                }
                val json = file.readText()
                gson.fromJson(json, CronStoreFile::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load store", e)
                CronStoreFile()
            }
        }
    }

    fun save(store: CronStoreFile) {
        lock.withLock {
            try {
                val json = gson.toJson(store)
                if (serializedCache[storePath] == json) return

                val file = File(storePath)
                file.parentFile?.mkdirs()
                
                if (file.exists()) {
                    file.copyTo(File("$storePath.bak"), overwrite = true)
                }

                val tempFile = File("$storePath.tmp")
                FileOutputStream(tempFile).use { it.write(json.toByteArray()) }
                tempFile.renameTo(file)

                serializedCache[storePath] = json
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save store", e)
            }
        }
    }
}

class CronScheduleAdapter : JsonSerializer<CronSchedule>, JsonDeserializer<CronSchedule> {
    override fun serialize(src: CronSchedule, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement {
        val json = JsonObject()
        when (src) {
            is CronSchedule.At -> {
                json.addProperty("kind", "at")
                json.addProperty("at", src.at)
            }
            is CronSchedule.Every -> {
                json.addProperty("kind", "every")
                json.addProperty("everyMs", src.everyMs)
                src.anchorMs?.let { json.addProperty("anchorMs", it) }
            }
            is CronSchedule.Cron -> {
                json.addProperty("kind", "cron")
                json.addProperty("expr", src.expr)
                src.tz?.let { json.addProperty("tz", it) }
                src.staggerMs?.let { json.addProperty("staggerMs", it) }
            }
        }
        return json
    }

    override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): CronSchedule {
        val obj = json.asJsonObject
        return when (obj.get("kind").asString) {
            "at" -> CronSchedule.At(obj.get("at").asString)
            "every" -> CronSchedule.Every(
                obj.get("everyMs").asLong,
                obj.get("anchorMs")?.asLong
            )
            "cron" -> CronSchedule.Cron(
                obj.get("expr").asString,
                obj.get("tz")?.asString,
                obj.get("staggerMs")?.asLong
            )
            else -> throw IllegalArgumentException("Unknown schedule kind")
        }
    }
}

class CronPayloadAdapter : JsonSerializer<CronPayload>, JsonDeserializer<CronPayload> {
    override fun serialize(src: CronPayload, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement {
        val json = JsonObject()
        when (src) {
            is CronPayload.SystemEvent -> {
                json.addProperty("kind", "systemEvent")
                json.addProperty("text", src.text)
            }
            is CronPayload.AgentTurn -> {
                json.addProperty("kind", "agentTurn")
                json.addProperty("message", src.message)
                src.model?.let { json.addProperty("model", it) }
                src.fallbacks?.let { list ->
                    val arr = JsonArray()
                    list.forEach { arr.add(it) }
                    json.add("fallbacks", arr)
                }
                src.thinking?.let { json.addProperty("thinking", it) }
                src.timeoutSeconds?.let { json.addProperty("timeoutSeconds", it) }
                src.deliver?.let { json.addProperty("deliver", it) }
                src.channel?.let { json.addProperty("channel", it) }
                src.to?.let { json.addProperty("to", it) }
                src.bestEffortDeliver?.let { json.addProperty("bestEffortDeliver", it) }
                src.lightContext?.let { json.addProperty("lightContext", it) }
                src.allowUnsafeExternalContent?.let { json.addProperty("allowUnsafeExternalContent", it) }
            }
        }
        return json
    }

    override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): CronPayload {
        val obj = json.asJsonObject
        return when (obj.get("kind").asString) {
            "systemEvent" -> CronPayload.SystemEvent(obj.get("text").asString)
            "agentTurn" -> CronPayload.AgentTurn(
                message = obj.get("message").asString,
                model = obj.get("model")?.asString,
                fallbacks = obj.getAsJsonArray("fallbacks")?.map { it.asString },
                thinking = obj.get("thinking")?.asString,
                timeoutSeconds = obj.get("timeoutSeconds")?.asInt,
                deliver = obj.get("deliver")?.asBoolean,
                channel = obj.get("channel")?.asString,
                to = obj.get("to")?.asString,
                bestEffortDeliver = obj.get("bestEffortDeliver")?.asBoolean,
                lightContext = obj.get("lightContext")?.asBoolean,
                allowUnsafeExternalContent = obj.get("allowUnsafeExternalContent")?.asBoolean
            )
            else -> throw IllegalArgumentException("Unknown payload kind")
        }
    }
}
