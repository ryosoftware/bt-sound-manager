package com.ryosoftware.bt_sound_manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.bt_sound_manager.service.MonitorBroadcastHandler
import com.ryosoftware.bt_sound_manager.service.MonitorBroadcastSource
import com.ryosoftware.bt_sound_manager.service.MonitorServiceState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var broadcastHandler: MonitorBroadcastHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return
        if (MonitorServiceState.shutdownDetected.value) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                broadcastHandler.handle(
                    intent = intent,
                    source = MonitorBroadcastSource.RECEIVER,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
