package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import java.time.Instant
import java.time.ZoneId

object EventRetention {

    const val MIN_DAYS = EventsSettings.MIN_RETENTION_DAYS
    const val MAX_DAYS = EventsSettings.MAX_RETENTION_DAYS
    const val DEFAULT_DAYS = EventsSettings.DEFAULT_RETENTION_DAYS

    fun cutoffUtcMillis(
        nowMillis: Long,
        zone: ZoneId,
        days: Int = DEFAULT_DAYS,
    ): Long {
        val cutoffDate = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .minusDays(days.toLong())
        return cutoffDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
