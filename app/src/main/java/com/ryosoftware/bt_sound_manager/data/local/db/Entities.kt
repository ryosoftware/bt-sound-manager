package com.ryosoftware.bt_sound_manager.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bluetooth_events",
    indices = [Index(value = ["time_utc"])],
)
data class BluetoothEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "time_utc")
    val timeUtc: Long,
    @ColumnInfo(name = "event_type")
    val eventType: Int,
    @ColumnInfo(name = "device_address")
    val deviceAddress: String?,
    @ColumnInfo(name = "device_name")
    val deviceName: String?,
)

@Entity(tableName = "device_names")
data class DeviceNameEntity(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "name")
    val name: String,
)
