package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothRepository {
    fun isSupported(): Boolean

    fun isEnabled(): Boolean

    fun hasPermissions(): Boolean

    fun bondedDevices(): List<BluetoothDeviceInfo>

    suspend fun connectedDevices(): List<BluetoothDeviceInfo>

    fun observeConnectedDevices(): StateFlow<List<BluetoothDeviceInfo>>

    fun updateConnectionState(address: String?, name: String?, connected: Boolean)

    fun clearConnectionState()

    fun isDeviceConnected(address: String): Boolean

    fun observeSnapshot(): Flow<BluetoothSnapshot>

    fun refreshSnapshot()

    fun tryEnableBluetooth(): Boolean
}
