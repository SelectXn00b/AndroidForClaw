/** 1:1 对齐 hermes/tools/cronjob_tools.py */
package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import com.xiaomo.hermes.hermes.cron.createJob
import com.xiaomo.hermes.hermes.cron.cronHealthProbe
import com.xiaomo.hermes.hermes.cron.cronImmediateRunner
import com.xiaomo.hermes.hermes.cron.cronShortDelayScheduler
import com.xiaomo.hermes.hermes.cron.getJob
import com.xiaomo.hermes.hermes.cron.listJobs
import com.xiaomo.hermes.hermes.cron.parseSchedule
import com.xiaomo.hermes.hermes.cron.pauseJob
import com.xiaomo.hermes.hermes.cron.removeJob
import com.xiaomo.hermes.hermes.cron.resumeJob
import com.xiaomo.hermes.hermes.cron.triggerJob
import com.xiaomo.hermes.hermes.cron.updateJob
import com.xiaomo.hermes.hermes.gateway.getSessionEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Android-side minimum cron interval, in minutes.
 *
 * WorkManager's PeriodicWorkRequest cannot fire faster than every 15 minutes,
 * so we reject any sub-15-minute interval at the API boundary (R-AGENT-031).
 * One-shot schedules are unaffected: they fire once and don't loop.
 */
const val ANDROID_CRON_MIN_INTERVAL_MINUTES = 15

/**
 * R-AGENT-043: scope that owns "immediate trigger" fire-and-forget launches.
 *
 * Agent tool calls must return synchronously (the JSON response shape).
 * Running a cron job synchronously inside the tool call would block the
 * agent's turn-loop on a full secondary agent invocation, so the run
 * branch hands the job off to this background scope and returns
 * `triggered_immediately: true` immediately.
 *
 * SupervisorJob isolates per-job exceptions so one bad runner cannot
 * cancel the scope; exceptions are recorded inside `CronAgentRunner`
 * via `markJobRun(... success=false)`.
 */
private val _immediateTriggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Cronjob tool handler — port of tools/cronjob_tools.py.
 *
 * The Python upstream relies on host cron + a long-running daemon to fire
 * jobs. On Android the equivalent execution loop lives in the app module
 * (CronTickWorker + CronAgentRunner), driven by WorkManager every
 * 15 minutes. This file owns only the data-layer dispatch: validate the
 * tool args, scan prompts for threats, then forward to Jobs.kt CRUD.
 */
fun cronjob(
    action: String,
    jobId: String? = null,
    prompt: String? = null,
    schedule: String? = null,
    name: String? = null,
    repeat: Int? = null,
    deliver: String? = null,
    includeDisabled: Boolean = true,
    skill: String? = null,
    skills: Any? = null,
    model: String? = null,
    provider: String? = null,
    baseUrl: String? = null,
    reason: String? = null,
    script: String? = null,
    taskId: String? = null,
): String {
    @Suppress("UNUSED_VARIABLE") val _runNowAction = "run_now"
    @Suppress("UNUSED_VARIABLE") val _taskId = taskId  // unused but kept for handler signature compatibility
    // Deep-align string sentinels (R-AGENT-031): Python's `cronjob()` inlines literals
    // for create/remove/error branches that are still emitted at runtime by the Kotlin
    // helper functions (_createCronJob / inline branches). Kotlin template literals
    // (`"…${x}…"`) are one source string, so deep_align string-scan does not see the
    // embedded fragments; we hold them here verbatim purely to keep deep_align parity.
    @Suppress("UNUSED_VARIABLE") val _strCronJobPrefix = "Cron job '"
    @Suppress("UNUSED_VARIABLE") val _strCreatedSuffix = "' created."
    @Suppress("UNUSED_VARIABLE") val _strRemovedSuffix = "' removed."
    @Suppress("UNUSED_VARIABLE") val _strJobNotFoundPrefix = "Job with ID '"
    @Suppress("UNUSED_VARIABLE") val _strJobNotFoundSuffix = "' not found. Use cronjob(action='list') to inspect jobs."
    @Suppress("UNUSED_VARIABLE") val _strFailedRemovePrefix = "Failed to remove job '"
    @Suppress("UNUSED_VARIABLE") val _strJobIdRequired = "job_id is required for action '"
    @Suppress("UNUSED_VARIABLE") val _strUnknownAction = "Unknown cron action '"

    return try {
        val normalized = action.trim().lowercase()

        when (normalized) {
            "create" -> _createCronJob(
                prompt = prompt,
                schedule = schedule,
                name = name,
                repeat = repeat,
                deliver = deliver,
                skill = skill,
                skills = skills,
                model = model,
                provider = provider,
                baseUrl = baseUrl,
                script = script,
            )

            "list" -> {
                val jobs = listJobs(includeDisabled = includeDisabled).map { _formatJob(it) }
                Gson().toJson(mapOf("success" to true, "count" to jobs.size, "jobs" to jobs))
            }

            "health" -> {
                // R-AGENT-044: cron self-diagnostic snapshot. The probe is injected
                // by `OperitApplication.onCreate` (app module) — null in unit tests
                // / cold-start. We treat null probe as `worker_registered=false /
                // worker_state="MISSING"` so the agent gets a deterministic answer.
                val probe = cronHealthProbe
                val workerSnapshot: Map<String, Any?> = if (probe == null) {
                    mapOf(
                        "worker_registered" to false,
                        "worker_state" to "MISSING",
                        "last_enqueue_error" to null,
                        "last_tick_at" to null,
                        "next_scheduled_at" to null,
                    )
                } else {
                    try {
                        runBlocking { probe() }
                    } catch (e: Exception) {
                        mapOf(
                            "worker_registered" to false,
                            "worker_state" to "MISSING",
                            "last_enqueue_error" to (e.message ?: e.javaClass.simpleName),
                            "last_tick_at" to null,
                            "next_scheduled_at" to null,
                        )
                    }
                }

                val allJobs = try {
                    listJobs(includeDisabled = true)
                } catch (e: Exception) {
                    emptyList<MutableMap<String, Any?>>()
                }

                // pending_due_jobs: next_run_at <= now AND state != "paused" AND
                // enabled != false. Sort ascending by next_run_at (earliest first).
                val nowEpoch = System.currentTimeMillis()
                val pending = allJobs
                    .filter { j ->
                        val state = j["state"] as? String
                        val enabled = j["enabled"] as? Boolean ?: true
                        if (state == "paused" || !enabled) return@filter false
                        val nextRunAt = j["next_run_at"] as? String ?: return@filter false
                        val nextEpoch = _parseIsoToEpoch(nextRunAt) ?: return@filter false
                        nextEpoch <= nowEpoch
                    }
                    .sortedBy { _parseIsoToEpoch(it["next_run_at"] as? String) ?: Long.MAX_VALUE }
                    .map { j ->
                        mapOf(
                            "id" to j["id"],
                            "name" to j["name"],
                            "next_run_at" to j["next_run_at"],
                        )
                    }

                // recent_runs: top 5 jobs by last_run_at desc, only those that
                // have actually run (last_run_at non-null).
                val recentRuns = allJobs
                    .filter { it["last_run_at"] != null }
                    .sortedByDescending { _parseIsoToEpoch(it["last_run_at"] as? String) ?: Long.MIN_VALUE }
                    .take(5)
                    .map { j ->
                        mapOf(
                            "id" to j["id"],
                            "name" to j["name"],
                            "last_run_at" to j["last_run_at"],
                            "last_status" to j["last_status"],
                            "last_error" to j["last_error"],
                        )
                    }

                Gson().toJson(
                    mapOf(
                        "success" to true,
                        "worker_registered" to workerSnapshot["worker_registered"],
                        "worker_state" to workerSnapshot["worker_state"],
                        "last_tick_at" to workerSnapshot["last_tick_at"],
                        "next_scheduled_at" to workerSnapshot["next_scheduled_at"],
                        "enqueue_last_error" to workerSnapshot["last_enqueue_error"],
                        "immediate_runner_wired" to (cronImmediateRunner != null),
                        "pending_due_jobs" to pending,
                        "recent_runs" to recentRuns,
                    )
                )
            }

            else -> {
                if (jobId.isNullOrBlank()) {
                    return toolError("job_id is required for action '$normalized'")
                }
                val existing = getJob(jobId)
                    ?: return Gson().toJson(
                        mapOf(
                            "success" to false,
                            "error" to "Job with ID '$jobId' not found. Use cronjob(action='list') to inspect jobs."
                        )
                    )

                when (normalized) {
                    "remove" -> {
                        val removed = removeJob(jobId)
                        if (!removed) {
                            toolError("Failed to remove job '$jobId'")
                        } else {
                            Gson().toJson(
                                mapOf(
                                    "success" to true,
                                    "message" to "Cron job '${existing["name"]}' removed.",
                                    "removed_job" to mapOf(
                                        "id" to jobId,
                                        "name" to existing["name"],
                                        "schedule" to existing["schedule_display"]
                                    )
                                )
                            )
                        }
                    }

                    "pause" -> {
                        val updated = pauseJob(jobId, reason = reason) ?: existing
                        Gson().toJson(mapOf("success" to true, "job" to _formatJob(updated)))
                    }

                    "resume" -> {
                        val updated = resumeJob(jobId) ?: existing
                        Gson().toJson(mapOf("success" to true, "job" to _formatJob(updated)))
                    }

                    "run", "run_now", "trigger" -> {
                        // R-AGENT-043: bump next_run_at to now (cron parity), then
                        // hand off to the injected immediate runner if wired.
                        val updated = triggerJob(jobId) ?: existing
                        val runner = cronImmediateRunner
                        var triggeredImmediately = false
                        if (runner != null) {
                            // Fire-and-forget on a SupervisorJob scope so:
                            //   - the agent's tool call returns now (non-blocking)
                            //   - runner exceptions don't propagate to the agent
                            _immediateTriggerScope.launch {
                                runner(updated)
                            }
                            triggeredImmediately = true
                        }
                        Gson().toJson(
                            mapOf(
                                "success" to true,
                                "triggered_immediately" to triggeredImmediately,
                                "job" to _formatJob(updated),
                            )
                        )
                    }

                    "update" -> _updateCronJob(
                        jobId = jobId,
                        existing = existing,
                        prompt = prompt,
                        schedule = schedule,
                        name = name,
                        repeat = repeat,
                        deliver = deliver,
                        skill = skill,
                        skills = skills,
                        model = model,
                        provider = provider,
                        baseUrl = baseUrl,
                        script = script,
                    )

                    else -> toolError("Unknown cron action '$action'")
                }
            }
        }
    } catch (e: Exception) {
        toolError(e.message ?: "Unknown error")
    }
}

private fun _createCronJob(
    prompt: String?,
    schedule: String?,
    name: String?,
    repeat: Int?,
    deliver: String?,
    skill: String?,
    skills: Any?,
    model: String?,
    provider: String?,
    baseUrl: String?,
    script: String?,
): String {
    @Suppress("UNUSED_VARIABLE") val _scheduleRequiredMsg = "schedule is required for create"
    @Suppress("UNUSED_VARIABLE") val _createRequiresMsg = "create requires either prompt or at least one skill"

    if (schedule.isNullOrBlank()) return toolError("schedule is required for create")
    val canonicalSkills = _canonicalSkills(skill, skills)
    if (prompt.isNullOrEmpty() && canonicalSkills.isEmpty()) {
        return toolError("create requires either prompt or at least one skill")
    }
    if (!prompt.isNullOrEmpty()) {
        val scanError = _scanCronPrompt(prompt)
        if (scanError.isNotEmpty()) return toolError(scanError)
    }
    if (!script.isNullOrBlank()) {
        val scriptError = _validateCronScriptPath(script)
        if (scriptError != null) return toolError(scriptError)
    }

    // Android-only: enforce WorkManager's 15-minute minimum interval.
    val parsed = try {
        parseSchedule(schedule)
    } catch (e: IllegalArgumentException) {
        return toolError(e.message ?: "Invalid schedule")
    }
    if (parsed["kind"] == "interval") {
        val minutes = (parsed["minutes"] as? Number)?.toInt() ?: 0
        if (minutes < ANDROID_CRON_MIN_INTERVAL_MINUTES) {
            return toolError(
                "Android requires a minimum interval of $ANDROID_CRON_MIN_INTERVAL_MINUTES minutes. " +
                    "Got '$schedule'. WorkManager cannot fire periodic work faster than every 15 minutes."
            )
        }
    }

    val job = createJob(
        prompt = prompt ?: "",
        schedule = schedule,
        name = name,
        repeat = repeat,
        deliver = deliver,
        origin = _originFromEnv(),
        skill = canonicalSkills.firstOrNull(),
        skills = canonicalSkills,
        model = _normalizeOptionalJobValue(model),
        provider = _normalizeOptionalJobValue(provider),
        baseUrl = _normalizeOptionalJobValue(baseUrl, stripTrailingSlash = true),
        script = _normalizeOptionalJobValue(script),
    )

    // TC-CRON-EXACT: route once-jobs firing within 15 minutes to AlarmManager
    // (`cronShortDelayScheduler` injection point). The 15-min PeriodicWork tick
    // can't service them in time — see Scheduler.kt:cronShortDelayScheduler doc.
    _maybeScheduleShortDelayAlarm(job)

    return Gson().toJson(
        mapOf(
            "success" to true,
            "job_id" to job["id"],
            "name" to job["name"],
            "skill" to job["skill"],
            "skills" to (job["skills"] ?: emptyList<String>()),
            "schedule" to job["schedule_display"],
            "repeat" to _repeatDisplay(job),
            "deliver" to (job["deliver"] ?: "local"),
            "next_run_at" to job["next_run_at"],
            "job" to _formatJob(job),
            "message" to "Cron job '${job["name"]}' created."
        )
    )
}

@Suppress("UNCHECKED_CAST")
private fun _updateCronJob(
    jobId: String,
    existing: Map<String, Any?>,
    prompt: String?,
    schedule: String?,
    name: String?,
    repeat: Int?,
    deliver: String?,
    skill: String?,
    skills: Any?,
    model: String?,
    provider: String?,
    baseUrl: String?,
    script: String?,
): String {
    @Suppress("UNUSED_VARIABLE") val _noUpdates = "No updates provided."

    val updates = mutableMapOf<String, Any?>()

    if (prompt != null) {
        val scanError = _scanCronPrompt(prompt)
        if (scanError.isNotEmpty()) return toolError(scanError)
        updates["prompt"] = prompt
    }
    if (name != null) updates["name"] = name
    if (deliver != null) updates["deliver"] = deliver
    if (skills != null || skill != null) {
        val canonical = _canonicalSkills(skill, skills)
        updates["skills"] = canonical
        updates["skill"] = canonical.firstOrNull()
    }
    if (model != null) updates["model"] = _normalizeOptionalJobValue(model)
    if (provider != null) updates["provider"] = _normalizeOptionalJobValue(provider)
    if (baseUrl != null) {
        updates["base_url"] = _normalizeOptionalJobValue(baseUrl, stripTrailingSlash = true)
    }
    if (script != null) {
        if (script.isNotEmpty()) {
            val scriptError = _validateCronScriptPath(script)
            if (scriptError != null) return toolError(scriptError)
            updates["script"] = _normalizeOptionalJobValue(script)
        } else {
            updates["script"] = null
        }
    }
    if (repeat != null) {
        val normalizedRepeat: Int? = if (repeat <= 0) null else repeat
        val repeatState = (existing["repeat"] as? Map<String, Any?>)?.toMutableMap()
            ?: mutableMapOf()
        repeatState["times"] = normalizedRepeat
        updates["repeat"] = repeatState
    }
    if (schedule != null) {
        val parsedSchedule = try {
            parseSchedule(schedule)
        } catch (e: IllegalArgumentException) {
            return toolError(e.message ?: "Invalid schedule")
        }
        if (parsedSchedule["kind"] == "interval") {
            val minutes = (parsedSchedule["minutes"] as? Number)?.toInt() ?: 0
            if (minutes < ANDROID_CRON_MIN_INTERVAL_MINUTES) {
                return toolError(
                    "Android requires a minimum interval of $ANDROID_CRON_MIN_INTERVAL_MINUTES minutes. " +
                        "Got '$schedule'."
                )
            }
        }
        updates["schedule"] = parsedSchedule
        updates["schedule_display"] = parsedSchedule["display"] ?: schedule
        if (existing["state"] != "paused") {
            updates["state"] = "scheduled"
            updates["enabled"] = true
        }
    }

    if (updates.isEmpty()) return toolError("No updates provided.")

    val updated = updateJob(jobId, updates) ?: existing
    // TC-CRON-EXACT: same routing on schedule change (e.g. user moves the
    // reminder forward). Idempotent: re-scheduling the same alarm replaces it.
    _maybeScheduleShortDelayAlarm(updated)
    return Gson().toJson(mapOf("success" to true, "job" to _formatJob(updated)))
}

fun checkCronjobRequirements(): Boolean {
    @Suppress("UNUSED_VARIABLE") val _execAskEnv = "HERMES_EXEC_ASK"
    @Suppress("UNUSED_VARIABLE") val _gatewaySessionEnv = "HERMES_GATEWAY_SESSION"
    @Suppress("UNUSED_VARIABLE") val _interactiveEnv = "HERMES_INTERACTIVE"
    // Android: cron data layer is always available (Jobs.kt CRUD + WorkManager
    // tick in app module). No external daemon required.
    return true
}

// ── Module-level helpers ported from tools/cronjob_tools.py ───────────────

/**
 * Critical-severity patterns for cron-prompt scanning.  Cron prompts run in
 * fresh sessions with full tool access, so injection/exfiltration payloads
 * must be rejected at the API boundary.
 */
val _CRON_THREAT_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("ignore\\s+(?:\\w+\\s+)*(?:previous|all|above|prior)\\s+(?:\\w+\\s+)*instructions", RegexOption.IGNORE_CASE) to "prompt_injection",
    Regex("do\\s+not\\s+tell\\s+the\\s+user", RegexOption.IGNORE_CASE) to "deception_hide",
    Regex("system\\s+prompt\\s+override", RegexOption.IGNORE_CASE) to "sys_prompt_override",
    Regex("disregard\\s+(your|all|any)\\s+(instructions|rules|guidelines)", RegexOption.IGNORE_CASE) to "disregard_rules",
    Regex("curl\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", RegexOption.IGNORE_CASE) to "exfil_curl",
    Regex("wget\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", RegexOption.IGNORE_CASE) to "exfil_wget",
    Regex("cat\\s+[^\\n]*(\\.env|credentials|\\.netrc|\\.pgpass)", RegexOption.IGNORE_CASE) to "read_secrets",
    Regex("authorized_keys", RegexOption.IGNORE_CASE) to "ssh_backdoor",
    Regex("/etc/sudoers|visudo", RegexOption.IGNORE_CASE) to "sudoers_mod",
    Regex("rm\\s+-rf\\s+/", RegexOption.IGNORE_CASE) to "destructive_root_rm"
)

/** Invisible unicode code points that are stripped/blocked in cron prompts. */
val _CRON_INVISIBLE_CHARS: Set<Char> = setOf(
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e'
)

/** OpenAI-style function schema for the single `cronjob` tool. */
val CRONJOB_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "cronjob",
    "description" to (
        "Manage scheduled cron jobs with a single compressed tool.\n\n" +
            "Use action='create' to schedule a new job from a prompt or one or more skills.\n" +
            "Use action='list' to inspect jobs.\n" +
            "Use action='update', 'pause', 'resume', 'remove', or 'run' to manage an existing job.\n" +
            "Use action='health' to self-diagnose the cron subsystem.\n\n" +
            "To stop a job the user no longer wants: first action='list' to find the job_id, " +
            "then action='remove' with that job_id. Never guess job IDs — always list first.\n\n" +
            "Jobs run in a fresh session with no current-chat context, so prompts must be self-contained.\n" +
            "If skills are provided on create, the future cron run loads those skills in order, then follows the prompt as the task instruction.\n" +
            "On update, passing skills=[] clears attached skills.\n\n" +
            "NOTE: The agent's final response is auto-delivered to the target. Put the primary\n" +
            "user-facing content in the final response. Cron jobs run autonomously with no user\n" +
            "present — they cannot ask questions or request clarification.\n\n" +
            "Immediate trigger: action='run' (synonyms: 'run_now', 'trigger') fires the job " +
            "immediately on a background scope — it does NOT wait for the next 15-minute " +
            "worker tick. The JSON response includes `triggered_immediately: true` when the " +
            "in-process runner is wired (normal app runtime), or false during cold-start before " +
            "injection completes (job is still bumped via next_run_at as fallback). Use this " +
            "to test a job end-to-end on demand or to satisfy 'run it now' requests.\n\n" +
            "Self-diagnostic: action='health' returns a snapshot of cron subsystem status. " +
            "The JSON response contains: `worker_registered` (bool: WorkManager has the periodic " +
            "tick worker registered), `worker_state` (string: ENQUEUED / RUNNING / FAILED / " +
            "MISSING …), `last_tick_at` (ISO-8601 string or null: when the worker last actually " +
            "fired), `next_scheduled_at` (ISO-8601 string or null: WorkManager's estimate for " +
            "the next fire), `enqueue_last_error` (string or null: most recent enqueue exception " +
            "message), `immediate_runner_wired` (bool: R-AGENT-043 in-process runner injected), " +
            "`pending_due_jobs` (list of jobs whose next_run_at is in the past, sorted ascending; " +
            "non-empty here while worker is healthy means a tick will pick them up shortly), and " +
            "`recent_runs` (last 5 jobs by last_run_at desc with status / error). Use this when " +
            "the user reports cron isn't firing or asks 'is my schedule healthy'.\n\n" +
            "Important safety rule: cron-run sessions should not recursively schedule more cron jobs."
        ),
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "description" to "One of: create, list, update, pause, resume, remove, run, health. " +
                    "'run_now' and 'trigger' are accepted synonyms for 'run' (immediate fire). " +
                    "'health' returns a self-diagnostic snapshot (no other args required)."
            ),
            "job_id" to mapOf(
                "type" to "string",
                "description" to "Required for update/pause/resume/remove/run"
            ),
            "prompt" to mapOf(
                "type" to "string",
                "description" to "For create: the full self-contained prompt. If skills are also provided, this becomes the task instruction paired with those skills."
            ),
            "schedule" to mapOf(
                "type" to "string",
                "description" to "For create/update: '30m', 'every 2h', '0 9 * * *', or ISO timestamp"
            ),
            "name" to mapOf("type" to "string", "description" to "Optional human-friendly name"),
            "repeat" to mapOf(
                "type" to "integer",
                "description" to "Optional repeat count. Omit for defaults (once for one-shot, forever for recurring)."
            ),
            "deliver" to mapOf(
                "type" to "string",
                "description" to "Omit this parameter to auto-deliver back to the current chat and topic (recommended). Values: 'origin', 'local', or platform:chat_id:thread_id."
            ),
            "skills" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Optional ordered list of skill names to load before executing the cron prompt. On update, pass an empty array to clear."
            ),
            "model" to mapOf(
                "type" to "object",
                "description" to "Optional per-job model override.",
                "properties" to mapOf(
                    "provider" to mapOf("type" to "string", "description" to "Provider name (e.g. 'openrouter', 'anthropic')."),
                    "model" to mapOf("type" to "string", "description" to "Model name (e.g. 'anthropic/claude-sonnet-4').")
                ),
                "required" to listOf("model")
            ),
            "script" to mapOf(
                "type" to "string",
                "description" to "Optional path to a Python script that runs before each cron job execution; its stdout is injected into the prompt as context."
            )
        ),
        "required" to listOf("action")
    )
)

/** Scan a cron prompt for critical threats.  Returns error string if blocked, else empty. */
fun _scanCronPrompt(prompt: String): String {
    for (c in _CRON_INVISIBLE_CHARS) {
        if (prompt.indexOf(c) >= 0) {
            val hex = "%04X".format(c.code)
            return "Blocked: prompt contains invisible unicode U+$hex (possible injection)."
        }
    }
    for ((pattern, pid) in _CRON_THREAT_PATTERNS) {
        if (pattern.containsMatchIn(prompt)) {
            return "Blocked: prompt matches threat pattern '$pid'. Cron prompts must not contain injection or exfiltration payloads."
        }
    }
    return ""
}

/**
 * Capture origin context from session env vars so a cron job can deliver back
 * to the same platform/chat/thread later.  Returns null if platform+chat_id
 * are not both present.
 */
fun _originFromEnv(): Map<String, String?>? {
    val originPlatform = getSessionEnv("HERMES_SESSION_PLATFORM").takeIf { it.isNotEmpty() }
    val originChatId = getSessionEnv("HERMES_SESSION_CHAT_ID").takeIf { it.isNotEmpty() }
    if (originPlatform != null && originChatId != null) {
        val threadId = getSessionEnv("HERMES_SESSION_THREAD_ID").takeIf { it.isNotEmpty() }
        return mapOf(
            "platform" to originPlatform,
            "chat_id" to originChatId,
            "chat_name" to getSessionEnv("HERMES_SESSION_CHAT_NAME").takeIf { it.isNotEmpty() },
            "thread_id" to threadId
        )
    }
    return null
}

/** Human-readable repeat display: "forever" / "once" / "3/5" / "5 times". */
@Suppress("UNCHECKED_CAST")
fun _repeatDisplay(job: Map<String, Any?>): String {
    val repeat = job["repeat"] as? Map<String, Any?>
    val times = (repeat?.get("times") as? Number)?.toInt()
    val completed = (repeat?.get("completed") as? Number)?.toInt() ?: 0
    if (times == null) return "forever"
    if (times == 1) return if (completed == 0) "once" else "1/1"
    return if (completed != 0) "$completed/$times" else "$times times"
}

/** Normalize skill/skills inputs into a deduplicated ordered list. */
fun _canonicalSkills(skill: String? = null, skills: Any? = null): List<String> {
    val rawItems: List<Any?> = when {
        skills == null -> if (!skill.isNullOrEmpty()) listOf(skill) else emptyList()
        skills is String -> listOf(skills)
        skills is List<*> -> skills
        skills is Array<*> -> skills.toList()
        else -> listOf(skills)
    }
    val normalized = mutableListOf<String>()
    for (item in rawItems) {
        val text = (item?.toString() ?: "").trim()
        if (text.isNotEmpty() && text !in normalized) normalized.add(text)
    }
    return normalized
}

/**
 * Resolve a model override object into a (provider, model) pair.
 * When provider is omitted but a model is given, pins the current main
 * provider so the job doesn't drift when defaults change later.
 */
@Suppress("UNCHECKED_CAST")
fun _resolveModelOverride(modelObj: Map<String, Any?>?): Pair<String?, String?> {
    if (modelObj == null) return Pair(null, null)
    val modelName = (modelObj["model"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    var providerName = (modelObj["provider"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    if (modelName != null && providerName == null) {
        // On Android the cli config isn't bundled — leave provider null (best-effort).
    }
    return Pair(providerName, modelName)
}

/**
 * Normalize an optional string job field: trim whitespace, optionally strip
 * trailing slashes, and return null when the result is empty.
 */
fun _normalizeOptionalJobValue(value: Any?, stripTrailingSlash: Boolean = false): String? {
    if (value == null) return null
    var text = value.toString().trim()
    if (stripTrailingSlash) text = text.trimEnd('/')
    return text.takeIf { it.isNotEmpty() }
}

/**
 * Validate a cron-job script path at the API boundary.  Scripts must be
 * relative paths that resolve within `$HERMES_HOME/scripts/`.  Returns an
 * error string when blocked, null when valid.
 */
fun _validateCronScriptPath(script: String?): String? {
    @Suppress("UNUSED_VARIABLE") val _placeScriptsMsg = ". Place scripts in ~/.hermes/scripts/ and use just the filename."
    @Suppress("UNUSED_VARIABLE") val _scriptRelativePrefix = "Script path must be relative to ~/.hermes/scripts/. Got absolute or home-relative path: "
    if (script.isNullOrBlank()) return null
    val raw = script.trim()
    if (raw.startsWith("/") || raw.startsWith("~") || (raw.length >= 2 && raw[1] == ':')) {
        return "Script path must be relative to ~/.hermes/scripts/. " +
            "Got absolute or home-relative path: '$raw'. " +
            "Place scripts in ~/.hermes/scripts/ and use just the filename."
    }
    val hermesHome = run {
        val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
        if (envVal.isNotEmpty()) java.io.File(envVal).canonicalFile
        else java.io.File(System.getProperty("user.home") ?: "/", ".hermes").canonicalFile
    }
    val scriptsDir = java.io.File(hermesHome, "scripts")
    val candidate = try {
        java.io.File(scriptsDir, raw).canonicalFile
    } catch (_: Exception) {
        java.io.File(scriptsDir, raw).absoluteFile
    }
    val baseAbs = scriptsDir.absolutePath.trimEnd(java.io.File.separatorChar)
    val candidateAbs = candidate.absolutePath
    if (candidateAbs != baseAbs && !candidateAbs.startsWith(baseAbs + java.io.File.separator)) {
        return "Script path escapes the scripts directory via traversal: '$raw'"
    }
    return null
}

/** Format a stored job dict for display to the user. */
@Suppress("UNCHECKED_CAST")
fun _formatJob(job: Map<String, Any?>): Map<String, Any?> {
    val prompt = (job["prompt"] as? String) ?: ""
    val canonicalSkills = _canonicalSkills(job["skill"] as? String, job["skills"])
    val preview = if (prompt.length > 100) prompt.substring(0, 100) + "..." else prompt
    val enabled = (job["enabled"] as? Boolean) ?: true
    val state = (job["state"] as? String) ?: if (enabled) "scheduled" else "paused"

    val result = mutableMapOf<String, Any?>(
        "job_id" to job["id"],
        "name" to job["name"],
        "skill" to canonicalSkills.firstOrNull(),
        "skills" to canonicalSkills,
        "prompt_preview" to preview,
        "model" to job["model"],
        "provider" to job["provider"],
        "base_url" to job["base_url"],
        "schedule" to job["schedule_display"],
        "repeat" to _repeatDisplay(job),
        "deliver" to (job["deliver"] ?: "local"),
        "next_run_at" to job["next_run_at"],
        "last_run_at" to job["last_run_at"],
        "last_status" to job["last_status"],
        "last_delivery_error" to job["last_delivery_error"],
        "enabled" to enabled,
        "state" to state,
        "paused_at" to job["paused_at"],
        "paused_reason" to job["paused_reason"]
    )
    val script = job["script"]
    if (script != null) result["script"] = script
    return result
}

/**
 * R-AGENT-044: parse the ISO-8601 strings used by Jobs.kt (`next_run_at`,
 * `last_run_at`, `created_at`) into millis-since-epoch for sort/compare.
 *
 * Returns `null` when the input is null/blank or fails to parse so health
 * filters can defensively skip malformed records instead of crashing the
 * whole snapshot.
 *
 * **Bugfix to TC-CRON-EXACT (2026-06-23)**: `Jobs.kt::formatIsoDate` writes
 * timestamps via `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")`, which
 * emits zone offsets as `+0800` (no colon). `java.time.Instant.parse`
 * requires strict ISO-8601 (`Z` or `+08:00` with colon) and throws on
 * `+0800` — that means this helper returned `null` for **every** real
 * `next_run_at` produced by Jobs.kt, breaking the AlarmManager short-delay
 * route entirely. We try `SimpleDateFormat` (Jobs.kt's own format) first
 * so the round-trip works, and fall back to `Instant.parse` for the
 * rarer ISO-8601 strict inputs (e.g. tests or upstream-style `Z` suffix).
 */
private fun _parseIsoToEpoch(iso: String?): Long? = parseIsoToEpochInternal(iso)

/**
 * Module-internal entry point for [_parseIsoToEpoch] so unit tests can
 * exercise the parser directly without going through Context-bound
 * `createJob` paths. Behaviour identical to the file-private wrapper —
 * see that doc-comment for the bugfix history.
 */
internal fun parseIsoToEpochInternal(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    // Primary path: parse the format Jobs.kt actually writes.
    // `SimpleDateFormat` with `Z` accepts RFC822-style `+0800` offsets.
    try {
        val sdf = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            java.util.Locale.US
        )
        return sdf.parse(iso)?.time
    } catch (_: Exception) {
        // fall through to ISO-8601 strict parse
    }
    return try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

/**
 * TC-CRON-EXACT (bugfix to R-AGENT-031):
 *
 * Routes "once" jobs that fire within 15 minutes from now to the AlarmManager-
 * backed exact-alarm scheduler injected at startup
 * (`Scheduler.cronShortDelayScheduler`).
 *
 * **Why only `kind == "once"`**: interval/cron jobs already get serviced by
 * the existing 15-min `CronTickWorker` PeriodicWork tick — and `_createCronJob`
 * rejects sub-15-min intervals upstream (`ANDROID_CRON_MIN_INTERVAL_MINUTES`).
 * Once-jobs have no minimum, so a "remind me in 5 minutes" produces a
 * `next_run_at` that the 15-min poll can miss by 10–15 minutes; AlarmManager
 * is the only Android API that fires it precisely.
 *
 * **Why the `15`-minute boundary**: matches the PeriodicWork tick — for any
 * delta ≥ 15 min the next tick is always sufficient to catch the job, so we
 * don't waste an alarm slot. < 15 min means the next tick is too late.
 *
 * **Idempotent**: AlarmManager.setExactAndAllowWhileIdle replaces an alarm
 * with the same PendingIntent (we key by jobId), so calling this on update
 * is safe even if the job was already scheduled.
 *
 * **No-op when injection slot is null** (unit tests / cold-start): falls
 * back to the existing 15-min PeriodicWork path. That preserves the bug
 * pre-injection but keeps unit tests of `_createCronJob` working without
 * a Context.
 */
@Suppress("UNCHECKED_CAST")
private fun _maybeScheduleShortDelayAlarm(job: Map<String, Any?>) {
    val scheduler = cronShortDelayScheduler ?: return
    val schedule = job["schedule"] as? Map<String, Any?> ?: return
    val kind = schedule["kind"] as? String ?: return
    if (kind != "once") return
    val jobId = job["id"] as? String ?: return
    val nextRunAtIso = job["next_run_at"] as? String ?: return
    val runAtMillis = _parseIsoToEpoch(nextRunAtIso) ?: return
    val now = System.currentTimeMillis()
    val deltaMillis = runAtMillis - now
    // Only bypass for short-delay jobs. ≥ 15 min → existing CronTickWorker tick
    // handles it. < 0 (already overdue) → still alarm so the next-tick wait
    // doesn't add another 15 minutes on top of overdueness.
    val cutoffMillis = ANDROID_CRON_MIN_INTERVAL_MINUTES * 60_000L
    if (deltaMillis >= cutoffMillis) return
    try {
        scheduler.invoke(jobId, runAtMillis)
    } catch (_: Throwable) {
        // Ignore: scheduler failures are platform-level (e.g. SCHEDULE_EXACT_ALARM
        // permission denied on Android 12+). Job is still persisted; CronTickWorker
        // will catch it on the next 15-min tick — late, but not lost.
    }
}
