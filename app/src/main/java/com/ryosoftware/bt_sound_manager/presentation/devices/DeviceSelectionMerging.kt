package com.ryosoftware.bt_sound_manager.presentation.devices

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo

fun mergeSelectableDevices(
    bluetoothDevices: List<BluetoothDeviceInfo>,
    cachedNames: Map<String, String>,
    selectedAddresses: Set<String>,
): List<SelectableDevice> {
    val merged = linkedMapOf<String, SelectableDevice>()
    bluetoothDevices.forEach { device ->
        merged[device.address] = SelectableDevice(
            address = device.address,
            name = cachedNames[device.address] ?: device.name,
            connected = device.isConnected,
            bonded = device.isBonded,
        )
    }
    cachedNames.forEach { (address, name) ->
        merged.putIfAbsent(
            address,
            SelectableDevice(
                address = address,
                name = name,
                connected = false,
                bonded = false,
            ),
        )
    }
    selectedAddresses.forEach { address ->
        merged.putIfAbsent(
            address,
            SelectableDevice(
                address = address,
                name = address,
                connected = false,
                bonded = false,
            ),
        )
    }
    return merged.values.sortedBy { it.name.lowercase() }
}
