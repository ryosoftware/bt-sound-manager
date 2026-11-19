package com.ryosoftware.bt_sound_manager.data.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.ryosoftware.bt_sound_manager.service.AutomationApplyWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkManagerAutomationApplySchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerAutomationApplyScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            if (workerClassName == AutomationApplyWorker::class.java.name) {
                                NoOpWorker(appContext, workerParameters)
                            } else {
                                null
                            }
                    },
                )
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerAutomationApplyScheduler(context)
    }

    @Test
    fun scheduleDelayedReplacesPreviousDeviceEventWork() {
        scheduler.scheduleDelayed(
            eventTimeMillis = 1_000L,
            deviceAddress = "AA:BB",
            connected = true,
        )
        scheduler.scheduleDelayed(
            eventTimeMillis = 2_000L,
            deviceAddress = "CC:DD",
            connected = false,
        )

        val info = singleWork(WorkManagerAutomationApplyScheduler.WORK_DEVICE_EVENT)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun scheduleInterruptionFilterChangedEnqueuesWork() {
        scheduler.scheduleInterruptionFilterChanged()

        val info = singleWork(WorkManagerAutomationApplyScheduler.WORK_INTERRUPTION)
        assertNotEquals(WorkInfo.State.FAILED, info.state)
    }

    @Test
    fun scheduleUserForegroundEnqueuesWork() {
        scheduler.scheduleUserForeground()

        val info = singleWork(WorkManagerAutomationApplyScheduler.WORK_USER_FOREGROUND)
        assertNotEquals(WorkInfo.State.FAILED, info.state)
    }

    private fun singleWork(uniqueWorkName: String): WorkInfo {
        val infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertEquals(1, infos.size)
        return infos.single()
    }

    private class NoOpWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {
        override fun doWork(): ListenableWorker.Result = ListenableWorker.Result.success()
    }
}
