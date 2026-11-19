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
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val start = settings.startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        var candidate = now.toLocalDate().atStartOfDay(zone).plusMinutes(start.toLong())
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
