package com.ryosoftware.bt_sound_manager.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceNameDao {
    @Query("SELECT * FROM device_names ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DeviceNameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceNameEntity)

    @Query("SELECT name FROM device_names WHERE address = :address LIMIT 1")
    suspend fun findName(address: String): String?

    @Query("DELETE FROM device_names WHERE address = :address")
    suspend fun delete(address: String)
}
