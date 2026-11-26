package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import java.time.Instant
import java.time.LocalDateTime
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
            nextClockSetBackMillis(nowMillis, settings, zone),
        ).minOrNull()
    }

    /**
     * Next instant whose local time is [minuteOfDay]; 1440 means the following midnight.
     * A time skipped when clocks go forward resolves to the moment of the jump, and a
     * time repeated when they go back yields both occurrences.
     */
    private fun nextOccurrenceMillis(
        nowMillis: Long,
        minuteOfDay: Int,
        zone: ZoneId,
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis)
        val today = now.atZone(zone).toLocalDate()
        return (-1L..2L).asSequence()
            .flatMap { dayOffset ->
                val localDateTime = today.plusDays(dayOffset)
                    .atStartOfDay()
                    .plusMinutes(minuteOfDay.toLong())
                instantsAt(localDateTime, zone)
            }
            .filter { it.isAfter(now) }
            .min()
            .toEpochMilli()
    }

    private fun instantsAt(localDateTime: LocalDateTime, zone: ZoneId): Sequence<Instant> {
        val offsets = zone.rules.getValidOffsets(localDateTime)
        return if (offsets.isEmpty()) {
            sequenceOf(zone.rules.getTransition(localDateTime).instant)
        } else {
            offsets.asSequence().map { localDateTime.toInstant(it) }
        }
    }

    /**
     * Turning the clock back can move the local time in or out of the window without
     * passing through its start or end, so that jump is a boundary too.
     */
    private fun nextClockSetBackMillis(
        nowMillis: Long,
        settings: AutomationSettings,
        zone: ZoneId,
    ): Long? {
        val transition = zone.rules.nextTransition(Instant.ofEpochMilli(nowMillis))
            ?.takeIf { it.isOverlap }
            ?: return null
        fun isWithinAt(instant: Instant): Boolean {
            val time = instant.atZone(zone).toLocalTime()
            return isWithin(settings, time.hour * 60 + time.minute)
        }
        val changes = isWithinAt(transition.instant.minusMillis(1)) !=
            isWithinAt(transition.instant)
        return if (changes) transition.instant.toEpochMilli() else null
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
