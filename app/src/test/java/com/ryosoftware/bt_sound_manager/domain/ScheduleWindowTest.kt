package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.usecase.ScheduleWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWindowTest {

    private val zone = ZoneId.of("UTC")

    private fun settings(
        allDay: Boolean = false,
        start: Int = 8 * 60,
        end: Int = 18 * 60,
    ) = AutomationSettings(
        allDay = allDay,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
    )

    @Test
    fun allDayIsAlwaysWithin() {
        assertTrue(ScheduleWindow.isWithin(settings(allDay = true), 0))
        assertTrue(ScheduleWindow.isWithin(settings(allDay = true), 23 * 60 + 59))
        assertNull(
            ScheduleWindow.nextStartMillis(
                nowMillis = 0,
                settings = settings(allDay = true),
                zone = zone,
            ),
        )
    }

    @Test
    fun normalWindowIncludesBoundaries() {
        assertTrue(ScheduleWindow.isWithin(settings(), 8 * 60))
        assertTrue(ScheduleWindow.isWithin(settings(), 18 * 60))
        assertFalse(ScheduleWindow.isWithin(settings(), 7 * 60 + 59))
        assertFalse(ScheduleWindow.isWithin(settings(), 18 * 60 + 1))
    }

    @Test
    fun crossingMidnightWindowWorks() {
        val crossing = settings(start = 22 * 60, end = 6 * 60)
        assertTrue(ScheduleWindow.isWithin(crossing, 23 * 60))
        assertTrue(ScheduleWindow.isWithin(crossing, 2 * 60))
        assertTrue(ScheduleWindow.isWithin(crossing, 6 * 60))
        assertFalse(ScheduleWindow.isWithin(crossing, 12 * 60))
    }

    @Test
    fun nextStartIsTodayIfStillInTheFuture() {
        val now = ZonedDateTime.of(2026, 1, 15, 7, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val expected = ZonedDateTime.of(2026, 1, 15, 8, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(
            expected,
            ScheduleWindow.nextStartMillis(now, settings(), zone),
        )
    }

    @Test
    fun nextStartIsTomorrowAfterWindowStart() {
        val now = ZonedDateTime.of(2026, 1, 15, 9, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val expected = ZonedDateTime.of(2026, 1, 16, 8, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(
            expected,
            ScheduleWindow.nextStartMillis(now, settings(), zone),
        )
    }

    @Test
    fun nextStartAtExactStartMovesToTomorrow() {
        val now = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-16T08:00:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextStartMillis(now, settings(), zone))
    }

    @Test
    fun allDayHasNoEndOrBoundary() {
        val now = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli()
        assertNull(ScheduleWindow.nextEndMillis(now, settings(allDay = true), zone))
        assertNull(ScheduleWindow.nextBoundaryMillis(now, settings(allDay = true), zone))
    }

    @Test
    fun nextEndIsFirstMinuteOutsideWindow() {
        val now = Instant.parse("2026-01-15T09:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-15T18:01:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextEndMillis(now, settings(), zone))
    }

    @Test
    fun nextBoundaryChoosesStartBeforeWindow() {
        val now = Instant.parse("2026-01-15T07:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-15T08:00:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, settings(), zone))
    }

    @Test
    fun nextBoundaryChoosesEndInsideWindow() {
        val now = Instant.parse("2026-01-15T09:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-15T18:01:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, settings(), zone))
    }

    @Test
    fun nextBoundaryChoosesTomorrowStartAfterWindow() {
        val now = Instant.parse("2026-01-15T19:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-16T08:00:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, settings(), zone))
    }

    @Test
    fun nextBoundaryHandlesCrossingMidnightInside() {
        val crossing = settings(start = 22 * 60, end = 6 * 60)
        val now = Instant.parse("2026-01-15T23:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-16T06:01:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, crossing, zone))
    }

    @Test
    fun nextBoundaryHandlesCrossingMidnightOutside() {
        val crossing = settings(start = 22 * 60, end = 6 * 60)
        val now = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-15T22:00:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, crossing, zone))
    }

    @Test
    fun nextBoundaryAtExactEndWaitsForFirstOutsideMinute() {
        val now = Instant.parse("2026-01-15T18:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-15T18:01:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, settings(), zone))
    }

    @Test
    fun nextBoundaryAfterEndChoosesTomorrowStart() {
        val now = Instant.parse("2026-01-15T18:01:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-16T08:00:00Z").toEpochMilli()
        assertEquals(expected, ScheduleWindow.nextBoundaryMillis(now, settings(), zone))
    }
}
