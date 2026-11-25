package com.ryosoftware.bt_sound_manager.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class AutomationApplyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: AutomationCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        when (inputData.getString(KEY_TRIGGER)) {
            TRIGGER_DEVICE_EVENT -> coordinator.applyForEvent(
                eventTimeMillis = inputData.getLong(KEY_EVENT_TIME, 0L),
                deviceAddress = inputData.getString(KEY_DEVICE_ADDRESS),
                connected = inputData.getBoolean(KEY_CONNECTED, false),
            )

            TRIGGER_INTERRUPTION -> coordinator.onInterruptModeChanged()
            TRIGGER_USER_FOREGROUND -> coordinator.onUserForeground()

            TRIGGER_ATTEMPT_TIMEOUT -> coordinator.onAttemptTimeout()
        }
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val TRIGGER_DEVICE_EVENT = "device_event"
        const val TRIGGER_INTERRUPTION = "interruption_filter"
        const val TRIGGER_USER_FOREGROUND = "user_foreground"
        const val TRIGGER_ATTEMPT_TIMEOUT = "attempt_timeout"
        const val KEY_EVENT_TIME = "event_time"
        const val KEY_DEVICE_ADDRESS = "device_address"
        const val KEY_CONNECTED = "connected"
    }
}
