package com.ryosoftware.bt_sound_manager.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eventProcessor: EventProcessor,
    private val settingsRepository: SettingsRepository,
    private val serviceController: MonitorServiceController,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        when (inputData.getString(KEY_MODE)) {
            MaintenanceScheduler.MODE_BOOT -> {
                delay(BOOT_PROCESSING_DELAY_MILLIS)
                eventProcessor.onBoot()
            }

            MaintenanceScheduler.MODE_PACKAGE_REPLACED -> eventProcessor.onPackageReplaced()
        }
        serviceController.sync(settingsRepository.snapshot().background)
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val KEY_MODE = "mode"
        const val BOOT_PROCESSING_DELAY_MILLIS = 15_000L
    }
}
