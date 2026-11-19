package com.ryosoftware.bt_sound_manager.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.core.time.SystemAppClock
import com.ryosoftware.bt_sound_manager.data.bluetooth.BluetoothRepositoryImpl
import com.ryosoftware.bt_sound_manager.data.local.db.AppDatabase
import com.ryosoftware.bt_sound_manager.data.local.db.BluetoothEventDao
import com.ryosoftware.bt_sound_manager.data.local.db.DeviceNameDao
import com.ryosoftware.bt_sound_manager.data.local.prefs.RuntimeStateDataStore
import com.ryosoftware.bt_sound_manager.data.local.prefs.SettingsDataStore
import com.ryosoftware.bt_sound_manager.data.notifications.AndroidInterruptModeRepository
import com.ryosoftware.bt_sound_manager.data.notifications.AndroidVolumeRepository
import com.ryosoftware.bt_sound_manager.data.notifications.AutomationSchedulerImpl
import com.ryosoftware.bt_sound_manager.data.notifications.MonitorNotificationRepositoryImpl
import com.ryosoftware.bt_sound_manager.data.notifications.WorkManagerAutomationApplyScheduler
import com.ryosoftware.bt_sound_manager.data.repository.BluetoothEventRepositoryImpl
import com.ryosoftware.bt_sound_manager.data.repository.DeviceRepositoryImpl
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationApplyScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.MonitorNotificationRepository
import com.ryosoftware.bt_sound_manager.domain.repository.RuntimeStateRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.repository.VolumeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.btSoundManagerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bt_sound_manager_settings",
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.btSoundManagerDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bt_sound_manager.db").build()

    @Provides
    fun provideBluetoothEventDao(database: AppDatabase): BluetoothEventDao =
        database.bluetoothEventDao()

    @Provides
    fun provideDeviceNameDao(database: AppDatabase): DeviceNameDao =
        database.deviceNameDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun bindClock(clock: SystemAppClock): AppClock

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: SettingsDataStore): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRuntimeStateRepository(
        repository: RuntimeStateDataStore,
    ): RuntimeStateRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothEventRepository(
        repository: BluetoothEventRepositoryImpl,
    ): BluetoothEventRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(repository: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(repository: BluetoothRepositoryImpl): BluetoothRepository

    @Binds
    @Singleton
    abstract fun bindInterruptModeRepository(
        repository: AndroidInterruptModeRepository,
    ): InterruptModeRepository

    @Binds
    @Singleton
    abstract fun bindVolumeRepository(repository: AndroidVolumeRepository): VolumeRepository

    @Binds
    @Singleton
    abstract fun bindMonitorNotificationRepository(
        repository: MonitorNotificationRepositoryImpl,
    ): MonitorNotificationRepository

    @Binds
    @Singleton
    abstract fun bindAutomationScheduler(scheduler: AutomationSchedulerImpl): AutomationScheduler

    @Binds
    @Singleton
    abstract fun bindAutomationApplyScheduler(
        scheduler: WorkManagerAutomationApplyScheduler,
    ): AutomationApplyScheduler
}
