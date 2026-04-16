package com.xiaomo.hermes.config

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigMergeTest {

    // ===== mergeJsonConfigs =====

    @Test
    fun `empty overlay returns base copy`() {
        val base = JSONObject().put("key", "value")
        val overlay = JSONObject()
        val result = ConfigMerge.mergeJsonConfigs(base, overlay)
        assertEquals("value", result.getString("key"))
    }

    @Test
    fun `overlay overrides top-level field`() {
        val base = JSONObject().put("name", "old")
        val overlay = JSONObject().put("name", "new")
        val result = ConfigMerge.mergeJsonConfigs(base, overlay)
        assertEquals("new", result.getString("name"))
    }

    @Test
    fun `nested objects are deep merged`() {
        val base = JSONObject()
            .put("outer", JSONObject().put("a", 1).put("b", 2))
        val overlay = JSONObject()
            .put("outer", JSONObject().put("b", 99).put("c", 3))
        val result = ConfigMerge.mergeJsonConfigs(base, overlay)
        val outer = result.getJSONObject("outer")
        assertEquals(1, outer.getInt("a"))
        assertEquals(99, outer.getInt("b"))
        assertEquals(3, outer.getInt("c"))
    }

    @Test
    fun `arrays are replaced wholesale`() {
        val base = JSONObject()
            .put("items", JSONArray().put("a").put("b"))
        val overlay = JSONObject()
            .put("items", JSONArray().put("x"))
        val result = ConfigMerge.mergeJsonConfigs(base, overlay)
        val items = result.getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals("x", items.getString(0))
    }

    @Test
    fun `overlay new fields are appended`() {
        val base = JSONObject().put("a", 1)
        val overlay = JSONObject().put("b", 2)
        val result = ConfigMerge.mergeJsonConfigs(base, overlay)
        assertEquals(1, result.getInt("a"))
        assertEquals(2, result.getInt("b"))
    }

    @Test
    fun `explicit null in overlay does not erase base`() {
        val base = JSONObject().put("key", "keep-me")
        val overlay = JSONObject().put("key", JSONObject.NULL)
        val result = ConfigMerge.mergeJsonConfigs(base, overlay)
        assertEquals("keep-me", result.getString("key"))
    }

    @Test
    fun `base is not mutated`() {
        val base = JSONObject().put("x", 1)
        val overlay = JSONObject().put("x", 2)
        ConfigMerge.mergeJsonConfigs(base, overlay)
        assertEquals(1, base.getInt("x"))
    }

    // ===== resolveModelAlias =====

    @Test
    fun `null aliases returns original`() {
        assertEquals("gpt-4o", ConfigMerge.resolveModelAlias("gpt-4o", null))
    }

    @Test
    fun `empty aliases returns original`() {
        assertEquals("gpt-4o", ConfigMerge.resolveModelAlias("gpt-4o", emptyMap()))
    }

    @Test
    fun `matching alias returns mapped value`() {
        val aliases = mapOf("fast" to "gpt-4o-mini", "smart" to "gpt-4o")
        assertEquals("gpt-4o-mini", ConfigMerge.resolveModelAlias("fast", aliases))
    }

    @Test
    fun `non-matching alias returns original`() {
        val aliases = mapOf("fast" to "gpt-4o-mini")
        assertEquals("claude-3", ConfigMerge.resolveModelAlias("claude-3", aliases))
    }
}
