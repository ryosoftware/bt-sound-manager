package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

object AutomationEventFactory {

    fun createExternalChange(
        nowUtcMillis: Long,
        mode: InterruptMode,
    ): BluetoothEvent = BluetoothEvent(
        timeUtc = nowUtcMillis,
        type = BluetoothEventType.EXTERNAL_MODE_CHANGED,
        mode = mode,
    )

    fun create(
        nowUtcMillis: Long,
        mode: InterruptMode,
        bluetoothEnabled: Boolean,
        monitoredConnectedDevices: List<BluetoothDeviceInfo>,
    ): BluetoothEvent = createWithType(
        type = causeType(
            bluetoothEnabled = bluetoothEnabled,
            hasConnectedDevices = monitoredConnectedDevices.isNotEmpty(),
            connectedType = BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
            noDevicesType = BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
            bluetoothOffType = BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF,
        ),
        nowUtcMillis = nowUtcMillis,
        mode = mode,
        monitoredConnectedDevices = monitoredConnectedDevices,
    )

    fun createAttempt(
        nowUtcMillis: Long,
        mode: InterruptMode,
        bluetoothEnabled: Boolean,
        monitoredConnectedDevices: List<BluetoothDeviceInfo>,
    ): BluetoothEvent = createWithType(
        type = causeType(
            bluetoothEnabled = bluetoothEnabled,
            hasConnectedDevices = monitoredConnectedDevices.isNotEmpty(),
            connectedType = BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED,
            noDevicesType = BluetoothEventType.AUTOMATION_ATTEMPTING_NO_DEVICES,
            bluetoothOffType = BluetoothEventType.AUTOMATION_ATTEMPTING_BLUETOOTH_OFF,
        ),
        nowUtcMillis = nowUtcMillis,
        mode = mode,
        monitoredConnectedDevices = monitoredConnectedDevices,
    )

    fun createFailure(
        nowUtcMillis: Long,
        mode: InterruptMode,
        bluetoothEnabled: Boolean,
        monitoredConnectedDevices: List<BluetoothDeviceInfo>,
    ): BluetoothEvent = createWithType(
        type = causeType(
            bluetoothEnabled = bluetoothEnabled,
            hasConnectedDevices = monitoredConnectedDevices.isNotEmpty(),
            connectedType = BluetoothEventType.AUTOMATION_FAILED_CONNECTED,
            noDevicesType = BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES,
            bluetoothOffType = BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF,
        ),
        nowUtcMillis = nowUtcMillis,
        mode = mode,
        monitoredConnectedDevices = monitoredConnectedDevices,
    )

    fun createPermissionsFailure(
        nowUtcMillis: Long,
        mode: InterruptMode,
    ): BluetoothEvent = BluetoothEvent(
        timeUtc = nowUtcMillis,
        type = BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS,
        mode = mode,
    )

    fun createFailureFromConfirmed(
        confirmedEvent: BluetoothEvent,
        nowUtcMillis: Long,
    ): BluetoothEvent? {
        val failureType = when (confirmedEvent.type) {
            BluetoothEventType.AUTOMATION_APPLIED_CONNECTED ->
                BluetoothEventType.AUTOMATION_FAILED_CONNECTED

            BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES ->
                BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES

            BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF ->
                BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF

            else -> return null
        }
        return confirmedEvent.copy(
            id = 0,
            timeUtc = nowUtcMillis,
            type = failureType,
        )
    }

    private fun causeType(
        bluetoothEnabled: Boolean,
        hasConnectedDevices: Boolean,
        connectedType: BluetoothEventType,
        noDevicesType: BluetoothEventType,
        bluetoothOffType: BluetoothEventType,
    ): BluetoothEventType = when {
        !bluetoothEnabled -> bluetoothOffType
        !hasConnectedDevices -> noDevicesType
        else -> connectedType
    }

    private fun createWithType(
        type: BluetoothEventType,
        nowUtcMillis: Long,
        mode: InterruptMode,
        monitoredConnectedDevices: List<BluetoothDeviceInfo>,
    ): BluetoothEvent {
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
