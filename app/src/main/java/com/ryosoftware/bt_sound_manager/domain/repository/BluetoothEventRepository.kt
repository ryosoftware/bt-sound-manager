package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import kotlinx.coroutines.flow.Flow

interface BluetoothEventRepository {
    fun observeEvents(): Flow<List<BluetoothEvent>>

    suspend fun addEvent(event: BluetoothEvent): Long

    suspend fun replaceEvent(removedId: Long, event: BluetoothEvent): Long

    suspend fun getLatestEvent(): BluetoothEvent?

    suspend fun getAllEvents(): List<BluetoothEvent>

    suspend fun replaceAllEvents(events: List<BluetoothEvent>)

    suspend fun pruneExpired()

    suspend fun deleteEvents(ids: Set<Long>)

    suspend fun deleteAll()

    suspend fun deleteRange(startUtc: Long, endUtc: Long)
}
