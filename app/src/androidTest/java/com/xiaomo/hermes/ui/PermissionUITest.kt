package com.xiaomo.hermes.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomo.hermes.core.MyApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * 权限测试
 * 测试 Hermes 的权限管理
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PermissionUITest {

    @Before
    fun grantStoragePermission() {
        // API 30+ needs MANAGE_EXTERNAL_STORAGE for /sdcard/ access
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
            .close()
    }

    /**
     * 测试1: 应用有存储权限
     * API 30+ uses MANAGE_EXTERNAL_STORAGE (granted via appops in @Before)
     * API 29- uses WRITE_EXTERNAL_STORAGE
     */
    @Test
    fun testStoragePermission_granted() {
        val context = ApplicationProvider.getApplicationContext<MyApplication>()

        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= 30) {
            // On API 30+, MANAGE_EXTERNAL_STORAGE is granted via appops
            android.os.Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        assertTrue("应该有存储权限", hasPermission)
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

        // Debug 和 Release 统一使用相同包名
        assertEquals(
            "包名应该正确",
            "com.xiaomo.hermes",
            context.packageName
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
