package com.ryosoftware.bt_sound_manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.bt_sound_manager.BluetoothEnableProxyActivity
import com.ryosoftware.bt_sound_manager.core.util.AppActions
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var automationCoordinator: AutomationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppActions.ACTION_ENABLE_BLUETOOTH -> {
                context.startActivity(
                    Intent(context, BluetoothEnableProxyActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            AppActions.ACTION_TOGGLE_AUTOMATION -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        automationCoordinator.toggleAutomation()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
