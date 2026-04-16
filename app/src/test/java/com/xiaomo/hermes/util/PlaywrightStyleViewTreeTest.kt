package com.xiaomo.hermes.util

import com.xiaomo.hermes.accessibility.service.Point
import com.xiaomo.hermes.accessibility.service.ViewNode
import org.junit.Assert.*
import org.junit.Test

class PlaywrightStyleViewTreeTest {

    private fun makeNode(
        index: Int = 0,
        text: String? = null,
        contentDesc: String? = null,
        className: String? = "android.widget.Button",
        clickable: Boolean = false,
        scrollable: Boolean = false,
        resourceId: String? = null,
        x: Int = 540, y: Int = 960,
        left: Int = 0, top: Int = 0, right: Int = 1080, bottom: Int = 1920
    ) = ViewNode(
        index = index,
        text = text,
        resourceId = resourceId,
        className = className,
        packageName = "com.test",
        contentDesc = contentDesc,
        clickable = clickable,
        enabled = true,
        focusable = false,
        focused = false,
        scrollable = scrollable,
        point = Point(x, y),
        left = left, right = right, top = top, bottom = bottom,
        node = null
    )

    @Test
    fun emptyNodesReturnEmpty() {
        val result = PlaywrightStyleViewTree.buildSnapshot(emptyList())
        assertEquals("(empty)", result.snapshot)
        assertTrue(result.refs.isEmpty())
        assertEquals(0, result.stats.totalNodes)
    }

    @Test
    fun buttonGetsRefAndRole() {
        val nodes = listOf(
            makeNode(text = "登录", className = "android.widget.Button", clickable = true)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertTrue("Should contain button role", result.snapshot.contains("- button"))
        assertTrue("Should contain ref", result.snapshot.contains("[ref=e1]"))
        assertTrue("Should contain name", result.snapshot.contains("\"登录\""))
        assertTrue("Should contain center", result.snapshot.contains("center="))
        assertTrue("Should contain bounds", result.snapshot.contains("bounds="))
        assertEquals(1, result.refs.size)
        assertEquals("button", result.refs["e1"]?.role)
        assertEquals("登录", result.refs["e1"]?.name)
    }

    @Test
    fun textboxGetsRefAndRole() {
        val nodes = listOf(
            makeNode(text = "用户名", className = "android.widget.EditText")
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertTrue("Should map EditText to textbox", result.snapshot.contains("- textbox"))
        assertTrue("Should have ref", result.snapshot.contains("[ref="))
    }

    @Test
    fun groupWithoutContentGetsNoRef() {
        val nodes = listOf(
            makeNode(className = "android.widget.FrameLayout", text = null, contentDesc = null)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertTrue("Should be group role", result.snapshot.contains("- group"))
        assertFalse("Group without content should not get ref", result.snapshot.contains("[ref="))
        assertEquals(0, result.refs.size)
    }

    @Test
    fun clickableViewGetButtonRole() {
        val nodes = listOf(
            makeNode(text = "自定义控件", className = "com.custom.SomeView", clickable = true)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertTrue("Clickable view with text should become button", result.snapshot.contains("- button"))
    }

    @Test
    fun statsAreCorrect() {
        val nodes = listOf(
            makeNode(index = 0, text = "按钮1", className = "android.widget.Button", clickable = true, top = 200),
            makeNode(index = 1, text = "文本", className = "android.widget.TextView", top = 400),
            makeNode(index = 2, className = "android.widget.FrameLayout", top = 100)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertEquals(3, result.stats.totalNodes)
        assertTrue("Should have at least 1 interactive", result.stats.interactiveNodes >= 1)
    }

    @Test
    fun systemStatusBarFiltered() {
        val nodes = listOf(
            makeNode(text = "12:00", top = 0, bottom = 50),  // status bar clock
            makeNode(text = "登录", className = "android.widget.Button", clickable = true, top = 500, bottom = 600)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertFalse("Should filter status bar", result.snapshot.contains("12:00"))
        assertTrue("Should keep real button", result.snapshot.contains("登录"))
    }

    @Test
    fun scrollableShowsAnnotation() {
        val nodes = listOf(
            makeNode(className = "androidx.recyclerview.widget.RecyclerView", scrollable = true, top = 200)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertTrue("Scrollable should be annotated", result.snapshot.contains("[scrollable]"))
    }

    @Test
    fun contentDescUsedAsName() {
        val nodes = listOf(
            makeNode(contentDesc = "返回按钮", className = "android.widget.ImageButton", clickable = true, top = 200)
        )
        val result = PlaywrightStyleViewTree.buildSnapshot(nodes)
        assertTrue("contentDesc should be used as name", result.snapshot.contains("\"返回按钮\""))
    }
}
