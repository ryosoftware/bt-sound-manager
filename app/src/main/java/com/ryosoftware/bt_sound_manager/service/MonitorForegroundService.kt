package com.ryosoftware.bt_sound_manager.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.repository.MonitorNotificationRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationScheduleStatusResolver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MonitorForegroundService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var broadcastHandler: MonitorBroadcastHandler

    @Inject
    lateinit var notificationRepository: MonitorNotificationRepository

    @Inject
    lateinit var statusHolder: AutomationStatusHolder

    @Inject
    lateinit var clock: AppClock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val eventIntent = intent ?: return
            if (eventIntent.action == null) return
            if (MonitorServiceState.shutdownDetected.value) return
            serviceScope.launch {
                if (broadcastHandler.handle(eventIntent, MonitorBroadcastSource.SERVICE)) {
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        MonitorServiceState.setRunning(true)
        MonitorServiceState.setShutdownDetected(false)
        notificationRepository.ensureChannels()
        startAsForeground()
        registerMonitorReceiver()
        serviceScope.launch {
            val automation = settingsRepository.snapshot().automation
            statusHolder.update(
                AutomationScheduleStatusResolver.resolve(
                    settings = automation,
                    nowMillis = clock.nowMillis(),
                    zone = clock.zoneId(),
                ),
            )
            statusHolder.status.collectLatest { status ->
                notificationRepository.updateBackgroundNotification(status)
            }
        }
        serviceScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                if (!settings.background.anyEnabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        MonitorServiceState.setRunning(false)
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was not registered.
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = notificationRepository.buildBackgroundNotification(
            statusHolder.status.value,
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerMonitorReceiver() {
        ContextCompat.registerReceiver(
            this,
            receiver,
            broadcastHandler.intentFilter(),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 101
    }
}
