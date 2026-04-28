package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HermesSettingsScreen] — covers TC-UI-001 through TC-UI-005 from
 * docs/hermes-test-cases.md.
 *
 * The hub is a purely presentational composable: 5 `HermesSubScreenCard`s,
 * each with a `stringResource` title/subtitle/icon and a navigation callback.
 * The tests verify:
 *   001 — all 5 tiles render (title visible)
 *   002 — all 5 tiles render their subtitle (proxy for full-card-rendered —
 *          icons use `contentDescription = null` so they're not semantically
 *          queryable; a visible subtitle on every tile proves layout reached
 *          the Row that contains the icon)
 *   003 — tapping each tile invokes exactly its own callback
 *   004 — mounting alone triggers zero callbacks (no side-effects)
 *   005 — rendering under a narrow constraint still succeeds (titles configured
 *          `maxLines = 1 / overflow = Ellipsis` in the production source)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesSettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // ── TC-UI-001-a: hub renders 5 tiles ──
    @Test
    fun `five tiles visible — every title rendered`() {
        composeTestRule.setContent {
            HermesSettingsScreen(
                navigateToCredentials = {},
                navigateToPolicies = {},
                navigateToAgentParams = {},
                navigateToGatewayService = {},
                navigateToQrBind = {},
            )
        }
        listOf(
            R.string.screen_title_hermes_gateway_credentials,
            R.string.screen_title_hermes_gateway_policies,
            R.string.screen_title_hermes_agent_params,
            R.string.screen_title_hermes_gateway_service,
            R.string.screen_title_hermes_gateway_qr_bind,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(context.getString(resId)).assertIsDisplayed()
        }
    }

    // ── TC-UI-002-a: each tile renders its icon/subtitle pair ──
    @Test
    fun `tile icons — subtitles visible proving icon-row reached`() {
        composeTestRule.setContent {
            HermesSettingsScreen(
                navigateToCredentials = {},
                navigateToPolicies = {},
                navigateToAgentParams = {},
                navigateToGatewayService = {},
                navigateToQrBind = {},
            )
        }
        // Icons have `contentDescription = null` (decorative), so the icon
        // tree is not directly queryable. Each tile's subtitle sits in the
        // same Row as its icon — if the subtitle is displayed, the Row
        // containing the icon reached layout. One subtitle per tile, paired
        // to the icon in production source (§HermesSettingsScreen.kt L62-86).
        listOf(
            R.string.hermes_settings_gateway_credentials_subtitle,
            R.string.hermes_settings_gateway_policies_subtitle,
            R.string.hermes_settings_agent_params_subtitle,
            R.string.hermes_settings_gateway_service_subtitle,
            R.string.hermes_settings_gateway_qr_bind_subtitle,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(context.getString(resId)).assertIsDisplayed()
        }
    }

    // ── TC-UI-003-a: click dispatches to the correct navigation lambda ──
    @Test
    fun `tile click triggers callback — each tap routes to its own lambda`() {
        val taps = mutableMapOf<String, Int>().withDefault { 0 }
        fun record(key: String) { taps[key] = taps.getValue(key) + 1 }

        composeTestRule.setContent {
            HermesSettingsScreen(
                navigateToCredentials = { record("credentials") },
                navigateToPolicies = { record("policies") },
                navigateToAgentParams = { record("agentParams") },
                navigateToGatewayService = { record("service") },
                navigateToQrBind = { record("qr") },
            )
        }

        val expectations = listOf(
            R.string.screen_title_hermes_gateway_credentials to "credentials",
            R.string.screen_title_hermes_gateway_policies to "policies",
            R.string.screen_title_hermes_agent_params to "agentParams",
            R.string.screen_title_hermes_gateway_service to "service",
            R.string.screen_title_hermes_gateway_qr_bind to "qr",
        )
        expectations.forEach { (resId, key) ->
            composeTestRule.onNodeWithText(context.getString(resId)).performClick()
            assertEquals(
                "tapping '${context.getString(resId)}' must call navigateTo$key exactly once",
                1,
                taps.getValue(key),
            )
        }
        // Sum == 5 rules out any tap spilling into another lambda.
        assertEquals("total callbacks == number of taps", 5, taps.values.sum())
    }

    // ── TC-UI-004-a: mounting has no side-effect — no callback fires ──
    @Test
    fun `no side effect on open — zero callbacks without tap`() {
        var any = false
        composeTestRule.setContent {
            HermesSettingsScreen(
                navigateToCredentials = { any = true },
                navigateToPolicies = { any = true },
                navigateToAgentParams = { any = true },
                navigateToGatewayService = { any = true },
                navigateToQrBind = { any = true },
            )
        }
        // Force composition + measurement without interaction.
        composeTestRule.onNodeWithText(
            context.getString(R.string.screen_title_hermes_gateway_credentials),
        ).assertIsDisplayed()
        assertFalse("mounting must not fire any navigation callback", any)
    }

    // ── TC-UI-005-a: narrow constraint still displays titles (ellipsis in production) ──
    @Test
    fun `tile text ellipsis — narrow width does not crash, titles still present`() {
        composeTestRule.setContent {
            // 120.dp is narrower than the typical title's ideal width and forces
            // the production `maxLines = 1 / overflow = Ellipsis` path on the
            // title Text. If ellipsis were not configured, layout would either
            // wrap (failing maxLines) or the assertion below would still find
            // the node — this test is primarily a no-crash guard that proves
            // the composable is robust to narrow measurement.
            Box(Modifier.width(120.dp)) {
                HermesSettingsScreen(
                    navigateToCredentials = {},
                    navigateToPolicies = {},
                    navigateToAgentParams = {},
                    navigateToGatewayService = {},
                    navigateToQrBind = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            context.getString(R.string.screen_title_hermes_gateway_credentials),
        ).assertExists()
    }
}
