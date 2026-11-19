package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo

fun mergeBluetoothDevices(
    bonded: List<BluetoothDeviceInfo>,
    connected: List<BluetoothDeviceInfo>,
): List<BluetoothDeviceInfo> {
    val merged = linkedMapOf<String, BluetoothDeviceInfo>()
    bonded.forEach { merged[it.address] = it }
    connected.forEach { device ->
        val previous = merged[device.address]
        merged[device.address] = if (previous == null) {
            device
        } else {
            device.copy(
                name = preferredName(previous.name, device.name, device.address),
                isConnected = previous.isConnected || device.isConnected,
                isBonded = previous.isBonded || device.isBonded,
            )
        }
    }
    return merged.values.toList()
}

private fun preferredName(previous: String, incoming: String, address: String): String =
    if (incoming == address && previous != address) previous else incoming
