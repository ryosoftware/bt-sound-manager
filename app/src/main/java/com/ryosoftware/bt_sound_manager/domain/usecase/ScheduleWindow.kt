package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import java.time.Instant
import java.time.ZoneId

object ScheduleWindow {

    fun isWithin(settings: AutomationSettings, minuteOfDay: Int): Boolean {
        if (settings.allDay) return true
        val start = settings.startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val end = settings.endMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return if (start <= end) {
            minuteOfDay in start..end
        } else {
            minuteOfDay >= start || minuteOfDay <= end
        }
    }

    fun nextStartMillis(
        nowMillis: Long,
        settings: AutomationSettings,
        zone: ZoneId,
    ): Long? {
        if (settings.allDay) return null
        val start = settings.startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return nextOccurrenceMillis(nowMillis, start, zone)
    }

    fun nextEndMillis(
        nowMillis: Long,
        settings: AutomationSettings,
        zone: ZoneId,
    ): Long? {
        if (settings.allDay) return null
        val end = settings.endMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return nextOccurrenceMillis(nowMillis, end + 1, zone)
    }

    fun nextBoundaryMillis(
        nowMillis: Long,
        settings: AutomationSettings,
        zone: ZoneId,
    ): Long? {
        if (settings.allDay) return null
        return listOfNotNull(
            nextStartMillis(nowMillis, settings, zone),
            nextEndMillis(nowMillis, settings, zone),
        ).minOrNull()
    }

    private fun nextOccurrenceMillis(
        nowMillis: Long,
        minuteOfDay: Int,
        zone: ZoneId,
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var candidate = now.toLocalDate().atStartOfDay(zone).plusMinutes(minuteOfDay.toLong())
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
