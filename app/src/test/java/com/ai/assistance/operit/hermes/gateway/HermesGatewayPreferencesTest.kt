package com.ai.assistance.operit.hermes.gateway

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HermesGatewayPreferences] — covers TC-CONFIG-001 through
 * TC-CONFIG-007 from docs/hermes-test-cases.md.
 *
 * Uses Robolectric for a real Android Context so that DataStore +
 * EncryptedSharedPreferences (with plain-SharedPreferences fallback) work on
 * the JVM without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetSingleton()
        resetDataStoreDelegate()
        // Wipe both stores between tests — DataStore persists files across
        // tests inside the same Robolectric JVM, which would otherwise make
        // "default flow emit" / "clamp" tests sensitive to run order.
        context.getSharedPreferences("hermes_gateway_secrets_plain", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val dataStoreFile = java.io.File(
            context.filesDir.parentFile,
            "datastore/hermes_gateway_preferences.preferences_pb",
        )
        if (dataStoreFile.exists()) dataStoreFile.delete()
    }

    @After
    fun tearDown() {
        resetSingleton()
    }

    // ── TC-CONFIG-001-a: getInstance returns same instance for same context ──
    @Test
    fun `singleton — getInstance twice returns same instance`() {
        val a = HermesGatewayPreferences.getInstance(context)
        val b = HermesGatewayPreferences.getInstance(context)
        assertSame("getInstance must be idempotent per application context", a, b)
    }

    // ── TC-CONFIG-002-a: write secret lands in encrypted store ──
    @Test
    fun `dual store writes — secret writes go to encrypted store`() {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            "cli_test_123",
        )

        val read = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
        )
        assertEquals("cli_test_123", read)

        // The secret must NOT show up in the non-secret DataStore.
        // Indirectly: the policy flow for the same feishu bucket should still
        // be default (empty), proving the writeSecret call did not touch it.
        val policy = runBlocking {
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_DM_POLICY,
                default = "allow",
            ).first()
        }
        assertEquals("allow", policy)
    }

    // ── TC-CONFIG-002-b: write policy lands in DataStore (not secret store) ──
    @Test
    fun `dual store writes — policy writes go to DataStore`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.savePlatformPolicyField(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.FIELD_DM_POLICY,
            "require_mention",
        )

        val out = prefs.platformPolicyFieldFlow(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.FIELD_DM_POLICY,
            default = "allow",
        ).first()
        assertEquals("require_mention", out)

        // The DataStore write must NOT have leaked into the secret store.
        val leakedSecret = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.FIELD_DM_POLICY,
        )
        assertEquals("", leakedSecret)
    }

    // ── TC-CONFIG-003-a: saveAgentMaxTurns(0) clamps to 1 ──
    @Test
    fun `maxTurns clamp low — 0 coerces to 1`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveAgentMaxTurns(0)
        assertEquals(1, prefs.agentMaxTurnsFlow.first())
    }

    // ── TC-CONFIG-003-b: saveAgentMaxTurns(9999) clamps to 200 ──
    @Test
    fun `maxTurns clamp high — 9999 coerces to 200`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveAgentMaxTurns(9999)
        assertEquals(200, prefs.agentMaxTurnsFlow.first())
    }

    @Test
    fun `maxTurns clamp — valid value passes through`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveAgentMaxTurns(42)
        assertEquals(42, prefs.agentMaxTurnsFlow.first())
    }

    // ── TC-CONFIG-004-a: clearSecrets("feishu") only clears feishu prefix ──
    @Test
    fun `clearSecrets prefix only — feishu clear does not touch weixin`() {
        val prefs = HermesGatewayPreferences.getInstance(context)

        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            "feishu_app_x",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
            "feishu_secret_y",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
            "wx_acct_z",
        )

        prefs.clearSecrets(HermesGatewayPreferences.PLATFORM_FEISHU)

        val feishuAppId = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
        )
        val feishuSecret = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
        )
        val weixinAcct = prefs.readSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
        )

        assertEquals("feishu secrets must be cleared", "", feishuAppId)
        assertEquals("feishu secrets must be cleared", "", feishuSecret)
        assertEquals("weixin secrets must be untouched", "wx_acct_z", weixinAcct)
    }

    // ── TC-CONFIG-005-a: first read of unset flow emits default ──
    @Test
    fun `default flow emit — unset serviceEnabled reads false`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        assertFalse(prefs.serviceEnabledFlow.first())
        assertEquals(
            HermesGatewayPreferences.DEFAULT_SERVICE_ENABLED,
            prefs.serviceEnabledFlow.first(),
        )
    }

    @Test
    fun `default flow emit — unset autoStartOnBoot reads false`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        assertFalse(prefs.autoStartOnBootFlow.first())
    }

    @Test
    fun `default flow emit — unset agentMaxTurns reads 30`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        assertEquals(
            HermesGatewayPreferences.DEFAULT_AGENT_MAX_TURNS,
            prefs.agentMaxTurnsFlow.first(),
        )
    }

    @Test
    fun `default flow emit — unset platformEnabled reads false`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        assertFalse(
            prefs.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_FEISHU).first(),
        )
    }

    // ── TC-CONFIG-006-a: writes survive singleton restart (same process) ──
    @Test
    fun `roundtrip persistence — values survive singleton restart`() = runBlocking {
        val first = HermesGatewayPreferences.getInstance(context)
        first.saveServiceEnabled(true)
        first.saveAgentMaxTurns(77)
        first.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_FEISHU, true)
        first.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            "persisted_app_id",
        )

        // Simulate a cold start by dropping the in-memory singleton.
        resetSingleton()

        val second = HermesGatewayPreferences.getInstance(context)
        assertNotEquals("singleton must be a new instance after reset", first, second)

        assertTrue(second.serviceEnabledFlow.first())
        assertEquals(77, second.agentMaxTurnsFlow.first())
        assertTrue(
            second.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_FEISHU).first(),
        )
        assertEquals(
            "persisted_app_id",
            second.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            ),
        )
    }

    // ── TC-CONFIG-007-a: constant key names match Python gateway config ──
    @Test
    fun `constant names — platform keys match Python lowercase tokens`() {
        assertEquals("feishu", HermesGatewayPreferences.PLATFORM_FEISHU)
        assertEquals("weixin", HermesGatewayPreferences.PLATFORM_WEIXIN)
    }

    @Test
    fun `constant names — policy field names match Python YAML keys`() {
        assertEquals("dm_policy", HermesGatewayPreferences.FIELD_DM_POLICY)
        assertEquals("group_policy", HermesGatewayPreferences.FIELD_GROUP_POLICY)
        assertEquals("dm_allow_from", HermesGatewayPreferences.FIELD_DM_ALLOW_FROM)
        assertEquals("group_allow_from", HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM)
        assertEquals("reply_to_mode", HermesGatewayPreferences.FIELD_REPLY_TO_MODE)
        assertEquals("require_mention", HermesGatewayPreferences.FIELD_REQUIRE_MENTION)
    }

    @Test
    fun `constant names — Feishu secret field names match Python extra keys`() {
        assertEquals("app_id", HermesGatewayPreferences.SECRET_FEISHU_APP_ID)
        assertEquals("app_secret", HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET)
        assertEquals("verification_token", HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN)
        assertEquals("encrypt_key", HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY)
        assertEquals("domain", HermesGatewayPreferences.SECRET_FEISHU_DOMAIN)
        assertEquals("bot_open_id", HermesGatewayPreferences.SECRET_FEISHU_BOT_OPEN_ID)
        assertEquals("bot_name", HermesGatewayPreferences.SECRET_FEISHU_BOT_NAME)
    }

    @Test
    fun `constant names — Weixin secret field names match Python extra keys`() {
        assertEquals("account_id", HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID)
        assertEquals("login_token", HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN)
    }

    // ─────────────────────── helpers ───────────────────────

    /**
     * Drop the [@Volatile] `INSTANCE` cache on the singleton so the next
     * `getInstance` call builds a fresh object — lets us simulate a cold
     * process restart without actually restarting the JVM.
     */
    private fun resetSingleton() {
        // Kotlin hoists companion-object `@Volatile` properties onto the outer
        // class as private static fields.
        val field = HermesGatewayPreferences::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    /**
     * Clear the cached [DataStore] on the file-private `Context.hermesGatewayDataStore`
     * extension delegate. `preferencesDataStore` returns a
     * `PreferenceDataStoreSingletonDelegate` that caches the [DataStore] in
     * a field named `INSTANCE` — nulling it plus deleting the on-disk file
     * gives us a fresh store for the next test, defeating cross-test state
     * leakage inside the same Robolectric JVM.
     */
    private fun resetDataStoreDelegate() {
        val kt = Class.forName("com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferencesKt")
        val delegateField = kt.getDeclaredField("hermesGatewayDataStore\$delegate")
        delegateField.isAccessible = true
        val delegate = delegateField.get(null)
        val instanceField = delegate.javaClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(delegate, null)
    }
}
