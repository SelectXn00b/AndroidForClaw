package com.xiaomo.hermes.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import ai.openclaw.app.R
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.ui.chat.ChatSheetTestHelper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ChatThreadSelector UI 自动化测试
 *
 * 覆盖场景:
 * 1. 会话 chip 正常显示
 * 2. 点击切换会话
 * 3. 长按弹出删除确认对话框
 * 4. 确认删除触发回调
 * 5. 取消删除关闭对话框
 *
 * 运行:
 * adb shell am instrument -w -e class com.xiaomo.hermes.ui.ChatThreadSelectorUITest \
 *   com.xiaomo.hermes.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ChatThreadSelectorUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testSessions = listOf(
        ChatSessionEntry(key = "main", updatedAtMs = System.currentTimeMillis() - 1000L, displayName = "Main"),
        ChatSessionEntry(key = "session-2", updatedAtMs = System.currentTimeMillis() - 2000L, displayName = "Debug Session"),
        ChatSessionEntry(key = "session-3", updatedAtMs = System.currentTimeMillis() - 3000L, displayName = "Test Chat"),
    )

    private val res get() = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val deleteSessionTitle get() = res.getString(R.string.delete_session)
    private val actionDelete get() = res.getString(R.string.action_delete)
    private val actionCancel get() = res.getString(R.string.action_cancel)

    // ========================================================================
    // 1. 会话 chip 显示
    // ========================================================================

    @Test
    fun sessionsDisplayed() {
        composeTestRule.setContent {
            ChatSheetTestHelper.ChatThreadSelectorTest(
                sessionKey = "main",
                sessions = testSessions,
                mainSessionKey = "main",
            )
        }

        composeTestRule.onNodeWithText("Main", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Debug Session", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Chat", substring = true).assertIsDisplayed()
    }

    // ========================================================================
    // 2. 点击切换会话
    // ========================================================================

    @Test
    fun clickSwitchesSession() {
        var selectedKey = ""
        composeTestRule.setContent {
            ChatSheetTestHelper.ChatThreadSelectorTest(
                sessionKey = "main",
                sessions = testSessions,
                mainSessionKey = "main",
                onSelectSession = { selectedKey = it },
            )
        }

        composeTestRule.onNodeWithText("Debug Session", substring = true).performClick()
        composeTestRule.waitForIdle()

        assert(selectedKey == "session-2") {
            "Expected selected key 'session-2', got '$selectedKey'"
        }
    }

    // ========================================================================
    // 3. 长按弹出删除确认对话框
    // ========================================================================

    @Test
    fun longPressShowsDeleteDialog() {
        composeTestRule.setContent {
            ChatSheetTestHelper.ChatThreadSelectorTest(
                sessionKey = "main",
                sessions = testSessions,
                mainSessionKey = "main",
                onDeleteSession = {},
            )
        }

        composeTestRule.onNodeWithText("Debug Session", substring = true)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Should show delete confirmation dialog
        composeTestRule.onNodeWithText(deleteSessionTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(actionDelete).assertIsDisplayed()
        composeTestRule.onNodeWithText(actionCancel).assertIsDisplayed()
    }

    // ========================================================================
    // 4. 确认删除触发回调
    // ========================================================================

    @Test
    fun confirmDeleteTriggersCallback() {
        var deletedKey = ""
        composeTestRule.setContent {
            ChatSheetTestHelper.ChatThreadSelectorTest(
                sessionKey = "main",
                sessions = testSessions,
                mainSessionKey = "main",
                onDeleteSession = { deletedKey = it },
            )
        }

        // Long press to open dialog
        composeTestRule.onNodeWithText("Debug Session", substring = true)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Click delete button
        composeTestRule.onNodeWithText(actionDelete).performClick()
        composeTestRule.waitForIdle()

        assert(deletedKey == "session-2") {
            "Expected deleted key 'session-2', got '$deletedKey'"
        }

        // Dialog should be dismissed
        composeTestRule.onNodeWithText(deleteSessionTitle).assertDoesNotExist()
    }

    // ========================================================================
    // 5. 取消删除关闭对话框
    // ========================================================================

    @Test
    fun cancelDeleteDismissesDialog() {
        var deletedKey = ""
        composeTestRule.setContent {
            ChatSheetTestHelper.ChatThreadSelectorTest(
                sessionKey = "main",
                sessions = testSessions,
                mainSessionKey = "main",
                onDeleteSession = { deletedKey = it },
            )
        }

        // Long press to open dialog
        composeTestRule.onNodeWithText("Test Chat", substring = true)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Click cancel
        composeTestRule.onNodeWithText(actionCancel).performClick()
        composeTestRule.waitForIdle()

        // Dialog should be dismissed, no delete callback
        composeTestRule.onNodeWithText(deleteSessionTitle).assertDoesNotExist()
        assert(deletedKey.isEmpty()) {
            "Delete should not have been called, but got '$deletedKey'"
        }
    }

    // ========================================================================
    // 6. 无删除回调时长按不弹框
    // ========================================================================

    @Test
    fun longPressWithoutDeleteCallbackDoesNothing() {
        composeTestRule.setContent {
            ChatSheetTestHelper.ChatThreadSelectorTest(
                sessionKey = "main",
                sessions = testSessions,
                mainSessionKey = "main",
                onDeleteSession = null,
            )
        }

        composeTestRule.onNodeWithText("Debug Session", substring = true)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Should NOT show delete dialog
        composeTestRule.onNodeWithText(deleteSessionTitle).assertDoesNotExist()
    }
}
