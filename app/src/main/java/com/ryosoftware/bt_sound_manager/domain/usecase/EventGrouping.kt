package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class EventDayGroup(
    val date: LocalDate,
    val events: List<BluetoothEvent>,
)

object EventGrouping {

    fun group(events: List<BluetoothEvent>, zone: ZoneId): List<EventDayGroup> =
        events
            .groupBy { event ->
                Instant.ofEpochMilli(event.timeUtc).atZone(zone).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayEvents) ->
                EventDayGroup(
                    date = date,
                    events = dayEvents.sortedByDescending { it.timeUtc },
                )
            }
}
