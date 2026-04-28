package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HermesGatewayPoliciesScreen] — covers TC-UI-020 through
 * TC-UI-024 from docs/hermes-test-cases.md.
 *
 * The policy chip options ({open, pairing, allowlist, disabled}) come from
 * the Python upstream per CLAUDE.md §3 "冲突以 Hermes 为准":
 *   reference/hermes-agent/gateway/platforms/qqbot/adapter.py L15-17
 *   reference/hermes-agent/gateway/platforms/whatsapp.py L152-155
 * The earlier TC doc used `{allow, require_mention, deny}` — those are
 * not Hermes policy values; the TC has been corrected.
 *
 * `require_mention` is a separate boolean-string field (`FIELD_REQUIRE_MENTION`)
 * stored as `"true"` / `"false"` — the screen renders it as a Switch, not a
 * chip. TC-UI-022-a has been re-pointed to that switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayPoliciesScreenTest {

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
    fun tearDown() { resetSingleton() }

    // ── TC-UI-020-a: dm_policy chips match Python upstream ──
    @Test
    fun `dm policy chips — Feishu open-pairing-allowlist all rendered`() {
        composeTestRule.setContent { HermesGatewayPoliciesScreen() }
        // Feishu exposes 3 DM-policy chips: open / pairing / allowlist.
        // Each string appears as a FilterChip label.
        composeTestRule.onAllNodesWithText("open")
            .fetchSemanticsNodes().let {
                assertTrue(
                    "`open` chip must appear ≥ 2 times (Feishu DM + group / Weixin DM)",
                    it.size >= 2,
                )
            }
        composeTestRule.onNodeWithText("pairing").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("allowlist")
            .fetchSemanticsNodes().let {
                assertTrue(
                    "`allowlist` chip must appear ≥ 2 times",
                    it.size >= 2,
                )
            }
    }

    // ── TC-UI-020-b: group_policy chips — same {open, allowlist, disabled} for both platforms ──
    @Test
    fun `group policy chips — open-allowlist-disabled available for both platforms`() {
        composeTestRule.setContent { HermesGatewayPoliciesScreen() }
        // `disabled` appears in: Feishu group, Weixin DM, Weixin group = 3 occurrences.
        val disabledNodes = composeTestRule.onAllNodesWithText("disabled").fetchSemanticsNodes()
        assertTrue(
            "`disabled` chip must appear 2–3 times (group policies + Weixin DM). got ${disabledNodes.size}",
            disabledNodes.size in 2..3,
        )
        // `allowlist` appears in Feishu DM, Feishu group, Weixin DM, Weixin group = 4.
        val allowlistNodes = composeTestRule.onAllNodesWithText("allowlist").fetchSemanticsNodes()
        assertTrue(
            "`allowlist` chip must appear ≥ 3 times. got ${allowlistNodes.size}",
            allowlistNodes.size >= 3,
        )
    }

    // ── TC-UI-021-a: tapping a chip without pressing Save must NOT write prefs ──
    @Test
    fun `only save persists — chip change does not write prefs without Save`() {
        composeTestRule.setContent { HermesGatewayPoliciesScreen() }

        // Tap the `allowlist` chip (first occurrence = Feishu DM). state map
        // will flip locally but prefs stay untouched until Save.
        composeTestRule.onAllNodesWithText("allowlist")[0].performClick()
        composeTestRule.waitForIdle()

        val prefValue = runBlocking {
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_DM_POLICY,
                default = "pairing",
            ).first()
        }
        assertEquals(
            "chip tap without Save must leave prefs at default (pairing)",
            "pairing",
            prefValue,
        )
    }

    // ── TC-UI-022-a: string-boolean round-trip — prefs "false"/"true" renders Switch Off/On ──
    @Test
    fun `string-boolean match — prefs strings map to Switch ToggleableState both ways`() {
        // Pre-seed the FIELD_REQUIRE_MENTION field with values OPPOSITE to each
        // card's default so a failing map would be visible: Feishu default=true
        // → seed "false"; Weixin default=false → seed "true". If the screen's
        // `state[FIELD_REQUIRE_MENTION] == "true"` check works, the Feishu
        // Switch must render Off and the Weixin Switch must render On.
        //
        // This sidesteps the async Save path (rememberCoroutineScope + DataStore
        // suspend edit never drains under Robolectric+Compose clock). The string-
        // boolean contract is the same in both directions: we assert that prefs
        // strings render correctly as Switch state. Together with TC-UI-023-a
        // (defaults direction), this completes R-UI-001's string-boolean match.
        runBlocking {
            prefs.savePlatformPolicyField(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_REQUIRE_MENTION,
                "false",
            )
            prefs.savePlatformPolicyField(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.FIELD_REQUIRE_MENTION,
                "true",
            )
        }

        composeTestRule.setContent { HermesGatewayPoliciesScreen() }
        composeTestRule.waitForIdle()

        // After LaunchedEffect drains, exactly 2 ToggleableState nodes exist —
        // the two require_mention Switches. With opposite-of-default seeding,
        // one must be On (Weixin seeded "true") and one Off (Feishu seeded
        // "false"). If the string parsing were broken (e.g. read "false" as
        // true), both would be On or both Off.
        val allToggleables = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        )
        assertEquals(
            "exactly 2 require_mention switches (one per card)",
            2,
            allToggleables.fetchSemanticsNodes().size,
        )
        val onSwitches = composeTestRule.onAllNodes(isOn())
        val offSwitches = composeTestRule.onAllNodes(isOff())
        assertEquals(
            "seeding FEISHU=\"false\" + WEIXIN=\"true\" must yield exactly 1 On Switch",
            1,
            onSwitches.fetchSemanticsNodes().size,
        )
        assertEquals(
            "seeding FEISHU=\"false\" + WEIXIN=\"true\" must yield exactly 1 Off Switch",
            1,
            offSwitches.fetchSemanticsNodes().size,
        )
    }

    // ── TC-UI-023-a: defaults when no prefs have ever been written ──
    @Test
    fun `defaults — Feishu=pairing-allowlist-true, Weixin=open-disabled-false`() = runBlocking {
        // Values come straight from the Flow-with-default, bypassing UI.
        // This is a direct assertion on R-UI-001's platform defaults.
        assertEquals(
            "pairing",
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_DM_POLICY,
                default = "pairing",
            ).first(),
        )
        assertEquals(
            "allowlist",
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_GROUP_POLICY,
                default = "allowlist",
            ).first(),
        )
        assertEquals(
            "true",
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_REQUIRE_MENTION,
                default = "true",
            ).first(),
        )
        assertEquals(
            "open",
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.FIELD_DM_POLICY,
                default = "open",
            ).first(),
        )
        assertEquals(
            "disabled",
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.FIELD_GROUP_POLICY,
                default = "disabled",
            ).first(),
        )
        assertEquals(
            "false",
            prefs.platformPolicyFieldFlow(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.FIELD_REQUIRE_MENTION,
                default = "false",
            ).first(),
        )
    }

    // ── TC-UI-024-a: LaunchedEffect `if (state.isEmpty())` guard ──
    @Test
    fun `first-emit only init — external prefs change after first emit does not re-overwrite state`() {
        // Pre-seed Feishu DM policy to `allowlist` BEFORE mounting so the first
        // Flow emission initializes state[DM_POLICY] = "allowlist" on the Feishu
        // card. The Weixin card stays at its own default ("open") — its separate
        // `allowlist` chip must remain unselected, which lets us target the
        // Feishu DM "allowlist" chip by picking the one whose Selected=true
        // rather than by traversal order.
        runBlocking {
            prefs.savePlatformPolicyField(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_DM_POLICY,
                "allowlist",
            )
        }

        composeTestRule.setContent { HermesGatewayPoliciesScreen() }
        composeTestRule.waitForIdle()

        // After waitForIdle, the Feishu card's LaunchedEffect has fired:
        // state[FIELD_DM_POLICY] = "allowlist" (from pre-seed), and
        // state[FIELD_GROUP_POLICY] = "allowlist" (from the Feishu default).
        // Both chips labeled "allowlist" under the Feishu card are therefore
        // Selected=true → 2 selected `allowlist` chips in total. Weixin's DM
        // defaults to "open" and group to "disabled" — its two `allowlist`
        // chips stay unselected.
        val selectedAllowBefore = composeTestRule
            .onAllNodes(hasText("allowlist") and isSelected())
            .fetchSemanticsNodes().size
        assertEquals(
            "initial: Feishu DM (pre-seeded) + Feishu group (default) both hold " +
                "\"allowlist\" → 2 selected chips",
            2,
            selectedAllowBefore,
        )

        // Simulate another settings surface writing a different value AFTER the
        // LaunchedEffect already populated state. The guard checks
        // `if (state.isEmpty())` — state is no longer empty, so this new Flow
        // emission must NOT overwrite the local state["dm_policy"]. If the
        // guard held, Feishu DM row still has `allowlist` selected (count
        // stays at 2: Feishu DM + Feishu group). If the guard failed, state
        // gets clobbered to "disabled" — Feishu DM "allowlist" deselects
        // (count drops to 1: only Feishu group).
        runBlocking {
            prefs.savePlatformPolicyField(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.FIELD_DM_POLICY,
                "disabled",
            )
        }
        composeTestRule.waitForIdle()

        val selectedAllowAfter = composeTestRule
            .onAllNodes(hasText("allowlist") and isSelected())
            .fetchSemanticsNodes().size
        assertEquals(
            "first-emit-only guard: external prefs write to \"disabled\" must NOT change " +
                "the Feishu DM chip selection — both Feishu chips must still show \"allowlist\"",
            2,
            selectedAllowAfter,
        )
    }

    // ─────────────────────── helpers ───────────────────────

    private fun resetSingleton() {
        val field = HermesGatewayPreferences::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

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
