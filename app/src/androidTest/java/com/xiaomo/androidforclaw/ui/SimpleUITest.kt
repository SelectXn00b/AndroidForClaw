package com.xiaomo.androidforclaw.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaomo.androidforclaw.core.MyApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * 简化的 UI 测试
 * 使用 UI Automator 进行基本的应用测试
 *
 * 运行:
 * ./gradlew connectedDebugAndroidTest --tests "SimpleUITest"
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SimpleUITest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext<MyApplication>()

        // 确保设备回到主屏幕
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun testAppLaunches() {
        // 启动应用
        val intent = context.packageManager.getLaunchIntentForPackage(
            context.packageName
        )?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        context.startActivity(intent)

        // 等待应用启动
        val launched = device.wait(
            Until.hasObject(By.pkg(context.packageName)),
            5000
        )

        assertTrue("应用应该启动", launched)
    }

    @Test
    fun testAppSurvivesBackground() {
        // 启动应用
        launchApp()
        Thread.sleep(1000)

        // 按 Home 键
        device.pressHome()
        device.waitForIdle()
        Thread.sleep(1000)

        // 重新启动应用
        launchApp()
        Thread.sleep(1000)

        // 验证应用仍然运行
        val app = device.findObject(By.pkg(context.packageName))
        assertNotNull("应用应该仍然可用", app)
    }

    @Test
    fun testAppHandlesRotation() {
        // 启动应用
        launchApp()
        Thread.sleep(1000)

        try {
            // 旋转屏幕
            device.setOrientationLeft()
            device.waitForIdle()
            Thread.sleep(500)

            // 验证应用仍然可见
            val app = device.findObject(By.pkg(context.packageName))
            assertNotNull("旋转后应用应该仍然可见", app)

            // 恢复竖屏
            device.setOrientationNatural()
            device.waitForIdle()

        } catch (e: Exception) {
            // 某些设备可能不支持旋转
        }
    }

    @Test
    fun testDeviceProperties() {
        assertTrue("屏幕宽度应该 > 0", device.displayWidth > 0)
        assertTrue("屏幕高度应该 > 0", device.displayHeight > 0)
    }

    @Test
    fun testContextAvailable() {
        assertNotNull("Context 应该可用", context)
        assertEquals("包名应该正确", "com.xiaomo.androidforclaw.debug", context.packageName)
    }

    private fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(
            context.packageName
        )?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(context.packageName)), 5000)
        device.waitForIdle()
    }
}
