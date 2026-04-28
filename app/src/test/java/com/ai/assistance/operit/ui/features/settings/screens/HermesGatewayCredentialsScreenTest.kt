package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HermesGatewayCredentialsScreen] — covers TC-UI-010 through
 * TC-UI-015 from docs/hermes-test-cases.md.
 *
 * The screen reads/writes real [HermesGatewayPreferences] (DataStore for
 * `platformEnabled`, EncryptedSharedPreferences for secrets). We run under
 * Robolectric with the stock Android Application so those storages work on
 * the JVM, and wipe both stores between tests (mirrors
 * [HermesGatewayPreferencesTest]).
 *
 * Scope decisions:
 *  - TC-UI-010-a/b assert the actual production field counts (Feishu 4,
 *    Weixin 2). The TC doc was updated to match — the earlier "6/3"
 *    count was a planner miscount; R-UI-001 does not pin a field count,
 *    and Feishu `domain/bot_open_id/bot_name` are auto-populated by the
 *    QR / probe_bot flow, not hand-entered here.
 *  - TC-UI-011-a reflects into the private top-level `FEISHU_FIELDS` /
 *    `WEIXIN_FIELDS` lists to verify each secret-bearing field is flagged
 *    `isSecret = true` (i.e. wired to `PasswordVisualTransformation`). The
 *    `CredentialField.isSecret` flag is the single source of truth for
 *    masking; verifying it is equivalent to asserting
 *    `textVisible=false` on the rendered field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayCredentialsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: HermesGatewayPreferences

    @Before
    fun setUp() {
        resetSingleton()
        resetDataStoreDelegate()
        context.getSharedPreferences("hermes_gateway_secrets_plain", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val dataStoreFile = java.io.File(
            context.filesDir.parentFile,
            "datastore/hermes_gateway_preferences.preferences_pb",
        )
        if (dataStoreFile.exists()) dataStoreFile.delete()
        prefs = HermesGatewayPreferences.getInstance(context)
    }

    @After
    fun tearDown() {
        resetSingleton()
    }

    // ── TC-UI-010-a: Feishu card exposes exactly 4 fields ──
    @Test
    fun `feishu field count — exactly 4 fields rendered`() {
        composeTestRule.setContent { HermesGatewayCredentialsScreen() }

        listOf(
            R.string.hermes_credentials_feishu_app_id,
            R.string.hermes_credentials_feishu_app_secret,
            R.string.hermes_credentials_feishu_verification_token,
            R.string.hermes_credentials_feishu_encrypt_key,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(context.getString(resId))
                .assertIsDisplayed()
        }
        assertEquals(
            "FEISHU_FIELDS length must be exactly 4",
            4,
            readPrivateFieldList("FEISHU_FIELDS").size,
        )
    }

    // ── TC-UI-010-b: Weixin card exposes exactly 2 fields ──
    @Test
    fun `weixin field count — exactly 2 fields rendered`() {
        composeTestRule.setContent { HermesGatewayCredentialsScreen() }

        // Weixin card sits below the Feishu card inside a verticalScroll —
        // in the tiny default test viewport it can be off-fold. `assertExists`
        // confirms the field's composable is in the layout tree (the count
        // TC is what matters; visibility is a display-detail not wired into
        // this TC).
        listOf(
            R.string.hermes_credentials_weixin_account_id,
            R.string.hermes_credentials_weixin_login_token,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(context.getString(resId))
                .assertExists()
        }
        assertEquals(
            "WEIXIN_FIELDS length must be exactly 2",
            2,
            readPrivateFieldList("WEIXIN_FIELDS").size,
        )
    }

    // ── TC-UI-011-a: secret-flagged fields back the PasswordVisualTransformation ──
    @Test
    fun `password masked default — secret fields carry isSecret=true`() {
        // Expected secret keys per production source (HermesGatewayCredentialsScreen.kt L100-110).
        val expectedSecretFeishu = setOf(
            HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
            HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN,
            HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY,
        )
        val feishuFields = readPrivateFieldList("FEISHU_FIELDS")
        val feishuSecretKeys = feishuFields
            .filter { readBooleanProp(it, "isSecret") }
            .map { readStringProp(it, "key") }
            .toSet()
        assertEquals(
            "Feishu secret-flagged fields must match expected set",
            expectedSecretFeishu,
            feishuSecretKeys,
        )

        val weixinFields = readPrivateFieldList("WEIXIN_FIELDS")
        val weixinSecretKeys = weixinFields
            .filter { readBooleanProp(it, "isSecret") }
            .map { readStringProp(it, "key") }
            .toSet()
        assertEquals(
            "Weixin secret-flagged fields must be exactly {login_token}",
            setOf(HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN),
            weixinSecretKeys,
        )

        // Sanity: every non-secret key was NOT tagged secret (no leaks).
        val nonSecretFeishu = feishuFields
            .filterNot { readBooleanProp(it, "isSecret") }
            .map { readStringProp(it, "key") }
        assertEquals(listOf(HermesGatewayPreferences.SECRET_FEISHU_APP_ID), nonSecretFeishu)
    }

    // ── TC-UI-012-a: flipping the enable switch writes to prefs ──
    @Test
    fun `enable toggle immediate save — switch flip persists to prefs`() {
        composeTestRule.setContent { HermesGatewayCredentialsScreen() }

        // There are two toggle (Switch) nodes — one per card. Both start Off.
        val switchNodes = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        )
        val count = switchNodes.fetchSemanticsNodes().size
        assertTrue("must find ≥ 2 toggle nodes (Feishu + Weixin switches)", count >= 2)
        switchNodes[0].assertIsOff()
        switchNodes[1].assertIsOff()

        // Click Feishu (index 0 — top card in layout order).
        switchNodes[0].performClick()
        composeTestRule.waitForIdle()

        // The onEnabledChange callback launches a suspend save on the
        // rememberCoroutineScope; DataStore's write hops to IO. Poll the
        // Flow until the edit lands, or time out.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                prefs.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_FEISHU).first()
            }
        }

        val feishuEnabled = runBlocking {
            prefs.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_FEISHU).first()
        }
        val weixinEnabled = runBlocking {
            prefs.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_WEIXIN).first()
        }
        assertTrue("Feishu switch tap must flip prefs to true", feishuEnabled)
        assertFalse("Weixin must remain false", weixinEnabled)
    }

    // ── TC-UI-013-a: Save button writes every field exactly once ──
    @Test
    fun `save per field write — button tap writes all Feishu fields`() {
        composeTestRule.setContent { HermesGatewayCredentialsScreen() }

        // Type into all 4 Feishu fields.
        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_credentials_feishu_app_id),
        ).performTextInput("cli_app_id_7")
        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_credentials_feishu_app_secret),
        ).performTextInput("secret_xyz")
        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_credentials_feishu_verification_token),
        ).performTextInput("vt_abc")
        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_credentials_feishu_encrypt_key),
        ).performTextInput("ek_123")

        // Two 保存 buttons (one per card). We want the Feishu card's — it sits
        // above the Weixin one in Column layout order, so index 0.
        val saveButtons = composeTestRule.onAllNodesWithText(
            context.getString(R.string.hermes_common_save),
        )
        assertEquals("exactly 2 Save buttons (one per card)", 2, saveButtons.fetchSemanticsNodes().size)
        saveButtons[0].performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "cli_app_id_7",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            ),
        )
        assertEquals(
            "secret_xyz",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
            ),
        )
        assertEquals(
            "vt_abc",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN,
            ),
        )
        assertEquals(
            "ek_123",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY,
            ),
        )
        // And Weixin was untouched.
        assertEquals(
            "Weixin account_id must stay empty",
            "",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
            ),
        )
    }

    // ── TC-UI-014-a: savedFlash shows after Save, hides after 1500ms ──
    @Test
    fun `saved flash 1500ms — flash visible after save, hidden after delay`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { HermesGatewayCredentialsScreen() }

        // Prime composition.
        composeTestRule.mainClock.advanceTimeBy(100)

        val saveButton = composeTestRule.onAllNodesWithText(
            context.getString(R.string.hermes_common_save),
        )[0]
        saveButton.performClick()

        // Let the button click + LaunchedEffect kick off.
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // savedFlash true now.
        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_common_saved),
        ).assertIsDisplayed()

        // Advance past the 1500ms delay.
        composeTestRule.mainClock.advanceTimeBy(1600)
        composeTestRule.waitForIdle()

        // Flash must have cleared.
        val flashNodes = composeTestRule.onAllNodesWithText(
            context.getString(R.string.hermes_common_saved),
        ).fetchSemanticsNodes()
        assertTrue("saved flash must clear after 1500ms", flashNodes.isEmpty())
    }

    // ── TC-UI-015-a: fields are pre-populated from prefs on mount ──
    @Test
    fun `initial values from prefs — seeded secrets render in fields`() {
        // Pre-seed prefs BEFORE composition.
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            "seeded_app_id",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
            "seeded_wx_acct",
        )

        composeTestRule.setContent { HermesGatewayCredentialsScreen() }
        composeTestRule.waitForIdle()

        // The seeded values must be present in the layout tree. `assertExists`
        // (vs. `assertIsDisplayed`) tolerates the Weixin card being below the
        // verticalScroll fold in the tiny default test viewport.
        composeTestRule.onNodeWithText("seeded_app_id").assertExists()
        composeTestRule.onNodeWithText("seeded_wx_acct").assertExists()
    }

    // ─────────────────────── helpers ───────────────────────

    private fun resetSingleton() {
        val field = HermesGatewayPreferences::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    /**
     * See [HermesGatewayPreferencesTest.resetDataStoreDelegate] for rationale.
     * `preferencesDataStore` caches a singleton DataStore per (Context, name)
     * inside its delegate; deleting the on-disk file is not enough.
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

    /**
     * `FEISHU_FIELDS` / `WEIXIN_FIELDS` are private top-level vals inside
     * `HermesGatewayCredentialsScreen.kt`. Kotlin hoists file-private
     * top-level vals to static fields on `<File>Kt`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun readPrivateFieldList(name: String): List<Any> {
        val kt = Class.forName(
            "com.ai.assistance.operit.ui.features.settings.screens.HermesGatewayCredentialsScreenKt",
        )
        val f = kt.getDeclaredField(name)
        f.isAccessible = true
        return f.get(null) as List<Any>
    }

    private fun readBooleanProp(obj: Any, prop: String): Boolean {
        val f = obj.javaClass.getDeclaredField(prop)
        f.isAccessible = true
        return f.getBoolean(obj)
    }

    private fun readStringProp(obj: Any, prop: String): String {
        val f = obj.javaClass.getDeclaredField(prop)
        f.isAccessible = true
        return f.get(obj) as String
    }
}
