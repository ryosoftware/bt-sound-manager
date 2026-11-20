package com.ryosoftware.bt_sound_manager.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationRuntimeState
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.MonitorNotificationRepository
import com.ryosoftware.bt_sound_manager.domain.repository.RuntimeStateRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.repository.VolumeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationCoordinatorEventConfirmationTest {

    private var nowMillis = 1_000L
    private val clock = object : AppClock {
        override fun nowMillis(): Long = nowMillis

        override fun elapsedRealtime(): Long = nowMillis

        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val runtimeStateRepository = mockk<RuntimeStateRepository>(relaxed = true)
    private val eventRepository = mockk<BluetoothEventRepository>(relaxed = true)
    private val bluetoothRepository = mockk<BluetoothRepository>(relaxed = true)
    private val interruptModeRepository = mockk<InterruptModeRepository>(relaxed = true)
    private val volumeRepository = mockk<VolumeRepository>(relaxed = true)
    private val notificationRepository = mockk<MonitorNotificationRepository>(relaxed = true)
    private val scheduler = mockk<AutomationScheduler>(relaxed = true)

    private lateinit var coordinator: AutomationCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        coordinator = AutomationCoordinator(
            context = context,
            settingsRepository = settingsRepository,
            runtimeStateRepository = runtimeStateRepository,
            eventRepository = eventRepository,
            bluetoothRepository = bluetoothRepository,
            interruptModeRepository = interruptModeRepository,
            volumeRepository = volumeRepository,
            notificationRepository = notificationRepository,
            statusHolder = AutomationStatusHolder(),
            modeChangeTracker = InterruptModeChangeTracker(clock),
            pendingAutomationEvents = PendingAutomationEventHolder(clock),
            scheduler = scheduler,
            clock = clock,
        )
        coEvery { settingsRepository.snapshot() } returns settings()
        coEvery { runtimeStateRepository.snapshot() } returns AutomationRuntimeState()
        coEvery { bluetoothRepository.connectedDevices() } returns listOf(device())
        every { bluetoothRepository.isEnabled() } returns true
        every { interruptModeRepository.canAccessPolicy() } returns true
        every { volumeRepository.currentMediaVolume() } returns -1
        coEvery { eventRepository.addEvent(any()) } returns 0
    }

    @Test
    fun insertsAutomationEventOnlyAfterConfirmation() = runBlocking {
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.NORMAL,
            InterruptMode.DO_NOT_DISTURB,
        )
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true

        coordinator.apply()

        coVerify(exactly = 0) { eventRepository.addEvent(any()) }
        nowMillis = 2_000

        coordinator.onInterruptModeChanged()

        val event = slot<BluetoothEvent>()
        coVerify(exactly = 1) { eventRepository.addEvent(capture(event)) }
        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, event.captured.type)
        assertEquals(InterruptMode.DO_NOT_DISTURB, event.captured.mode)
        assertEquals(2_000, event.captured.timeUtc)
    }

    @Test
    fun externalChangeDiscardsPendingAutomationEvent() = runBlocking {
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.NORMAL,
            InterruptMode.SILENT_ALL,
        )
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true

        coordinator.apply()
        coVerify(exactly = 0) { eventRepository.addEvent(any()) }
        nowMillis = 2_000

        coordinator.onInterruptModeChanged()

        val event = slot<BluetoothEvent>()
        coVerify(exactly = 1) { eventRepository.addEvent(capture(event)) }
        assertEquals(BluetoothEventType.EXTERNAL_MODE_CHANGED, event.captured.type)
        assertEquals(InterruptMode.SILENT_ALL, event.captured.mode)
    }

    private fun settings() = AppSettings(
        automation = AutomationSettings(
            enabled = true,
            allDay = true,
            monitorAllDevices = true,
            modeWhenConnected = InterruptMode.DO_NOT_DISTURB,
            modeWhenNone = InterruptMode.NORMAL,
        ),
    )

    private fun device() = BluetoothDeviceInfo(
        address = "AA:BB:CC:DD:EE:FF",
        name = "Headphones",
        isConnected = true,
        isBonded = true,
    )
}
