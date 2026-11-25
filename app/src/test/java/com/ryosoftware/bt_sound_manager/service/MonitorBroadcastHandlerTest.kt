package com.ryosoftware.bt_sound_manager.service

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MonitorBroadcastHandlerTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val bluetoothRepository = mockk<BluetoothRepository>()
    private val eventProcessor = mockk<EventProcessor>(relaxed = true)
    private lateinit var handler: MonitorBroadcastHandler

    @Before
    fun setUp() {
        MonitorServiceState.setRunning(false)
        every { eventProcessor.nowMillis() } returns EVENT_TIME
        every { bluetoothRepository.hasPermissions() } returns true
        handler = MonitorBroadcastHandler(
            settingsRepository,
            bluetoothRepository,
            eventProcessor,
        )
    }

    @After
    fun tearDown() {
        MonitorServiceState.setRunning(false)
    }

    @Test
    fun receiverProcessesBluetoothStateWhenDetectionDisabledAndServiceStopped() = runBlocking {
        mockSettings(detectBluetoothEvents = false)

        handler.handle(stateChangedIntent(), MonitorBroadcastSource.RECEIVER)

        coVerify(exactly = 1) {
            eventProcessor.onBluetoothStateChanged(
                enabled = false,
                eventTimeMillis = EVENT_TIME,
                dispatch = AutomationDispatch.DEFERRED,
            )
        }
    }

    @Test
    fun receiverProcessesBluetoothStateWhenServiceRunsWithoutDetection() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = false)

        handler.handle(stateChangedIntent(), MonitorBroadcastSource.RECEIVER)

        coVerify(exactly = 1) {
            eventProcessor.onBluetoothStateChanged(
                enabled = false,
                eventTimeMillis = EVENT_TIME,
                dispatch = AutomationDispatch.DEFERRED,
            )
        }
    }

    @Test
    fun receiverSkipsBluetoothStateWhenServiceHandlesDetection() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = true)

        handler.handle(stateChangedIntent(), MonitorBroadcastSource.RECEIVER)

        coVerify(exactly = 0) {
            eventProcessor.onBluetoothStateChanged(any(), any(), any())
        }
    }

    @Test
    fun serviceProcessesBluetoothStateWhenDetectionEnabled() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = true)

        handler.handle(stateChangedIntent(), MonitorBroadcastSource.SERVICE)

        coVerify(exactly = 1) {
            eventProcessor.onBluetoothStateChanged(
                enabled = false,
                eventTimeMillis = EVENT_TIME,
                dispatch = AutomationDispatch.DIRECT,
            )
        }
    }

    @Test
    fun receiverSkipsBluetoothStateWhenPermissionsAreMissing() = runBlocking {
        every { bluetoothRepository.hasPermissions() } returns false
        mockSettings(detectBluetoothEvents = true)

        handler.handle(stateChangedIntent(), MonitorBroadcastSource.RECEIVER)

        coVerify(exactly = 0) {
            eventProcessor.onBluetoothStateChanged(any(), any(), any())
        }
    }

    @Test
    fun receiverSkipsUserForegroundWhenPermissionsAreMissing() = runBlocking {
        every { bluetoothRepository.hasPermissions() } returns false
        mockSettings(detectBluetoothEvents = true, reactivateOnUserChange = true)

        handler.handle(
            Intent(Intent.ACTION_USER_FOREGROUND),
            MonitorBroadcastSource.RECEIVER,
        )

        coVerify(exactly = 0) {
            eventProcessor.onUserForeground(any())
        }
    }

    @Test
    fun serviceSkipsBluetoothStateWhenDetectionDisabled() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = false)

        handler.handle(stateChangedIntent(), MonitorBroadcastSource.SERVICE)

        coVerify(exactly = 0) {
            eventProcessor.onBluetoothStateChanged(any(), any(), any())
        }
    }

    @Test
    fun receiverProcessesUserForegroundWhenReactivateIsDisabled() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = true, reactivateOnUserChange = false)

        handler.handle(
            Intent(Intent.ACTION_USER_FOREGROUND),
            MonitorBroadcastSource.RECEIVER,
        )

        coVerify(exactly = 1) {
            eventProcessor.onUserForeground(dispatch = AutomationDispatch.DEFERRED)
        }
    }

    @Test
    fun receiverSkipsUserForegroundWhenServiceHandlesReactivate() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = true, reactivateOnUserChange = true)

        handler.handle(
            Intent(Intent.ACTION_USER_FOREGROUND),
            MonitorBroadcastSource.RECEIVER,
        )

        coVerify(exactly = 0) {
            eventProcessor.onUserForeground(any())
        }
    }

    @Test
    fun receiverSkipsUserForegroundWhenDetectionIsDisabled() = runBlocking {
        mockSettings(detectBluetoothEvents = false, reactivateOnUserChange = true)

        handler.handle(
            Intent(Intent.ACTION_USER_FOREGROUND),
            MonitorBroadcastSource.RECEIVER,
        )

        coVerify(exactly = 0) {
            eventProcessor.onUserForeground(any())
        }
    }

    @Test
    fun serviceSkipsUserForegroundWhenDetectionIsDisabled() = runBlocking {
        MonitorServiceState.setRunning(true)
        mockSettings(detectBluetoothEvents = false, reactivateOnUserChange = true)

        handler.handle(
            Intent(Intent.ACTION_USER_FOREGROUND),
            MonitorBroadcastSource.SERVICE,
        )

        coVerify(exactly = 0) {
            eventProcessor.onUserForeground(any())
        }
    }

    private fun mockSettings(
        detectBluetoothEvents: Boolean = false,
        reactivateOnUserChange: Boolean = false,
    ) {
        coEvery { settingsRepository.snapshot() } returns AppSettings(
            background = BackgroundSettings(
                detectBluetoothEvents = detectBluetoothEvents,
                reactivateOnUserChange = reactivateOnUserChange,
            ),
        )
    }

    private fun stateChangedIntent(): Intent = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
        .putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_TURNING_OFF)

    private companion object {
        const val EVENT_TIME = 1_234L
    }
}
