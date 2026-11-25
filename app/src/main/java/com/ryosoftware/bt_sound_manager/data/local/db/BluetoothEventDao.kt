package com.ryosoftware.bt_sound_manager.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BluetoothEventDao {
    @Query("SELECT * FROM bluetooth_events ORDER BY time_utc ASC, id ASC")
    fun observeAll(): Flow<List<BluetoothEventEntity>>

    @Query("SELECT * FROM bluetooth_events ORDER BY time_utc ASC, id ASC")
    suspend fun getAll(): List<BluetoothEventEntity>

    @Query("SELECT * FROM bluetooth_events ORDER BY time_utc DESC, id DESC LIMIT 1")
    suspend fun getLatest(): BluetoothEventEntity?

    @Insert
    suspend fun insert(entity: BluetoothEventEntity): Long

    @Insert
    suspend fun insertAll(entities: List<BluetoothEventEntity>)

    @Query("DELETE FROM bluetooth_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<Long>)

    @Query("DELETE FROM bluetooth_events")
    suspend fun deleteAll()

    @Query("DELETE FROM bluetooth_events WHERE time_utc >= :startUtc AND time_utc < :endUtc")
    suspend fun deleteRange(startUtc: Long, endUtc: Long)

    @Query("DELETE FROM bluetooth_events WHERE time_utc < :cutoffUtc")
    suspend fun deleteBefore(cutoffUtc: Long)
}
