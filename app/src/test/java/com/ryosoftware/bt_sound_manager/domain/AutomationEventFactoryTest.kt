package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationEventFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationEventFactoryTest {

    @Test
    fun createsConnectedEventWithFirstDeviceName() {
        val event = AutomationEventFactory.create(
            nowUtcMillis = 1_000,
            mode = InterruptMode.DO_NOT_DISTURB,
            bluetoothEnabled = true,
            monitoredConnectedDevices = listOf(
                device("Headphones"),
                device("Speaker"),
            ),
        )

        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, event.type)
        assertEquals("Headphones", event.deviceName)
        assertNull(event.deviceAddress)
        assertEquals(InterruptMode.DO_NOT_DISTURB, event.mode)
        assertEquals(1_000, event.timeUtc)
    }

    @Test
    fun createsNoDevicesEvent() {
        val event = AutomationEventFactory.create(
            nowUtcMillis = 1_000,
            mode = InterruptMode.NORMAL,
            bluetoothEnabled = true,
            monitoredConnectedDevices = emptyList(),
        )

        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES, event.type)
        assertNull(event.deviceName)
        assertEquals(InterruptMode.NORMAL, event.mode)
    }

    @Test
    fun createsExternalModeChangeEvent() {
        val event = AutomationEventFactory.createExternalChange(
            nowUtcMillis = 2_000,
            mode = InterruptMode.ALARMS_ONLY,
        )

        assertEquals(BluetoothEventType.EXTERNAL_MODE_CHANGED, event.type)
        assertNull(event.deviceName)
        assertNull(event.deviceAddress)
        assertEquals(InterruptMode.ALARMS_ONLY, event.mode)
        assertEquals(2_000, event.timeUtc)
    }

    @Test
    fun createsBluetoothOffEvent() {
        val event = AutomationEventFactory.create(
            nowUtcMillis = 1_000,
            mode = InterruptMode.SILENT_ALL,
            bluetoothEnabled = false,
            monitoredConnectedDevices = emptyList(),
        )

        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF, event.type)
        assertNull(event.deviceName)
        assertEquals(InterruptMode.SILENT_ALL, event.mode)
    }

    private fun device(name: String) = BluetoothDeviceInfo(
        address = "AA:BB:CC:DD:EE:FF",
        name = name,
        isConnected = true,
        isBonded = true,
    )
}
