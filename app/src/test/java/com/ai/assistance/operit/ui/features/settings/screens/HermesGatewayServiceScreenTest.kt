package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayController
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.services.gateway.GatewayForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [HermesGatewayServiceScreen] — covers TC-UI-040 through
 * TC-UI-043 from docs/hermes-test-cases.md.
 *
 * The screen wires two Switches to [HermesGatewayPreferences] and
 * dispatches [GatewayForegroundService] start/stop intents for the
 * run-switch. Three observables round out the surface:
 *  - `serviceEnabledFlow` (DataStore-backed)
 *  - `autoStartOnBootFlow` (DataStore-backed, no service side-effect)
 *  - `HermesGatewayController.status` / `.error` (MutableStateFlows)
 *
 * We reset the preferences singletons + DataStore delegate per test
 * (mirrors the other screen tests). For controller state we poke the
 * singleton's private `_status` / `_error` MutableStateFlows directly
 * via reflection — this lets us assert the status-label mapping and
 * error-bar visibility without standing up a real `GatewayRunner`.
 *
 * Service-intent dispatch is observed via Robolectric's
 * ShadowApplication queue (`peekNextStartedService()`). The suspend
 * DataStore edit inside the onCheckedChange launch does not reliably
 * drain under the Compose test clock — TC-UI-040/041 therefore assert
 * the *observable side-effect* (the service intent) instead of the
 * prefs write; TC-UI-041 asserts the absence of any service intent to
 * prove the autostart toggle is prefs-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayServiceScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: HermesGatewayPreferences
    private lateinit var controller: HermesGatewayController

    @Before
    fun setUp() {
        resetPrefsSingleton()
        resetDataStoreDelegate()
        resetControllerSingleton()
        context.getSharedPreferences("hermes_gateway_secrets_plain", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val dataStoreFile = java.io.File(
            context.filesDir.parentFile,
            "datastore/hermes_gateway_preferences.preferences_pb",
        )
        if (dataStoreFile.exists()) dataStoreFile.delete()
        prefs = HermesGatewayPreferences.getInstance(context)
        controller = HermesGatewayController.getInstance(context)
        // Drain any service-start intents from previous tests (defensive:
        // Robolectric reuses a process-wide Application, but we pop
        // until empty before each test so state is known-clean).
        drainServiceIntents()
    }

    @After
    fun tearDown() {
        resetPrefsSingleton()
        resetControllerSingleton()
    }

    // ── TC-UI-040-a: run-switch ON → service start Intent dispatched ──
    @Test
    fun `run switch double write — flipping on dispatches a GatewayForegroundService start intent`() {
        composeTestRule.setContent { HermesGatewayServiceScreen() }
        composeTestRule.waitForIdle()

        // Two Switches — run (index 0) and autostart (index 1). Both Off
        // initially (defaults false).
        val switches = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        )
        assertEquals(
            "exactly 2 switches (run + autostart)",
            2,
            switches.fetchSemanticsNodes().size,
        )

        switches[0].performClick()
        composeTestRule.waitForIdle()

        // The onCheckedChange lambda dispatches `GatewayForegroundService.start(ctx)`
        // in parallel with the suspend prefs write. The service intent is
        // observable via Robolectric's shadow application queue.
        val started = shadowOf(context as Application).nextStartedService
        assertNotNull(
            "flipping run switch On must dispatch a service-start Intent",
            started,
        )
        assertEquals(
            "intent must target GatewayForegroundService",
            GatewayForegroundService::class.java.name,
            started.component?.className,
        )
        assertEquals(
            "start intent action must be ACTION_START",
            GatewayForegroundService.ACTION_START,
            started.action,
        )
    }

    // ── TC-UI-040-b: run-switch OFF (from on) → service stop Intent ──
    @Test
    fun `run switch off — flipping run switch off dispatches a stop intent`() {
        // Pre-seed serviceEnabled=true so the Switch renders On at mount.
        runBlocking { prefs.saveServiceEnabled(true) }

        composeTestRule.setContent { HermesGatewayServiceScreen() }
        composeTestRule.waitForIdle()

        val switches = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        )
        // Drain any intent produced during setContent's LaunchedEffect path.
        drainServiceIntents()

        switches[0].performClick() // Run switch On -> Off
        composeTestRule.waitForIdle()

        val stopped = shadowOf(context as Application).nextStartedService
        assertNotNull(
            "flipping run switch Off must dispatch a service-stop Intent",
            stopped,
        )
        assertEquals(
            GatewayForegroundService::class.java.name,
            stopped.component?.className,
        )
        assertEquals(
            "stop intent action must be ACTION_STOP",
            GatewayForegroundService.ACTION_STOP,
            stopped.action,
        )
    }

    // ── TC-UI-041-a: autostart toggle is prefs-only; no service intent ──
    @Test
    fun `autostart pref only — flipping autostart does not dispatch any service intent`() {
        composeTestRule.setContent { HermesGatewayServiceScreen() }
        composeTestRule.waitForIdle()
        drainServiceIntents() // baseline clean

        val switches = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        )
        switches[1].performClick() // autostart switch (index 1)
        composeTestRule.waitForIdle()

        val leaked = shadowOf(context as Application).nextStartedService
        assertNull(
            "autostart onCheckedChange only writes prefs — no service intent must be dispatched",
            leaked,
        )
    }

    // ── TC-UI-042-a: Status.RUNNING maps to "运行中" / "Running" label ──
    @Test
    fun `status RUNNING mapped — controller state flows into status label`() {
        setControllerStatus(HermesGatewayController.Status.RUNNING)

        composeTestRule.setContent { HermesGatewayServiceScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_service_status_running),
        ).assertIsDisplayed()
    }

    // ── TC-UI-042-b: Status.FAILED maps to "启动失败" / "Failed to start" ──
    @Test
    fun `status FAILED mapped — failed status renders the failed label`() {
        setControllerStatus(HermesGatewayController.Status.FAILED)

        composeTestRule.setContent { HermesGatewayServiceScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            context.getString(R.string.hermes_service_status_failed),
        ).assertIsDisplayed()
    }

    // ── TC-UI-043-a: error != null → error bar rendered ──
    @Test
    fun `error bar visible — non-null error message renders in status card`() {
        setControllerError("unit-test error line")

        composeTestRule.setContent { HermesGatewayServiceScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("unit-test error line").assertIsDisplayed()
    }

    // ─────────────────────── helpers ───────────────────────

    private fun resetPrefsSingleton() {
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

    private fun resetControllerSingleton() {
        val field = HermesGatewayController::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    /** Drain any started service Intents the ShadowApplication is holding. */
    private fun drainServiceIntents() {
        val app = context as Application
        while (shadowOf(app).nextStartedService != null) { /* drain */ }
    }

    /** Poke the controller's MutableStateFlow<Status> directly. */
    @Suppress("UNCHECKED_CAST")
    private fun setControllerStatus(status: HermesGatewayController.Status) {
        val f = HermesGatewayController::class.java.getDeclaredField("_status")
        f.isAccessible = true
        (f.get(controller) as MutableStateFlow<HermesGatewayController.Status>).value = status
    }

    @Suppress("UNCHECKED_CAST")
    private fun setControllerError(message: String?) {
        val f = HermesGatewayController::class.java.getDeclaredField("_error")
        f.isAccessible = true
        (f.get(controller) as MutableStateFlow<String?>).value = message
    }
}
