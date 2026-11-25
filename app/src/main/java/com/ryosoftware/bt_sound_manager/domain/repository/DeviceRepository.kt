package com.ryosoftware.bt_sound_manager.domain.repository

import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDeviceNames(): Flow<Map<String, String>>

    suspend fun getDeviceName(address: String): String?

    suspend fun saveDeviceName(address: String, name: String?)
}
