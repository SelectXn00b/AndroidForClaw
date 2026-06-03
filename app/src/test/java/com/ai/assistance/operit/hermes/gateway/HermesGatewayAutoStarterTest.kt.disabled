package com.ai.assistance.operit.hermes.gateway

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.operit.services.gateway.GatewayForegroundService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Bug 1 — Gateway 不会在应用前台时自恢复前台服务（用户开启后冷启动失效）。
 *
 * 这些测试覆盖 docs/hermes-test-cases.md 中的：
 *   TC-GW-220-a — serviceEnabledFlow=true 时，调用 GatewayForegroundService.start(ctx)
 *   TC-GW-220-b — serviceEnabledFlow=false 时，不调 start
 *   TC-GW-220-c — controller.status==RUNNING 时，不重复 start（去重）
 *   TC-GW-220-d — 重入保护：同一时间只有一次 ensure 真正去读 prefs+发 intent
 *   TC-GW-223-a — autoStartOnBootFlow 仅控 BootReceiver，autoStarter 不读它
 *
 * 红的预期：HermesGatewayAutoStarter 还没实现，编译失败 ⇒ 红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayAutoStarterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetPrefSingleton()
        resetDataStoreDelegate()
        // Wipe the on-disk DataStore between tests.
        val dataStoreFile = java.io.File(
            context.filesDir.parentFile,
            "datastore/hermes_gateway_preferences.preferences_pb",
        )
        if (dataStoreFile.exists()) dataStoreFile.delete()
        // Drain any leftover started services from a previous run.
        val app = shadowOf(context as Application)
        while (app.peekNextStartedService() != null) {
            app.getNextStartedService()
        }
        // Reset the autostarter's reentrancy guard.
        resetAutoStarterGuard()
    }

    @After
    fun tearDown() {
        resetPrefSingleton()
    }

    // ── TC-GW-220-a ──────────────────────────────────────────────
    @Test
    fun `starts when enabled — service ACTION_START intent dispatched`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveServiceEnabled(true)

        HermesGatewayAutoStarter.ensureRunningIfEnabledBlocking(context, "test_starts_when_enabled")

        val started = drainStartedServices()
        assertEquals(
            "Expected exactly one ACTION_START intent for GatewayForegroundService",
            1,
            started.size,
        )
        val intent = started.single()
        assertEquals(GatewayForegroundService.ACTION_START, intent.action)
        assertEquals(
            ComponentName(context, GatewayForegroundService::class.java),
            intent.component,
        )
    }

    // ── TC-GW-220-b ──────────────────────────────────────────────
    @Test
    fun `skips when disabled — no service intent dispatched`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveServiceEnabled(false)

        HermesGatewayAutoStarter.ensureRunningIfEnabledBlocking(context, "test_skips_when_disabled")

        assertNull(
            "Disabled gateway must NOT trigger any foreground service start",
            shadowOf(context as Application).peekNextStartedService(),
        )
    }

    // ── TC-GW-220-c ──────────────────────────────────────────────
    @Test
    fun `noop when already running — RUNNING status skips start`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveServiceEnabled(true)

        // Simulate the controller already in RUNNING status.
        forceControllerStatus(HermesGatewayController.Status.RUNNING)

        HermesGatewayAutoStarter.ensureRunningIfEnabledBlocking(context, "test_noop_when_running")

        assertNull(
            "Already-running gateway must NOT receive a duplicate ACTION_START",
            shadowOf(context as Application).peekNextStartedService(),
        )
    }

    // ── TC-GW-220-d ──────────────────────────────────────────────
    @Test
    fun `reentrancy guard — second concurrent ensure is dropped`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        prefs.saveServiceEnabled(true)

        // Hold the in-flight latch by toggling ensureInProgress to true.
        forceAutoStarterGuard(true)
        try {
            HermesGatewayAutoStarter.ensureRunningIfEnabledBlocking(
                context, "test_reentrancy_dropped",
            )
        } finally {
            forceAutoStarterGuard(false)
        }

        assertNull(
            "Reentrant ensure call must be skipped while another is in progress",
            shadowOf(context as Application).peekNextStartedService(),
        )
    }

    // ── TC-GW-223-a ──────────────────────────────────────────────
    @Test
    fun `does not read autoStartOnBoot — only serviceEnabledFlow gates the start`() = runBlocking {
        val prefs = HermesGatewayPreferences.getInstance(context)
        // serviceEnabled=true, autoStartOnBoot=false ⇒ must still start (the
        // boot flag is only for BootReceiver, not for foreground autostart).
        prefs.saveServiceEnabled(true)
        prefs.saveAutoStartOnBoot(false)

        HermesGatewayAutoStarter.ensureRunningIfEnabledBlocking(context, "test_ignores_autoStartOnBoot")

        val started = drainStartedServices()
        assertEquals(
            "autoStartOnBoot=false must NOT block the foreground autostarter",
            1,
            started.size,
        )
        assertEquals(GatewayForegroundService.ACTION_START, started.single().action)
    }

    // ─────────────────────── helpers ───────────────────────

    private fun drainStartedServices(): List<Intent> {
        val app = shadowOf(context as Application)
        val out = mutableListOf<Intent>()
        while (true) {
            val next = app.getNextStartedService() ?: break
            // Filter to only the gateway service to avoid noise from any
            // other auto-starts that may have been triggered by Robolectric.
            if (next.component?.className == GatewayForegroundService::class.java.name) {
                out.add(next)
            }
        }
        return out
    }

    private fun resetPrefSingleton() {
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

    /** Force the autostarter's reentrancy AtomicBoolean to a known value. */
    private fun forceAutoStarterGuard(inProgress: Boolean) {
        val field = HermesGatewayAutoStarter::class.java.getDeclaredField("ensureInProgress")
        field.isAccessible = true
        val flag = field.get(HermesGatewayAutoStarter) as java.util.concurrent.atomic.AtomicBoolean
        flag.set(inProgress)
    }

    private fun resetAutoStarterGuard() = forceAutoStarterGuard(false)

    /**
     * Force the [HermesGatewayController] singleton's `_status` MutableStateFlow
     * to a chosen value.  Lets us test "skip if RUNNING" without needing to
     * actually start a real GatewayRunner (which requires platform credentials).
     */
    private fun forceControllerStatus(status: HermesGatewayController.Status) {
        val controller = HermesGatewayController.getInstance(context)
        assertNotNull(controller)
        val field = HermesGatewayController::class.java.getDeclaredField("_status")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(controller)
            as kotlinx.coroutines.flow.MutableStateFlow<HermesGatewayController.Status>
        state.value = status
    }
}
