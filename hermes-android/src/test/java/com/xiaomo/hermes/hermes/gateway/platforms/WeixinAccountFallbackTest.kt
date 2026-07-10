package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix-1 (TC-GW-112-a): when the gateway config carries an
 * `account_id` but no `login_token`, the Weixin adapter must call
 * `loadWeixinAccount(hermesHome, accountId)` and backfill `_loginToken`
 * (and `_baseUrl` when persisted). Mirrors Python
 * `weixin.py:1166-1170`. Without this, a re-launched gateway after
 * EncryptedSP clears (or a config refresh that drops in-memory token
 * but leaves the on-disk file) silently fails to connect.
 *
 * Source-scan because driving the constructor + persisted file end-to-end
 * requires Application Context + EncryptedSP + an actual hermesHome
 * directory layout.
 */
class WeixinAccountFallbackTest {

    private val source: String by lazy { File(weixinPath()).readText() }

    @Test
    fun `TC-GW-112-a fallback loads persisted token`() {
        // The fallback must be expressed as: when accountId is non-empty
        // AND loginToken is empty, call loadWeixinAccount and backfill
        // _loginToken / _baseUrl from the persisted map. The condition is
        // the heart of the fix — assert all three pieces appear.
        assertTrue(
            "TC-GW-112-a: source must contain a fallback guarded by `_accountId.isNotEmpty()` and `_loginToken.isEmpty()`",
            Regex("""_accountId\.isNotEmpty\s*\(\s*\)\s*&&\s*_loginToken\.isEmpty\s*\(\s*\)""")
                .containsMatchIn(source),
        )
        assertTrue(
            "TC-GW-112-a: fallback must call loadWeixinAccount(hermesHome, _accountId)",
            Regex("""loadWeixinAccount\s*\(""").containsMatchIn(source),
        )
        // The fallback must surface the persisted `token` (and base_url) into
        // the adapter's mutable `_loginToken` / `_baseUrl` fields. We accept
        // either direct re-assignment or `?: ` chains.
        assertTrue(
            "TC-GW-112-a: fallback must reassign `_loginToken` from the persisted map",
            Regex("""_loginToken\s*=\s*[^=]""").containsMatchIn(source),
        )
    }

    private fun weixinPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Weixin.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Weixin.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Weixin.kt — cwd=${File(".").absolutePath}")
    }
}
