package com.ryosoftware.bt_sound_manager.domain.model

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val isConnected: Boolean,
    val isBonded: Boolean,
)

data class BluetoothSnapshot(
    val supported: Boolean,
    val enabled: Boolean,
    val connectedDevices: List<BluetoothDeviceInfo> = emptyList(),
)
