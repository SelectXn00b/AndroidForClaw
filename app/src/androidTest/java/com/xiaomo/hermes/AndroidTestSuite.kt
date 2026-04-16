package com.xiaomo.hermes

import com.xiaomo.hermes.e2e.CameraE2ETest
import com.xiaomo.hermes.integration.AgentIntegrationTest
import com.xiaomo.hermes.ui.*
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Hermes UI 自动化测试套件
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
    PermissionUITest::class,
    ConfigActivityUITest::class,
    FloatingWindowUITest::class,
    ForClawMainTabsUITest::class,

    // E2E 测试
    CameraE2ETest::class,

    // 集成测试
    AgentIntegrationTest::class
)
class AndroidTestSuite
