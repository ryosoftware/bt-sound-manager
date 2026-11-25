package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.usecase.EventRetention
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRetentionTest {

    @Test
    fun cutoffStartsAtMidnightSevenDaysAgo() {
        val now = Instant.parse("2026-01-15T12:34:56Z").toEpochMilli()
        val expected = Instant.parse("2026-01-08T00:00:00Z").toEpochMilli()
        assertEquals(
            expected,
            EventRetention.cutoffUtcMillis(now, ZoneId.of("UTC")),
        )
    }

    @Test
    fun cutoffUsesLocalZone() {
        val now = Instant.parse("2026-01-15T01:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-01-07T23:00:00Z").toEpochMilli()
        assertEquals(
            expected,
            EventRetention.cutoffUtcMillis(now, ZoneId.of("Europe/Madrid")),
        )
    }

    @Test
    fun cutoffUsesConfiguredDays() {
        val now = Instant.parse("2026-01-15T12:34:56Z").toEpochMilli()

        assertEquals(
            Instant.parse("2026-01-12T00:00:00Z").toEpochMilli(),
            EventRetention.cutoffUtcMillis(now, ZoneId.of("UTC"), days = 3),
        )
        assertEquals(
            Instant.parse("2025-12-16T00:00:00Z").toEpochMilli(),
            EventRetention.cutoffUtcMillis(now, ZoneId.of("UTC"), days = 30),
        )
    }
}
