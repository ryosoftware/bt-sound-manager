package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.AutomationScheduleStatus
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationScheduleStatusResolver
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationScheduleStatusResolverTest {

    private val zone = ZoneId.of("UTC")

    private fun settings(
        enabled: Boolean = true,
        allDay: Boolean = false,
        start: Int = 8 * 60,
        end: Int = 18 * 60,
    ) = AutomationSettings(
        enabled = enabled,
        allDay = allDay,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
    )

    @Test
    fun disabledAutomationHasNoStatus() {
        assertNull(AutomationScheduleStatusResolver.resolve(settings(enabled = false), 9 * 60))
    }

    @Test
    fun allDayAutomationHasAllDayStatus() {
        assertEquals(
            AutomationScheduleStatus.ALL_DAY,
            AutomationScheduleStatusResolver.resolve(settings(allDay = true), 3 * 60),
        )
    }

    @Test
    fun resolvesInsideSchedule() {
        assertEquals(
            AutomationScheduleStatus.INSIDE_SCHEDULE,
            AutomationScheduleStatusResolver.resolve(settings(), 9 * 60),
        )
    }

    @Test
    fun resolvesOutsideSchedule() {
        assertEquals(
            AutomationScheduleStatus.OUTSIDE_SCHEDULE,
            AutomationScheduleStatusResolver.resolve(settings(), 19 * 60),
        )
    }

    @Test
    fun resolvesCrossingMidnightInside() {
        val crossing = settings(start = 22 * 60, end = 6 * 60)
        assertEquals(
            AutomationScheduleStatus.INSIDE_SCHEDULE,
            AutomationScheduleStatusResolver.resolve(crossing, 23 * 60),
        )
    }

    @Test
    fun resolvesCrossingMidnightOutside() {
        val crossing = settings(start = 22 * 60, end = 6 * 60)
        assertEquals(
            AutomationScheduleStatus.OUTSIDE_SCHEDULE,
            AutomationScheduleStatusResolver.resolve(crossing, 12 * 60),
        )
    }

    @Test
    fun resolvesUsingEpochMillisAndZone() {
        val now = Instant.parse("2026-01-15T19:00:00Z").toEpochMilli()
        assertEquals(
            AutomationScheduleStatus.OUTSIDE_SCHEDULE,
            AutomationScheduleStatusResolver.resolve(settings(), now, zone),
        )
    }
}
