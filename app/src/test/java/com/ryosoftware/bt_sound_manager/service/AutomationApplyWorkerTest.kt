package com.ryosoftware.bt_sound_manager.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationApplyWorkerTest {

    private lateinit var context: Context
    private lateinit var coordinator: AutomationCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = mockk(relaxed = true)
    }

    @Test
    fun deviceEventTriggersApplyForEvent() = runBlocking {
        val result = buildWorker(
            workDataOf(
                AutomationApplyWorker.KEY_TRIGGER to
                    AutomationApplyWorker.TRIGGER_DEVICE_EVENT,
                AutomationApplyWorker.KEY_EVENT_TIME to 1_234L,
                AutomationApplyWorker.KEY_DEVICE_ADDRESS to "AA:BB",
                AutomationApplyWorker.KEY_CONNECTED to true,
            ),
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) {
            coordinator.applyForEvent(
                eventTimeMillis = 1_234L,
                deviceAddress = "AA:BB",
                connected = true,
            )
        }
    }

    @Test
    fun interruptionFilterTriggersInterruptModeChanged() = runBlocking {
        val result = buildWorker(
            workDataOf(
                AutomationApplyWorker.KEY_TRIGGER to
                    AutomationApplyWorker.TRIGGER_INTERRUPTION,
            ),
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { coordinator.onInterruptModeChanged() }
    }

    @Test
    fun userForegroundTriggersUserForeground() = runBlocking {
        val result = buildWorker(
            workDataOf(
                AutomationApplyWorker.KEY_TRIGGER to
                    AutomationApplyWorker.TRIGGER_USER_FOREGROUND,
            ),
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { coordinator.onUserForeground() }
    }

    private fun buildWorker(input: androidx.work.Data): AutomationApplyWorker =
        TestListenableWorkerBuilder<AutomationApplyWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker? =
                        if (workerClassName == AutomationApplyWorker::class.java.name) {
                            AutomationApplyWorker(appContext, workerParameters, coordinator)
                        } else {
                            null
                        }
                },
            )
            .setInputData(input)
            .build()
}
