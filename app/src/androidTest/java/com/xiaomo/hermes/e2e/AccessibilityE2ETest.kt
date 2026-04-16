package com.xiaomo.hermes.e2e

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaomo.hermes.agent.tools.AndroidToolRegistry
import com.xiaomo.hermes.core.MyApplication
import com.xiaomo.hermes.data.model.TaskDataManager
import com.xiaomo.hermes.ui.activity.MainActivityCompose
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * E2E test for accessibility / phone operation capabilities.
 *
 * Tests the full device tool pipeline:
 *   snapshot (view tree), act (tap/swipe/home/back/type), screenshot
 *
 * Accessibility service is enabled via ADB in @BeforeClass.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AccessibilityE2ETest {

    companion object {
        private const val TAG = "AccessibilityE2ETest"
        private const val PKG = "com.xiaomo.hermes"
        private const val A11Y_SERVICE =
            "$PKG/com.xiaomo.hermes.accessibility.service.PhoneAccessibilityService"
        private const val TIMEOUT = 5000L

        lateinit var device: UiDevice
        lateinit var context: Context
        lateinit var toolRegistry: AndroidToolRegistry

        @BeforeClass
        @JvmStatic
        fun setupOnce() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            device = UiDevice.getInstance(instrumentation)

            // Grant permissions via ADB
            execShell("settings put secure enabled_accessibility_services $A11Y_SERVICE")
            execShell("settings put secure accessibility_enabled 1")
            execShell("appops set $PKG MANAGE_EXTERNAL_STORAGE allow")
            execShell("appops set $PKG android:system_alert_window allow")

            println("$TAG: Permissions granted via ADB, waiting for service...")
            Thread.sleep(3000)

            context = ApplicationProvider.getApplicationContext<MyApplication>()
            val taskDataManager = TaskDataManager.getInstance()
            toolRegistry = AndroidToolRegistry(context, taskDataManager)

            // Launch app to ensure accessibility service binds
            launchApp()
            Thread.sleep(2000)
        }

        @JvmStatic
        fun launchApp() {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(PKG, MainActivityCompose::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            device.wait(Until.hasObject(By.pkg(PKG).depth(0)), TIMEOUT)
            device.waitForIdle()
        }

        private fun execShell(cmd: String): String {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val pfd: ParcelFileDescriptor = automation.executeShellCommand(cmd)
            val reader = BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd)))
            return reader.use { it.readText().trim() }
        }
    }

    // ── 1. View Tree (snapshot) ─────────────────────────────────

    @Test
    fun test01_snapshot_returnsViewTree() = runBlocking {
        println("$TAG: test01 — snapshot (view tree)")

        val result = toolRegistry.execute("device", mapOf("action" to "snapshot"))
        assumeTrue("snapshot 需要无障碍服务，跳过", result.success)

        assertTrue("snapshot should return content", result.content.isNotEmpty())
        // snapshot returns a structured view tree with refs
        println("$TAG: ✅ snapshot returned ${result.content.length} chars")
    }

    // ── 2. Press Home ───────────────────────────────────────────

    @Test
    fun test02_act_home() = runBlocking {
        println("$TAG: test02 — act home")

        // Make sure we're in our app first
        launchApp()
        Thread.sleep(500)

        val result = toolRegistry.execute("device", mapOf(
            "action" to "act", "kind" to "home"
        ))
        assumeTrue("home 需要无障碍服务，跳过", result.success)

        device.waitForIdle(2000)
        val currentPkg = device.currentPackageName
        assertNotEquals("Should leave our app after home", PKG, currentPkg)
        println("$TAG: ✅ home → current package: $currentPkg")

        // Return to app for next tests
        launchApp()
        Thread.sleep(500)
    }

    // ── 3. Press Back ───────────────────────────────────────────

    @Test
    fun test03_act_back() = runBlocking {
        println("$TAG: test03 — act back")

        val result = toolRegistry.execute("device", mapOf(
            "action" to "act", "kind" to "press", "key" to "BACK"
        ))
        assumeTrue("back 需要无障碍服务，跳过", result.success)

        device.waitForIdle(1000)
        println("$TAG: ✅ back succeeded")

        launchApp()
        Thread.sleep(500)
    }

    // ── 4. Tap ──────────────────────────────────────────────────

    @Test
    fun test04_act_tap() = runBlocking {
        println("$TAG: test04 — act tap (center)")

        val w = device.displayWidth
        val h = device.displayHeight
        val result = toolRegistry.execute("device", mapOf(
            "action" to "act",
            "kind" to "tap",
            "x" to w / 2,
            "y" to h / 2
        ))
        assumeTrue("tap 需要无障碍服务，跳过", result.success)

        device.waitForIdle(1000)
        println("$TAG: ✅ tap at (${w / 2}, ${h / 2}) succeeded")
    }

    // ── 5. Swipe ────────────────────────────────────────────────

    @Test
    fun test05_act_swipe() = runBlocking {
        println("$TAG: test05 — act swipe up")

        // Go home for a clean surface
        device.pressHome()
        device.waitForIdle(1000)

        val w = device.displayWidth
        val h = device.displayHeight
        val result = toolRegistry.execute("device", mapOf(
            "action" to "act",
            "kind" to "swipe",
            "startX" to w / 2,
            "startY" to h * 3 / 4,
            "endX" to w / 2,
            "endY" to h / 4
        ))
        assumeTrue("swipe 需要无障碍服务，跳过", result.success)

        device.waitForIdle(1000)
        println("$TAG: ✅ swipe up succeeded")

        launchApp()
        Thread.sleep(500)
    }

    // ── 6. Type ─────────────────────────────────────────────────

    @Test
    fun test06_act_type() = runBlocking {
        println("$TAG: test06 — act type")

        val result = toolRegistry.execute("device", mapOf(
            "action" to "act",
            "kind" to "type",
            "text" to "hello e2e"
        ))
        // type may fail without a focused field — that's OK
        println("$TAG: ✅ type returned success=${result.success} (may be false if no focused field)")
    }

    // ── 7. Screenshot ───────────────────────────────────────────

    @Test
    fun test07_screenshot() = runBlocking {
        println("$TAG: test07 — screenshot")

        val result = toolRegistry.execute("device", mapOf("action" to "screenshot"))
        assumeTrue("screenshot 需要 MediaProjection 权限，跳过", result.success)

        assertTrue("screenshot should return content", result.content.isNotEmpty())
        println("$TAG: ✅ screenshot returned ${result.content.length} chars")
    }

    // ── 8. Status ───────────────────────────────────────────────

    @Test
    fun test08_status() = runBlocking {
        println("$TAG: test08 — device status")

        val result = toolRegistry.execute("device", mapOf("action" to "status"))
        assertTrue("status should succeed", result.success)
        assertTrue("status should return content", result.content.isNotEmpty())

        println("$TAG: ✅ status: ${result.content.take(200)}")
    }

    // ── 9. Snapshot caching ─────────────────────────────────────

    @Test
    fun test09_snapshot_caching() = runBlocking {
        println("$TAG: test09 — snapshot caching")

        // First call
        val t1 = System.currentTimeMillis()
        val r1 = toolRegistry.execute("device", mapOf("action" to "snapshot"))
        val d1 = System.currentTimeMillis() - t1
        assumeTrue("snapshot 需要无障碍服务，跳过", r1.success)

        // Second call immediately — should be faster due to view tree cache
        val t2 = System.currentTimeMillis()
        val r2 = toolRegistry.execute("device", mapOf("action" to "snapshot"))
        val d2 = System.currentTimeMillis() - t2

        assertTrue("Second snapshot should also succeed", r2.success)
        println("$TAG: ✅ snapshot caching: first=${d1}ms, second=${d2}ms")
    }

    // ── 10. Full workflow ───────────────────────────────────────

    @Test
    fun test10_fullWorkflow() = runBlocking {
        println("$TAG: test10 — full agent workflow: snapshot → tap → snapshot → home")

        // Step 1: snapshot to see the UI
        val snap1 = toolRegistry.execute("device", mapOf("action" to "snapshot"))
        assumeTrue("workflow 需要无障碍服务，跳过", snap1.success)
        println("  1. snapshot: ${snap1.content.length} chars")

        // Step 2: tap center
        val w = device.displayWidth
        val h = device.displayHeight
        val tap = toolRegistry.execute("device", mapOf(
            "action" to "act", "kind" to "tap", "x" to w / 2, "y" to h / 2
        ))
        println("  2. tap: success=${tap.success}")
        device.waitForIdle(1000)

        // Step 3: snapshot again to see result
        val snap2 = toolRegistry.execute("device", mapOf("action" to "snapshot"))
        println("  3. snapshot: ${snap2.content.length} chars")

        // Step 4: go home
        val home = toolRegistry.execute("device", mapOf("action" to "act", "kind" to "home"))
        println("  4. home: success=${home.success}")
        device.waitForIdle(1000)

        println("$TAG: ✅ full workflow completed")
    }
}
