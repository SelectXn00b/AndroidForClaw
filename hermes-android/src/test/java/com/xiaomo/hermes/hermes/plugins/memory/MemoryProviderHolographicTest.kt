package com.xiaomo.hermes.hermes.plugins.memory

import com.xiaomo.hermes.hermes.plugins.memory.holographic.HolographicProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * TC-SKILL-021-a — Holographic `MemoryProvider` round-trip.
 *
 * `R-SKILL-001` requires that every provider's `store()` emits an id that
 * the same instance can then pull back through `retrieve()`. This test
 * drives the Kotlin port of `hermes-agent/plugins/memory/holographic/holographic.py`
 * with an explicit temp `storageDir` so we never reach into
 * `getHermesHome()` (would need Android `Context`).
 *
 * Strategy:
 *  1. `initialize` with an isolated temp dir (bypasses `getHermesHome`).
 *  2. `store` a short English sentence — tokenizer is case-insensitive BoW,
 *     threshold >= 0.7 by default, so a query that shares 3+ content words
 *     must score 1.0 once both sides are tokenized.
 *  3. `retrieve` with a lexically identical query.
 *  4. Assert we get back the same content + id.
 *  5. `close` + teardown.
 */
class MemoryProviderHolographicTest {

    private lateinit var tempDir: File
    private lateinit var provider: HolographicProvider

    @Before
    fun setUp() {
        tempDir = createTempDir(prefix = "hol-test-")
        provider = HolographicProvider()
        runBlocking {
            provider.initialize(
                mapOf(
                    "storageDir" to tempDir,
                    // Lower the threshold so exact-token-set queries reliably
                    // cross it — cosine similarity on identical bags is 1.0,
                    // but the 0.7 default gives us zero wiggle room for
                    // tokenizer punctuation differences.
                    "similarityThreshold" to 0.7,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        runBlocking { provider.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `roundtrip — store then retrieve returns same content and id`() = runBlocking {
        val content = "the quick brown fox jumps over the lazy dog"
        val id = provider.store(content, metadata = mapOf("source" to "test"))

        assertTrue("store() must return a non-empty id", id.isNotEmpty())
        assertTrue("holographic id prefix must be hol_", id.startsWith("hol_"))

        val hits = provider.retrieve(
            query = content,
            limit = 10,
            threshold = 0.7,
        )

        assertEquals("retrieve must surface the single stored item", 1, hits.size)
        val hit = hits.single()
        assertEquals("content must roundtrip verbatim", content, hit.content)
        assertEquals("id must match the one returned by store()", id, hit.id)
        assertEquals("metadata must roundtrip", "test", hit.metadata["source"])
        assertTrue(
            "score must be close to 1.0 for identical content (got ${hit.score})",
            (hit.score ?: 0.0) >= 0.99,
        )
    }

    @Test
    fun `roundtrip — list after store returns the stored item`() = runBlocking {
        val content = "hello from the holographic provider"
        val id = provider.store(content, metadata = emptyMap())

        val items = provider.list(limit = 100, offset = 0)
        assertEquals("list must return exactly the stored item", 1, items.size)
        assertEquals(id, items.single().id)
        assertEquals(content, items.single().content)
    }

    @Test
    fun `roundtrip — delete drops the stored item`() = runBlocking {
        val id = provider.store("to be deleted", metadata = emptyMap())
        assertTrue("delete must report true for existing id", provider.delete(id))
        assertTrue("delete must report false for missing id", !provider.delete(id))
        assertTrue("list must be empty after delete", provider.list(100, 0).isEmpty())
    }
}
