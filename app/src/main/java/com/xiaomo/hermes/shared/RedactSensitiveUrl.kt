package com.xiaomo.hermes.shared

import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/net/redact-sensitive-url.ts
 *
 * Redact sensitive query parameters and credentials from URLs.
 */

const val SENSITIVE_URL_HINT_TAG = "url-secret"

private val SENSITIVE_URL_QUERY_PARAM_NAMES = setOf(
    "token", "key", "api_key", "apikey", "secret",
    "access_token", "password", "pass", "auth",
    "client_secret", "refresh_token"
)

fun isSensitiveUrlQueryParamName(name: String): Boolean =
    name.lowercase() in SENSITIVE_URL_QUERY_PARAM_NAMES

fun isSensitiveUrlConfigPath(path: String): Boolean {
    if (path.endsWith(".baseUrl") || path.endsWith(".httpUrl")) return true
    if (path.endsWith(".request.proxy.url")) return true
    return Regex("""^mcp\.servers\.(?:\*|[^.]+)\.url$""").matches(path)
}

/** Redact sensitive parts of a URL (credentials, sensitive query params). */
fun redactSensitiveUrl(value: String): String {
    return try {
        val url = URL(value)
        var mutated = false
        val sb = StringBuilder()

        // Reconstruct URL with redacted userinfo
        sb.append(url.protocol).append("://")
        val userInfo = url.userInfo
        if (userInfo != null) {
            sb.append("***:***@")
            mutated = true
        }
        sb.append(url.host)
        if (url.port != -1 && url.port != url.defaultPort) {
            sb.append(":").append(url.port)
        }
        sb.append(url.path ?: "")

        // Redact sensitive query params
        val query = url.query
        if (query != null) {
            val params = query.split("&").joinToString("&") { param ->
                val eqIdx = param.indexOf('=')
                if (eqIdx > 0) {
                    val key = param.substring(0, eqIdx)
                    if (isSensitiveUrlQueryParamName(key)) {
                        mutated = true
                        "$key=***"
                    } else param
                } else param
            }
            sb.append("?").append(params)
        }

        val ref = url.ref
        if (ref != null) sb.append("#").append(ref)

        if (mutated) sb.toString() else value
    } catch (_: Exception) {
        value
    }
}

/** Redact sensitive parts of a URL-like string (fallback for malformed URLs). */
fun redactSensitiveUrlLikeString(value: String): String {
    val redacted = redactSensitiveUrl(value)
    if (redacted != value) return redacted
    // Fallback: regex-based redaction
    return value
        .replace(Regex("""//([^@/?#]+)@"""), "//***:***@")
        .replace(Regex("""([?&])([^=&]+)=([^&]*)""")) { match ->
            val prefix = match.groupValues[1]
            val key = match.groupValues[2]
            if (isSensitiveUrlQueryParamName(key)) "${prefix}${key}=***"
            else match.value
        }
}
