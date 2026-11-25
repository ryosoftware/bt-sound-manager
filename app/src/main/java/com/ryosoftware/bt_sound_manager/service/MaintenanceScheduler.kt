package com.ryosoftware.bt_sound_manager.service

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueueBoot() = enqueue(MODE_BOOT)

    fun enqueuePackageReplaced() = enqueue(MODE_PACKAGE_REPLACED)

    private fun enqueue(mode: String) {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>()
            .setInputData(workDataOf(MaintenanceWorker.KEY_MODE to mode))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "maintenance-$mode",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val MODE_BOOT = "boot"
        const val MODE_PACKAGE_REPLACED = "package_replaced"
    }
}
