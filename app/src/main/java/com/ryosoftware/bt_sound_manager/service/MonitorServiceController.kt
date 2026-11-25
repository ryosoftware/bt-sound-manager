package com.ryosoftware.bt_sound_manager.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothRepository: BluetoothRepository,
) {
    fun start() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorForegroundService::class.java),
            )
        } catch (_: Exception) {
            // Android may reject a foreground service start from a restricted context.
        }
    }

    fun stop() {
        context.stopService(Intent(context, MonitorForegroundService::class.java))
    }

    fun sync(settings: BackgroundSettings) {
        if (settings.anyEnabled && bluetoothRepository.hasPermissions()) start() else stop()
    }
}
