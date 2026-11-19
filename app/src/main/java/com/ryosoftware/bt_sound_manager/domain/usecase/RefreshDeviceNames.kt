package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import javax.inject.Inject

class RefreshDeviceNames @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val deviceRepository: DeviceRepository,
) {

    suspend operator fun invoke(): List<BluetoothDeviceInfo> {
        val devices = mergeBluetoothDevices(
            bonded = bluetoothRepository.bondedDevices(),
            connected = bluetoothRepository.connectedDevices(),
        )
        devices.forEach { device ->
            val name = device.name.trim()
                .takeIf { it.isNotEmpty() && !it.equals(device.address, ignoreCase = true) }
            if (name != null) {
                deviceRepository.saveDeviceName(device.address, name)
            }
        }
        return devices
    }
}
