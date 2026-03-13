package com.xiaomo.androidforclaw.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.xiaomo.androidforclaw.core.MyApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * 权限测试
 * 测试 AndroidForClaw 的权限管理
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PermissionUITest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    /**
     * 测试1: 应用有存储权限
     */
    @Test
    fun testStoragePermission_granted() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()

        val hasPermission = context.checkSelfPermission(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        assertTrue("应该有存储权限", hasPermission)
    }

    /**
     * 测试2: 工作空间目录存在
     */
    @Test
    fun testWorkspaceDirectory_exists() {
        val workspaceDir = java.io.File("/sdcard/.androidforclaw/workspace")

        // 验证工作空间目录可以创建
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }

        assertTrue("工作空间目录应该存在", workspaceDir.exists())
        assertTrue("应该是目录", workspaceDir.isDirectory)
    }

    /**
     * 测试3: Skills 目录存在
     */
    @Test
    fun testSkillsDirectory_exists() {
        val skillsDir = java.io.File("/sdcard/.androidforclaw/workspace/skills")

        if (!skillsDir.exists()) {
            skillsDir.mkdirs()
        }

        assertTrue("Skills目录应该存在", skillsDir.exists())
        assertTrue("应该是目录", skillsDir.isDirectory)
        assertTrue("应该可写", skillsDir.canWrite())
    }

    /**
     * 测试4: 配置目录存在
     */
    @Test
    fun testConfigDirectory_exists() {
        val configDir = java.io.File("/sdcard/.androidforclaw/config")

        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        assertTrue("配置目录应该存在", configDir.exists())
        assertTrue("应该是目录", configDir.isDirectory)
    }

    /**
     * 测试5: 可以创建测试文件
     */
    @Test
    fun testFileCreation_works() {
        val testFile = java.io.File("/sdcard/.androidforclaw/workspace/test.txt")

        try {
            testFile.writeText("Test content")

            assertTrue("测试文件应该存在", testFile.exists())
            assertEquals("内容应该匹配", "Test content", testFile.readText())

        } finally {
            // 清理
            testFile.delete()
        }
    }

    /**
     * 测试6: 可以读取 assets 中的 skills
     */
    @Test
    fun testAssetsSkills_accessible() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()

        try {
            val skillsDir = context.assets.list("skills")

            assertNotNull("Skills目录应该存在", skillsDir)
            assertTrue("应该有bundled skills", skillsDir!!.isNotEmpty())

        } catch (e: Exception) {
            fail("无法访问assets中的skills: ${e.message}")
        }
    }

    /**
     * 测试7: 应用包名正确
     */
    @Test
    fun testPackageName_correct() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()

        // Debug 版本包名会有 .debug 后缀
        assertTrue(
            "包名应该是基础包名或debug变体",
            context.packageName == "com.xiaomo.androidforclaw" ||
            context.packageName == "com.xiaomo.androidforclaw.debug"
        )
    }

    /**
     * 测试8: 应用版本可获取
     */
    @Test
    fun testAppVersion_retrievable() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        assertNotNull("版本名不应为空", packageInfo.versionName)
        assertTrue("版本号应该大于0", packageInfo.versionCode > 0)
    }

    /**
     * 测试9: MMKV 初始化
     */
    @Test
    fun testMMKV_initialized() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()

        try {
            val mmkv = com.tencent.mmkv.MMKV.defaultMMKV()

            assertNotNull("MMKV应该初始化", mmkv)

            // 测试写入读取
            mmkv.putString("test_key", "test_value")
            assertEquals("应该能读取", "test_value", mmkv.getString("test_key", ""))

            // 清理
            mmkv.remove("test_key")

        } catch (e: Exception) {
            fail("MMKV未正确初始化: ${e.message}")
        }
    }

    /**
     * 测试10: 外部存储可用
     */
    @Test
    fun testExternalStorage_available() {
        val state = android.os.Environment.getExternalStorageState()

        assertEquals(
            "外部存储应该可用",
            android.os.Environment.MEDIA_MOUNTED,
            state
        )

        val externalDir = android.os.Environment.getExternalStorageDirectory()
        assertTrue("外部存储目录应该存在", externalDir.exists())
        assertTrue("应该可读", externalDir.canRead())
    }
}
