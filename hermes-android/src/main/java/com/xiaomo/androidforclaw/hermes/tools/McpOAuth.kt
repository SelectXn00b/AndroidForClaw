package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * MCP OAuth credential resolution.
 * Ported from mcp_oauth.py
 */
object McpOAuth {

    private const val TAG = "McpOAuth"
    private val gson = Gson()

    data class TokenSet(
        val accessToken: String = "",
        val tokenType: String = "Bearer",
        val expiresAt: String? = null,
    )

    /**
     * Load OAuth token for a given MCP server name.
     * Returns the token string or null if not available.
     */
    fun loadToken(serverName: String, authFile: File? = null): String? {
        // 1. Environment variable override
        val envKey = "MCP_${serverName.uppercase().replace('-', '_')}_TOKEN"
        val envToken = System.getenv(envKey)
        if (!envToken.isNullOrBlank()) return envToken.trim()

        // 2. Auth file
        val file = authFile ?: getDefaultAuthFile()
        if (file?.exists() == true) {
            try {
                val json = gson.fromJson(file.readText(Charsets.UTF_8), Map::class.java) as Map<String, Any>
                val providers = json["providers"] as? Map<String, Any> ?: return null
                val entry = providers[serverName] as? Map<String, Any> ?: return null
                val accessToken = entry["access_token"] as? String
                if (!accessToken.isNullOrBlank()) return accessToken.trim()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load OAuth token for $serverName: ${e.message}")
            }
        }

        return null
    }

    private fun getDefaultAuthFile(): File? {
        val home = System.getProperty("user.home") ?: return null
        return File(home, ".hermes/auth.json").takeIf { it.exists() }
    }



    fun _tokensPath(): String {
        return ""
    }
    fun _clientInfoPath(): String {
        return ""
    }
    suspend fun getTokens(): Any? {
        return null
    }
    suspend fun setTokens(tokens: Any?): Unit {
        // TODO: implement setTokens
    }
    suspend fun getClientInfo(): Any? {
        return null
    }
    suspend fun setClientInfo(clientInfo: Any?): Unit {
        // TODO: implement setClientInfo
    }
    /** Delete all stored OAuth state for this server. */
    fun remove(): Unit {
        // TODO: implement remove
    }
    /** Return True if we have tokens on disk (may be expired). */
    fun hasCachedTokens(): Boolean {
        return false
    }

}
