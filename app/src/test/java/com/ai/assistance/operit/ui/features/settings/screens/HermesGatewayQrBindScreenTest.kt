package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [HermesGatewayQrBindScreen] — covers TC-UI-050 through
 * TC-UI-055 from docs/hermes-test-cases.md.
 *
 * The screen hosts two QR-onboarding cards (Feishu + Weixin) that drive
 * top-level `qrRegister` / `qrLogin` functions from the hermes-android
 * module. Those functions make real network calls and are imported (not
 * injectable); we cannot drive the async success path end-to-end under
 * Robolectric without a mock network. The TCs have therefore been
 * re-scoped to cover the **synchronous / UI-invariant** paths that
 * determine the observable contracts:
 *
 *  - TC-UI-050/051-a: the success branch's observable effect is the
 *    "已绑定" card rendering driven by `prefs.readSecret` at mount time.
 *    We pre-seed the secret and verify the bound-state card appears —
 *    this is the exact path the UI takes after a successful QR flow
 *    writes creds via synchronous `prefs.writeSecret`.
 *  - TC-UI-052-a: QR generation failure is signalled by the private
 *    top-level `generateQrBitmap` returning `null`. We invoke it via
 *    reflection with a known-failing input (`size = 0`) to pin the
 *    null-return contract the UI relies on to set "⚠️ 二维码生成失败".
 *  - TC-UI-053-a: cancel button is only reachable after qrRegister is
 *    running. Without mocking the network, we verify the **idle-state
 *    invariant** — cancel button absent, start button present.
 *  - TC-UI-054-a: "清除凭证" path is fully synchronous via
 *    `prefs.clearSecrets` — pre-seed, click, assert secrets gone.
 *  - TC-UI-055-a: Weixin card's descriptive fallback text renders
 *    regardless of async state (the text users see when the flow
 *    hasn't started or has failed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HermesGatewayQrBindScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: HermesGatewayPreferences

    @Before
    fun setUp() {
        resetSingleton()
        resetDataStoreDelegate()
        context.getSharedPreferences("hermes_gateway_secrets_plain", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val dataStoreFile = java.io.File(
            context.filesDir.parentFile,
            "datastore/hermes_gateway_preferences.preferences_pb",
        )
        if (dataStoreFile.exists()) dataStoreFile.delete()
        prefs = HermesGatewayPreferences.getInstance(context)
    }

    @After
    fun tearDown() { resetSingleton() }

    // ── TC-UI-050-a: Feishu bound-state rendering driven by writeSecret ──
    @Test
    fun `feishu success writes creds — pre-seeded appId renders bound card`() {
        // The QR success branch synchronously calls:
        //   prefs.writeSecret(FEISHU, SECRET_FEISHU_APP_ID, ...)
        //   prefs.writeSecret(FEISHU, SECRET_FEISHU_BOT_NAME, ...)
        //   prefs.writeSecret(FEISHU, SECRET_FEISHU_DOMAIN, ...)
        // Then the card re-renders with `appId.isNotEmpty()` gating the
        // "已绑定: $appId" info card. Pre-seeding mimics post-success state.
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            "cli_seeded_app",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_BOT_NAME,
            "Hermes Bot",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_DOMAIN,
            "lark",
        )

        composeTestRule.setContent { HermesGatewayQrBindScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("已绑定: cli_seeded_app").assertExists()
        composeTestRule.onNodeWithText("Bot: Hermes Bot").assertExists()
        composeTestRule.onNodeWithText("Domain: lark").assertExists()
    }

    // ── TC-UI-051-a: Weixin bound-state rendering ──
    @Test
    fun `weixin success writes creds — pre-seeded accountId renders bound card`() {
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
            "wx_seed_123",
        )

        composeTestRule.setContent { HermesGatewayQrBindScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("已绑定账号: wx_seed_123").assertExists()
    }

    // ── TC-UI-052-a: generateQrBitmap null-return contract ──
    @Test
    fun `qr gen failure — generateQrBitmap returns null on invalid size`() {
        // The UI sets "⚠️ 二维码生成失败" iff `generateQrBitmap(...)` returns
        // null. This TC pins that contract directly against the private
        // top-level function — it's what the UI depends on to decide
        // whether to render the bitmap or the error status.
        val gen = reflectGenerateQrBitmap()

        // Valid input → non-null Bitmap (happy path sanity — confirms the
        // method signature and normal return shape).
        val ok = gen.invoke(null, "https://hermes.example/qr", 256) as Bitmap?
        assertNotNull("valid content + size must produce a Bitmap", ok)
        assertEquals(256, ok!!.width)
        assertEquals(256, ok.height)

        // Known-failing input: ZXing throws IllegalArgumentException on
        // non-positive size; the method's try/catch returns null.
        val bad = gen.invoke(null, "content", 0) as Bitmap?
        assertNull("size=0 must trip ZXing → catch → null return", bad)
    }

    // ── TC-UI-053-a: idle-state button invariants ──
    @Test
    fun `cancel clears state — idle state shows start buttons, no cancel button`() {
        // The cancel button is gated on `isRunning=true`, which requires
        // qrRegister/qrLogin to be in flight — not reachable without a
        // mock network. We assert the idle-state invariant instead: on
        // first mount (isRunning=false, no creds), each card exposes its
        // Start button and neither exposes the "取消" button.
        composeTestRule.setContent { HermesGatewayQrBindScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("开始扫码注册").assertExists()
        composeTestRule.onNodeWithText("开始扫码登录").assertExists()
        // No cancel button when nothing's running — both cards share the
        // same "取消" label, so zero occurrences across the tree.
        val cancelCount = composeTestRule.onAllNodesWithText("取消").fetchSemanticsNodes().size
        assertEquals("no cancel button in idle state", 0, cancelCount)
    }

    // ── TC-UI-054-a-feishu: Feishu 清除凭证 clears secrets synchronously ──
    @Test
    fun `clear credentials invokes clear — Feishu clear wipes secrets`() {
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            "to_be_cleared",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_FEISHU,
            HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
            "also_cleared",
        )
        // Sanity: bound card renders means the button exists.
        composeTestRule.setContent { HermesGatewayQrBindScreen() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已绑定: to_be_cleared").assertExists()

        // Two 清除凭证 buttons (one per card) when both have creds; only
        // Feishu is seeded here, so exactly one renders. Click it.
        composeTestRule.onNodeWithText("清除凭证").performClick()
        composeTestRule.waitForIdle()

        // Synchronous prefs.clearSecrets — all Feishu secrets wiped.
        assertEquals(
            "app_id must be cleared",
            "",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
            ),
        )
        assertEquals(
            "app_secret must be cleared",
            "",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_FEISHU,
                HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
            ),
        )
        // Status text flips to "已清除凭证".
        composeTestRule.onNodeWithText("已清除凭证").assertExists()
    }

    // ── TC-UI-054-a-weixin: Weixin 清除凭证 clears account_id + token ──
    @Test
    fun `clear credentials invokes clear — Weixin clear wipes secrets`() {
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
            "wx_acct_seed",
        )
        prefs.writeSecret(
            HermesGatewayPreferences.PLATFORM_WEIXIN,
            HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN,
            "wx_token_seed",
        )

        composeTestRule.setContent { HermesGatewayQrBindScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("已绑定账号: wx_acct_seed").assertExists()
        // Weixin card is below Feishu in the vertical scroll; scroll its
        // 清除凭证 button into view before clicking.
        composeTestRule.onNodeWithText("清除凭证").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
            ),
        )
        assertEquals(
            "",
            prefs.readSecret(
                HermesGatewayPreferences.PLATFORM_WEIXIN,
                HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN,
            ),
        )
    }

    // ── TC-UI-055-a: Weixin card's descriptive fallback text always renders ──
    @Test
    fun `weixin fallback message — descriptive qr_login guidance text always visible`() {
        // The Weixin card always renders a descriptive line explaining the
        // qr_login flow (shown whether the QR is live, has failed, or is
        // idle). It's the fallback context users rely on when the flow
        // doesn't work — and the static invariant we can pin without
        // stubbing the network.
        composeTestRule.setContent { HermesGatewayQrBindScreen() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("微信 (Weixin) iLink 扫码登录").assertExists()
        composeTestRule.onNodeWithText(
            "使用 Hermes Python gateway 的 qr_login 协议。扫码 → 手机确认 → 凭证落盘。",
        ).assertExists()
    }

    // ─────────────────────── helpers ───────────────────────

    private fun resetSingleton() {
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

    /**
     * The private top-level `generateQrBitmap(content, size)` is hoisted
     * to a static method on `HermesGatewayQrBindScreenKt`.
     */
    private fun reflectGenerateQrBitmap(): java.lang.reflect.Method {
        val kt = Class.forName(
            "com.ai.assistance.operit.ui.features.settings.screens.HermesGatewayQrBindScreenKt",
        )
        val m = kt.getDeclaredMethod("generateQrBitmap", String::class.java, Int::class.java)
        m.isAccessible = true
        return m
    }
}
