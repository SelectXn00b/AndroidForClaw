package com.xiaomo.feishu.tools.calendar

import com.google.gson.JsonObject
import com.xiaomo.feishu.tools.FeishuToolTestBase
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeishuCalendarToolsTest : FeishuToolTestBase() {

    private lateinit var calendarTool: FeishuCalendarCalendarTool
    private lateinit var eventTool: FeishuCalendarEventTool
    private lateinit var attendeeTool: FeishuCalendarEventAttendeeTool
    private lateinit var freebusyTool: FeishuCalendarFreebusyTool

    @Before
    fun setUp() {
        calendarTool = FeishuCalendarCalendarTool(config, client)
        eventTool = FeishuCalendarEventTool(config, client)
        attendeeTool = FeishuCalendarEventAttendeeTool(config, client)
        freebusyTool = FeishuCalendarFreebusyTool(config, client)
    }

    // ─── Calendar Tool ───────────────────────────────────────

    @Test
    fun `calendar tool name and enabled`() {
        assertEquals("feishu_calendar_calendar", calendarTool.name)
        assertTrue(calendarTool.isEnabled())
    }

    @Test
    fun `calendar missing action returns error`() = runTest {
        val result = calendarTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `calendar list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("calendar_list", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/calendar/v4/calendars", data)

        val result = calendarTool.execute(mapOf("action" to "list"))
        assertTrue(result.success)
    }

    @Test
    fun `calendar get calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("calendar", jsonObj("calendar_id" to "cal_123"))
        }
        mockGet("/open-apis/calendar/v4/calendars/cal_123", data)

        val result = calendarTool.execute(mapOf("action" to "get", "calendar_id" to "cal_123"))
        assertTrue(result.success)
    }

    @Test
    fun `calendar primary calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("calendars", jsonArr(jsonObj(
                "calendar" to jsonObj("calendar_id" to "cal_primary"),
                "role" to "owner"
            )))
        }
        mockPost("/open-apis/calendar/v4/calendars/primary", data)

        val result = calendarTool.execute(mapOf("action" to "primary"))
        assertTrue(result.success)
    }

    // ─── Event Tool ──────────────────────────────────────────

    @Test
    fun `event tool name`() {
        assertEquals("feishu_calendar_event", eventTool.name)
    }

    @Test
    fun `event missing action returns error`() = runTest {
        val result = eventTool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("action"))
    }

    @Test
    fun `event create calls correct API`() = runTest {
        val eventData = JsonObject().apply {
            add("event", jsonObj("event_id" to "evt_123"))
        }
        mockPost("/open-apis/calendar/v4/calendars/cal_123/events", eventData)

        val result = eventTool.execute(mapOf(
            "action" to "create",
            "calendar_id" to "cal_123",
            "summary" to "Team Meeting",
            "start_time" to "2024-04-01T10:00:00+08:00",
            "end_time" to "2024-04-01T11:00:00+08:00"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `event list calls instance_view API`() = runTest {
        val listData = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/calendar/v4/calendars/cal_123/events/instance_view", listData)

        val result = eventTool.execute(mapOf(
            "action" to "list",
            "calendar_id" to "cal_123",
            "start_time" to "2024-04-01T00:00:00+08:00",
            "end_time" to "2024-04-30T23:59:59+08:00"
        ))
        assertTrue(result.success)
    }

    @Test
    fun `event handles API failure`() = runTest {
        mockGetError("/open-apis/calendar/v4/calendars/cal_123/events/evt_123", "not found")

        val result = eventTool.execute(mapOf(
            "action" to "get",
            "calendar_id" to "cal_123",
            "event_id" to "evt_123"
        ))
        assertFalse(result.success)
    }

    // ─── Attendee Tool ───────────────────────────────────────

    @Test
    fun `attendee tool name`() {
        assertEquals("feishu_calendar_event_attendee", attendeeTool.name)
    }

    @Test
    fun `attendee create calls correct API`() = runTest {
        val attendeeData = JsonObject().apply {
            add("attendees", jsonArr())
        }
        mockPost("/open-apis/calendar/v4/calendars/cal_123/events/evt_123/attendees", attendeeData)

        val result = attendeeTool.execute(mapOf(
            "action" to "create",
            "calendar_id" to "cal_123",
            "event_id" to "evt_123",
            "attendees" to listOf(mapOf("type" to "user", "user_id" to "ou_123"))
        ))
        assertTrue(result.success)
    }

    @Test
    fun `attendee list calls correct API`() = runTest {
        val listData = JsonObject().apply {
            add("items", jsonArr())
            addProperty("has_more", false)
        }
        mockGet("/open-apis/calendar/v4/calendars/cal_123/events/evt_123/attendees", listData)

        val result = attendeeTool.execute(mapOf(
            "action" to "list",
            "calendar_id" to "cal_123",
            "event_id" to "evt_123"
        ))
        assertTrue(result.success)
    }

    // ─── Freebusy Tool ───────────────────────────────────────

    @Test
    fun `freebusy tool name`() {
        assertEquals("feishu_calendar_freebusy", freebusyTool.name)
    }

    @Test
    fun `freebusy list calls correct API`() = runTest {
        val data = JsonObject().apply {
            add("freebusy_list", jsonArr())
        }
        mockPost("/open-apis/calendar/v4/freebusy/batch", data)

        val result = freebusyTool.execute(mapOf(
            "user_ids" to listOf("ou_123"),
            "time_min" to "2024-04-01T00:00:00+08:00",
            "time_max" to "2024-04-02T00:00:00+08:00"
        ))
        assertTrue(result.success)
    }

    // ─── Aggregator ──────────────────────────────────────────

    @Test
    fun `aggregator returns all 4 tools`() {
        val agg = FeishuCalendarTools(config, client)
        assertEquals(4, agg.getAllTools().size)
    }

    @Test
    fun `aggregator respects disabled config`() {
        val agg = FeishuCalendarTools(createDefaultConfig(enableCalendarTools = false), client)
        assertEquals(0, agg.getToolDefinitions().size)
    }
}
