package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

object AutomationEventFactory {

    fun create(
        nowUtcMillis: Long,
        mode: InterruptMode,
        bluetoothEnabled: Boolean,
        monitoredConnectedDevices: List<BluetoothDeviceInfo>,
    ): BluetoothEvent {
        val type = when {
            !bluetoothEnabled -> BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF
            monitoredConnectedDevices.isEmpty() -> BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES
            else -> BluetoothEventType.AUTOMATION_APPLIED_CONNECTED
        }
        val firstDevice = monitoredConnectedDevices.firstOrNull()
        return BluetoothEvent(
            timeUtc = nowUtcMillis,
            type = type,
            deviceName = firstDevice?.let { device ->
                device.name.takeIf { it.isNotBlank() } ?: device.address
            },
            mode = mode,
        )
    }
}
