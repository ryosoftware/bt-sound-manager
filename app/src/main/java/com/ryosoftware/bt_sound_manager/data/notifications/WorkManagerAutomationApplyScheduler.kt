package com.ryosoftware.bt_sound_manager.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationApplyScheduler
import com.ryosoftware.bt_sound_manager.service.AutomationApplyWorker
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerAutomationApplyScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationApplyScheduler {

    override fun scheduleDelayed(
        eventTimeMillis: Long,
        deviceAddress: String?,
        connected: Boolean,
    ) {
        val request = OneTimeWorkRequestBuilder<AutomationApplyWorker>()
            .setInitialDelay(
                AutomationCoordinator.DELAY_BEFORE_APPLY_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .setInputData(
                workDataOf(
                    AutomationApplyWorker.KEY_TRIGGER to AutomationApplyWorker.TRIGGER_DEVICE_EVENT,
                    AutomationApplyWorker.KEY_EVENT_TIME to eventTimeMillis,
                    AutomationApplyWorker.KEY_DEVICE_ADDRESS to deviceAddress,
                    AutomationApplyWorker.KEY_CONNECTED to connected,
                ),
            )
            .build()
        enqueue(WORK_DEVICE_EVENT, request)
    }

    override fun scheduleInterruptionFilterChanged() {
        enqueue(
            WORK_INTERRUPTION,
            OneTimeWorkRequestBuilder<AutomationApplyWorker>()
                .setInputData(
                    workDataOf(
                        AutomationApplyWorker.KEY_TRIGGER to
                            AutomationApplyWorker.TRIGGER_INTERRUPTION,
                    ),
                )
                .build(),
        )
    }

    override fun scheduleUserForeground() {
        enqueue(
            WORK_USER_FOREGROUND,
            OneTimeWorkRequestBuilder<AutomationApplyWorker>()
                .setInputData(
                    workDataOf(
                        AutomationApplyWorker.KEY_TRIGGER to
                            AutomationApplyWorker.TRIGGER_USER_FOREGROUND,
                    ),
                )
                .build(),
        )
    }

    private fun enqueue(name: String, request: OneTimeWorkRequest) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    internal companion object {
        const val WORK_DEVICE_EVENT = "automation-apply-device-event"
        const val WORK_INTERRUPTION = "automation-apply-interruption"
        const val WORK_USER_FOREGROUND = "automation-apply-user"
    }
}
