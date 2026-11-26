package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType

data class MinimizedEvents(
    val events: List<BluetoothEvent>,
    /** For each visible event id, the ids of the events hidden behind it. */
    val hiddenIds: Map<Long, Set<Long>>,
)

object EventMinimizer {

    private const val MIN_GAP_MILLIS = 1_000L

    fun minimize(events: List<BluetoothEvent>): List<BluetoothEvent> =
        minimizeTrackingHidden(events).events

    fun minimizeTrackingHidden(events: List<BluetoothEvent>): MinimizedEvents {
        val result = events.toMutableList()
        val hiddenIds = mutableMapOf<Long, Set<Long>>()
        var index = 2
        while (index < result.size) {
            val first = result[index - 2]
            val middle = result[index - 1]
            val last = result[index]
            if (
                areSameType(last, first) &&
                areOpposite(last, middle) &&
                last.timeUtc - MIN_GAP_MILLIS < first.timeUtc
            ) {
                // The surviving event takes over the removed ones, including any
                // that they were already hiding.
                hiddenIds[first.id] = listOf(middle, last).fold(
                    hiddenIds[first.id].orEmpty(),
                ) { ids, removed ->
                    ids + removed.id + hiddenIds.remove(removed.id).orEmpty()
                }
                result.removeAt(index)
                result.removeAt(index - 1)
                index = maxOf(2, index - 1)
                continue
            }
            index++
        }
        return MinimizedEvents(events = result, hiddenIds = hiddenIds)
    }

    private fun areSameType(first: BluetoothEvent, second: BluetoothEvent): Boolean {
        if (first.type != second.type) return false
        return if (first.type.isDeviceEvent) {
            first.deviceAddress == second.deviceAddress
        } else {
            true
        }
    }

    private fun areOpposite(first: BluetoothEvent, second: BluetoothEvent): Boolean {
        if (first.type == BluetoothEventType.BLUETOOTH_ON &&
            second.type == BluetoothEventType.BLUETOOTH_OFF
        ) {
            return true
        }
        if (first.type == BluetoothEventType.BLUETOOTH_OFF &&
            second.type == BluetoothEventType.BLUETOOTH_ON
        ) {
            return true
        }
        if (!first.type.isDeviceEvent || !second.type.isDeviceEvent) return false
        if (first.deviceAddress != second.deviceAddress) return false
        return (first.type == BluetoothEventType.DEVICE_CONNECTED &&
            second.type == BluetoothEventType.DEVICE_DISCONNECTED) ||
            (first.type == BluetoothEventType.DEVICE_DISCONNECTED &&
                second.type == BluetoothEventType.DEVICE_CONNECTED)
    }
}
