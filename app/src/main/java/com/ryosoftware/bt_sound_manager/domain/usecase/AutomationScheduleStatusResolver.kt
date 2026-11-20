package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.AutomationScheduleStatus
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import java.time.Instant
import java.time.ZoneId

object AutomationScheduleStatusResolver {

    fun resolve(settings: AutomationSettings, minuteOfDay: Int): AutomationScheduleStatus? {
        if (!settings.enabled) return null
        if (settings.allDay) return AutomationScheduleStatus.ALL_DAY
        return if (ScheduleWindow.isWithin(settings, minuteOfDay)) {
            AutomationScheduleStatus.INSIDE_SCHEDULE
        } else {
            AutomationScheduleStatus.OUTSIDE_SCHEDULE
        }
    }

    fun resolve(
        settings: AutomationSettings,
        nowMillis: Long,
        zone: ZoneId,
    ): AutomationScheduleStatus? {
        val minuteOfDay = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalTime()
            .let { it.hour * 60 + it.minute }
        return resolve(settings, minuteOfDay)
    }
}
