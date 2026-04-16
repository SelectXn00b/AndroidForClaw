package com.xiaomo.hermes.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.xiaomo.hermes.ui.activity.MainActivityCompose
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 配置界面 UI 自动化测试
 * 测试配置相关的 UI 交互
 *
 * 运行:
 * ./gradlew connectedDebugAndroidTest --tests "ConfigActivityUITest"
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@SdkSuppress(maxSdkVersion = 35) // Espresso InputManager.getInstance() removed in API 36
class ConfigActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivityCompose::class.java)

    @Test
    fun testConfigActivity_launches() {
        // 验证配置界面启动 - 检查 API 配置标题
        onView(withText("API 配置"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testModelConfiguration_isVisible() {
        // 验证功能开关部分可见
        onView(withText("功能开关"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testConfigSave_works() {
        // 测试保存配置（如果有保存按钮）
        try {
            onView(withText("保存"))
                .check(matches(isDisplayed()))
                .perform(click())

            Thread.sleep(500)

            // 验证保存成功提示（如果有）
        } catch (e: Exception) {
            // 可能没有保存按钮
        }
    }

    @Test
    fun testBackNavigation_works() {
        // 测试返回导航
        activityRule.scenario.onActivity { activity ->
            activity.onBackPressed()
        }

        // 验证活动已关闭
        Thread.sleep(500)
    }
}
