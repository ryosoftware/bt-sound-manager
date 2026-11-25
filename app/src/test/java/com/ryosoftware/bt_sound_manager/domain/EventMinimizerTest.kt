package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.usecase.EventMinimizer
import org.junit.Assert.assertEquals
import org.junit.Test

class EventMinimizerTest {

    private fun event(
        id: Long,
        time: Long,
        type: BluetoothEventType,
        address: String? = null,
    ) = BluetoothEvent(
        id = id,
        timeUtc = time,
        type = type,
        deviceAddress = address,
    )

    @Test
    fun removesBluetoothOnOffOnInsideOneSecond() {
        val events = listOf(
            event(1, 1_000, BluetoothEventType.BLUETOOTH_ON),
            event(2, 1_400, BluetoothEventType.BLUETOOTH_OFF),
            event(3, 1_900, BluetoothEventType.BLUETOOTH_ON),
        )
        assertEquals(1, EventMinimizer.minimize(events).size)
        assertEquals(1L, EventMinimizer.minimize(events).first().id)
    }

    @Test
    fun keepsEventsWhenGapIsAtLeastOneSecond() {
        val events = listOf(
            event(1, 1_000, BluetoothEventType.BLUETOOTH_ON),
            event(2, 1_400, BluetoothEventType.BLUETOOTH_OFF),
            event(3, 2_000, BluetoothEventType.BLUETOOTH_ON),
        )
        assertEquals(3, EventMinimizer.minimize(events).size)
    }

    @Test
    fun removesDeviceConnectDisconnectConnectInsideOneSecond() {
        val events = listOf(
            event(1, 1_000, BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(2, 1_300, BluetoothEventType.DEVICE_DISCONNECTED, "AA:BB"),
            event(3, 1_800, BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
        )
        assertEquals(1, EventMinimizer.minimize(events).size)
    }

    @Test
    fun keepsEventsFromDifferentDevices() {
        val events = listOf(
            event(1, 1_000, BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(2, 1_300, BluetoothEventType.DEVICE_DISCONNECTED, "CC:DD"),
            event(3, 1_800, BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
        )
        assertEquals(3, EventMinimizer.minimize(events).size)
    }
}
