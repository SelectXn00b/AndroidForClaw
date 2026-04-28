package com.ai.assistance.operit.hermes.gateway

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xiaomo.hermes.hermes.initHermesConstants
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed tests for [HermesGatewayController] — covers the
 * subset of TCs from docs/hermes-test-cases.md that needs a real Android
 * Context (filesDir / DataStore / EncryptedSharedPreferences):
 *
 *  - TC-CONFIG-021-a: start() with no enabled platforms → Status.FAILED
 *
 * The remaining deferred TCs (020 happy-path, 023 stop-throws,
 * 026 empty-reply-fallback, 027 persist-swallow) either need a live
 * GatewayRunner or the full EnhancedAIService / ChatHistoryManager
 * singletons — out of scope for a unit surface. Kept in the docs with
 * explicit deferral notes.
 *
 * The pure-logic subset lives in [HermesGatewayControllerTest] and runs
 * without Robolectric for speed; this class only carries the paths that
 * actually reach `HermesGatewayConfigBuilder.build(context)` →
 * `getHermesHome()` → `getAppContext().filesDir`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayControllerRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Reset singletons + on-disk DataStore so fresh prefs are seen.
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
        // getHermesHome() is called inside ConfigBuilder.build — needs
        // the module's global app context primed.
        initHermesConstants(context)
    }

    @After
    fun tearDown() {
        resetPrefsSingleton()
        resetControllerSingleton()
    }

    // ── TC-CONFIG-021-a: empty platforms → Status.FAILED + error set ──
    @Test
    fun `empty platforms fails — start with no credentials flips to FAILED with error`() =
        runBlocking {
            val ctl = HermesGatewayController.getInstance(context)
            // Sanity: freshly constructed controller starts at STOPPED.
            assertEquals(HermesGatewayController.Status.STOPPED, ctl.status.value)

            val result = ctl.start()

            // With no secrets and no platformEnabled flags set, both
            // buildFeishu() and buildWeixin() return null; the resulting
            // GatewayConfig has platforms={}, enabledPlatforms=∅. The
            // controller short-circuits in the `config.enabledPlatforms.isEmpty()`
            // branch — Status.FAILED, error set, returns false.
            assertFalse("start() must return false on empty-platforms path", result)
            assertEquals(
                "status must land on FAILED (not STOPPED/STARTING/RUNNING)",
                HermesGatewayController.Status.FAILED,
                ctl.status.value,
            )
            val err = ctl.error.value
            assertNotNull("errorMessage must be populated on FAILED path", err)
            assertTrue(
                "error must describe the empty-platforms cause, got: $err",
                err!!.contains("no enabled platforms", ignoreCase = true) ||
                    err.contains("credentials", ignoreCase = true),
            )
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
}
