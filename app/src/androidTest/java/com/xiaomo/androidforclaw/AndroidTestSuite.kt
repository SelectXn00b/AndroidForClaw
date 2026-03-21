package com.xiaomo.androidforclaw

import com.xiaomo.androidforclaw.integration.AgentIntegrationTest
import com.xiaomo.androidforclaw.ui.*
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * AndroidForClaw UI 自动化测试套件
 *
 * 包含所有 UI 和集成测试
 *
 * 运行所有测试:
 * ./gradlew connectedDebugAndroidTest
 *
 * 运行此测试套件:
 * ./gradlew connectedDebugAndroidTest --tests "AndroidTestSuite"
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // UI 测试
    SimpleUITest::class,
    PermissionUITest::class,
    ConfigActivityUITest::class,
    FloatingWindowUITest::class,
    ForClawMainTabsUITest::class,
    ChatScreenUITest::class,

    // 集成测试
    AgentIntegrationTest::class
)
class AndroidTestSuite
