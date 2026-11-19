package com.ryosoftware.bt_sound_manager.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BluetoothEventEntity::class, DeviceNameEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bluetoothEventDao(): BluetoothEventDao

    abstract fun deviceNameDao(): DeviceNameDao
}
