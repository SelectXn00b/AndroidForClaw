package com.xiaomo.hermes.hermes.plugins.memory

import com.xiaomo.hermes.hermes.plugins.memory.honcho.HonchoHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-SKILL-022-a — Honcho provider "save" emits a properly-signed POST.
 *
 * `R-SKILL-001` requires that when a configured Honcho client performs a
 * write operation (e.g. `addMessages`, `createConclusion`), the outbound
 * HTTP request carries the `Authorization: Bearer <apiKey>` header and the
 * expected JSON `Content-Type`. This is the signature contract Honcho's
 * server relies on; omitting it means 401 and silent memory loss.
 *
 * We don't have MockWebServer wired into this module, so we reach into the
 * private `buildRequest` via reflection and inspect the [Request] it builds
 * without actually firing it. This proves the signing logic independently
 * of the network layer.
 */
class MemoryProviderHonchoTest {

    @Test
    fun `save signed — apiKey yields Bearer Authorization header`() {
        val client = HonchoHttpClient(
            workspaceId = "hermes",
            apiKey = "hnc_sk_test_abc123",
            baseUrl = "https://api.honcho.dev",
        )
        val req = invokeBuildRequest(
            client,
            url = "https://api.honcho.dev/workspaces/hermes/sessions/s1/messages",
            method = "POST",
            body = """{"messages":[]}""",
        )

        assertEquals("method must be POST", "POST", req.method)
        assertEquals(
            "apiKey must be surfaced as Bearer token",
            "Bearer hnc_sk_test_abc123",
            req.header("Authorization"),
        )
        assertEquals(
            "Content-Type must be application/json",
            "application/json",
            req.header("Content-Type"),
        )
        assertNotNull("POST body must be present", req.body)
    }

    @Test
    fun `save signed — missing apiKey omits Authorization header`() {
        val client = HonchoHttpClient(
            workspaceId = "hermes",
            apiKey = null,
            baseUrl = "http://localhost:8000",
        )
        val req = invokeBuildRequest(
            client,
            url = "http://localhost:8000/workspaces/hermes/chat",
            method = "POST",
            body = """{"query":"ping"}""",
        )
        assertNull(
            "null apiKey must not emit Authorization",
            req.header("Authorization"),
        )
    }

    @Test
    fun `save signed — empty apiKey string omits Authorization header`() {
        // Per Client.kt buildRequest: `if (!apiKey.isNullOrEmpty())` — so
        // both null and "" are treated as unauthenticated (local-dev mode).
        val client = HonchoHttpClient(
            workspaceId = "hermes",
            apiKey = "",
            baseUrl = "http://localhost:8000",
        )
        val req = invokeBuildRequest(
            client,
            url = "http://localhost:8000/workspaces/hermes/chat",
            method = "POST",
            body = "{}",
        )
        assertNull("empty apiKey must not emit Authorization", req.header("Authorization"))
    }

    @Test
    fun `save signed — GET of chat endpoint still carries Bearer`() {
        val client = HonchoHttpClient(
            workspaceId = "hermes",
            apiKey = "hnc_sk_get",
            baseUrl = "https://api.honcho.dev",
        )
        val req = invokeBuildRequest(
            client,
            url = "https://api.honcho.dev/workspaces/hermes/peers/p1/card",
            method = "GET",
            body = null,
        )
        assertEquals("GET", req.method)
        assertEquals(
            "auth must attach to GET as well as writes",
            "Bearer hnc_sk_get",
            req.header("Authorization"),
        )
    }

    // ────────────────────────── helpers ──────────────────────────

    /**
     * Reach into the private `buildRequest` — it's the sole place auth
     * headers are attached, so asserting on its output is equivalent to
     * asserting on any real outbound call.
     */
    private fun invokeBuildRequest(
        client: HonchoHttpClient,
        url: String,
        method: String,
        body: String?,
    ): Request {
        val m = HonchoHttpClient::class.java.getDeclaredMethod(
            "buildRequest",
            String::class.java,
            String::class.java,
            String::class.java,
        )
        m.isAccessible = true
        return m.invoke(client, url, method, body) as Request
    }
}
