package com.ryosoftware.bt_sound_manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.bt_sound_manager.service.MaintenanceScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var maintenanceScheduler: MaintenanceScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> maintenanceScheduler.enqueueBoot()
            Intent.ACTION_MY_PACKAGE_REPLACED -> maintenanceScheduler.enqueuePackageReplaced()
        }
    }
}
