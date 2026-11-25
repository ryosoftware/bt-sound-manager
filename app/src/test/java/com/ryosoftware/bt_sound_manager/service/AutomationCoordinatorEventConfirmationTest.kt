package com.ryosoftware.bt_sound_manager.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationRuntimeState
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationApplyScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
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
import io.mockk.verify
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
    private val deviceRepository = mockk<DeviceRepository>(relaxed = true)
    private val interruptModeRepository = mockk<InterruptModeRepository>(relaxed = true)
    private val volumeRepository = mockk<VolumeRepository>(relaxed = true)
    private val notificationRepository = mockk<MonitorNotificationRepository>(relaxed = true)
    private val scheduler = mockk<AutomationScheduler>(relaxed = true)
    private val applyScheduler = mockk<AutomationApplyScheduler>(relaxed = true)

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
            deviceRepository = deviceRepository,
            interruptModeRepository = interruptModeRepository,
            volumeRepository = volumeRepository,
            notificationRepository = notificationRepository,
            statusHolder = AutomationStatusHolder(),
            modeChangeTracker = InterruptModeChangeTracker(clock),
            pendingAutomationEvents = PendingAutomationEventHolder(clock),
            scheduler = scheduler,
            applyScheduler = applyScheduler,
            clock = clock,
        )
        coEvery { settingsRepository.snapshot() } returns settings()
        coEvery { runtimeStateRepository.snapshot() } returns AutomationRuntimeState()
        coEvery { bluetoothRepository.connectedDevices() } returns listOf(device())
        coEvery { deviceRepository.getDeviceName(any()) } returns null
        every { bluetoothRepository.isEnabled() } returns true
        every { bluetoothRepository.hasPermissions() } returns true
        every { interruptModeRepository.canAccessPolicy() } returns true
        every { volumeRepository.currentMediaVolume() } returns -1
        coEvery { eventRepository.addEvent(any()) } returns 0
        coEvery { eventRepository.replaceEvent(any(), any()) } returns 0
        coEvery { eventRepository.getLatestEvent() } returns null
    }

    @Test
    fun insertsAttemptImmediatelyAndConfirmsLater() = runBlocking {
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.NORMAL,
            InterruptMode.DO_NOT_DISTURB,
        )
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true

        coordinator.apply()

        val attempt = slot<BluetoothEvent>()
        coVerify(exactly = 1) { eventRepository.addEvent(capture(attempt)) }
        assertEquals(BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED, attempt.captured.type)
        assertEquals("Headphones", attempt.captured.deviceName)
        assertEquals(InterruptMode.DO_NOT_DISTURB, attempt.captured.mode)
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = true,
            )
        }
        coVerify(exactly = 0) { eventRepository.replaceEvent(any(), any()) }
        coVerify(exactly = 0) { runtimeStateRepository.saveAppliedMode(any(), any()) }

        nowMillis = 2_000
        coordinator.onInterruptModeChanged()

        val confirmed = slot<BluetoothEvent>()
        coVerify(exactly = 1) {
            eventRepository.replaceEvent(removedId = 0L, event = capture(confirmed))
        }
        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, confirmed.captured.type)
        assertEquals(InterruptMode.DO_NOT_DISTURB, confirmed.captured.mode)
        assertEquals(2_000L, confirmed.captured.timeUtc)
        coVerify(exactly = 1) { eventRepository.addEvent(any()) }
        coVerify {
            runtimeStateRepository.saveAppliedMode(
                mode = InterruptMode.DO_NOT_DISTURB,
                elapsedRealtimeTics = any(),
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = false,
            )
        }
    }

    @Test
    fun timeoutTransformsAttemptIntoFailure() = runBlocking {
        every { interruptModeRepository.currentMode() } returns InterruptMode.NORMAL
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true

        coordinator.apply()
        nowMillis = 7_000

        coordinator.onAttemptTimeout()

        val failure = slot<BluetoothEvent>()
        coVerify(exactly = 1) {
            eventRepository.replaceEvent(removedId = 0L, event = capture(failure))
        }
        assertEquals(BluetoothEventType.AUTOMATION_FAILED_CONNECTED, failure.captured.type)
        assertEquals(InterruptMode.DO_NOT_DISTURB, failure.captured.mode)
        assertEquals(7_000L, failure.captured.timeUtc)
        coVerify(exactly = 0) { runtimeStateRepository.saveAppliedMode(any(), any()) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = ApplicationProvider.getApplicationContext<Context>()
                    .getString(R.string.notification_automation_apply_failed),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
            )
        }
    }

    @Test
    fun timeoutWithMatchingModeConfirmsInsteadOfFailing() = runBlocking {
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.NORMAL,
            InterruptMode.DO_NOT_DISTURB,
        )
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true

        coordinator.apply()
        nowMillis = 7_000

        coordinator.onAttemptTimeout()

        val confirmed = slot<BluetoothEvent>()
        coVerify(exactly = 1) {
            eventRepository.replaceEvent(removedId = 0L, event = capture(confirmed))
        }
        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, confirmed.captured.type)
        coVerify {
            runtimeStateRepository.saveAppliedMode(
                mode = InterruptMode.DO_NOT_DISTURB,
                elapsedRealtimeTics = any(),
            )
        }
    }

    @Test
    fun setModeFailureTransformsAttemptIntoFailure() = runBlocking {
        every { interruptModeRepository.currentMode() } returns InterruptMode.NORMAL
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns false

        coordinator.apply()

        val failure = slot<BluetoothEvent>()
        coVerify(exactly = 1) {
            eventRepository.replaceEvent(removedId = 0L, event = capture(failure))
        }
        assertEquals(BluetoothEventType.AUTOMATION_FAILED_CONNECTED, failure.captured.type)
        coVerify(exactly = 0) { runtimeStateRepository.saveAppliedMode(any(), any()) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = ApplicationProvider.getApplicationContext<Context>()
                    .getString(R.string.notification_automation_apply_failed),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
            )
        }
    }

    @Test
    fun externalChangeDiscardsPendingAutomationEvent() = runBlocking {
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.NORMAL,
            InterruptMode.SILENT_ALL,
        )
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true

        coordinator.apply()
        nowMillis = 2_000

        coordinator.onInterruptModeChanged()

        coVerify(exactly = 1) {
            eventRepository.addEvent(
                match {
                    it.type == BluetoothEventType.EXTERNAL_MODE_CHANGED &&
                        it.mode == InterruptMode.SILENT_ALL
                },
            )
        }
        coVerify(exactly = 0) { eventRepository.replaceEvent(any(), any()) }
    }

    @Test
    fun blockedConnectionLogsFailureAndNamesDeviceInNotification() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        every { interruptModeRepository.currentMode() } returns InterruptMode.SILENT_ALL
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = context.getString(
            R.string.notification_automation_not_applied_connected,
            "Headphones",
        )

        coordinator.apply(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceConnected = true,
        )

        coVerify(exactly = 1) {
            eventRepository.addEvent(
                match {
                    it.type == BluetoothEventType.AUTOMATION_FAILED_CONNECTED &&
                        it.mode == InterruptMode.DO_NOT_DISTURB &&
                        it.deviceName == "Headphones"
                },
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = null,
                body = expected,
                showToggleAutomationAction = false,
                showEnableBluetoothAction = false,
            )
        }
    }

    @Test
    fun blockedFailureIsDeduplicatedWhenLatestEventMatches() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        every { interruptModeRepository.currentMode() } returns InterruptMode.SILENT_ALL
        coEvery { eventRepository.getLatestEvent() } returns BluetoothEvent(
            timeUtc = 500,
            type = BluetoothEventType.AUTOMATION_FAILED_CONNECTED,
            deviceName = "Headphones",
            mode = InterruptMode.DO_NOT_DISTURB,
        )

        coordinator.apply()

        coVerify(exactly = 0) { eventRepository.addEvent(any()) }
    }

    @Test
    fun insufficientPermissionsLogsFailureEvent() = runBlocking {
        every { interruptModeRepository.canAccessPolicy() } returns false
        every { interruptModeRepository.currentMode() } returns null

        coordinator.apply()

        coVerify(exactly = 1) {
            eventRepository.addEvent(
                match {
                    it.type == BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS &&
                        it.mode == InterruptMode.DO_NOT_DISTURB
                },
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = null,
                body = ApplicationProvider.getApplicationContext<Context>()
                    .getString(R.string.notification_automation_no_permissions),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                showPolicySettingsAction = true,
            )
        }
    }

    @Test
    fun alreadyCurrentShowsStatusNotificationWithoutEvent() = runBlocking {
        every { interruptModeRepository.currentMode() } returns InterruptMode.DO_NOT_DISTURB

        coordinator.apply()

        coVerify(exactly = 0) { eventRepository.addEvent(any()) }
        coVerify {
            runtimeStateRepository.saveAppliedMode(
                mode = InterruptMode.DO_NOT_DISTURB,
                elapsedRealtimeTics = any(),
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = false,
            )
        }
    }

    @Test
    fun blockedDisconnectionNotificationNamesDevice() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        coEvery { bluetoothRepository.connectedDevices() } returns emptyList()
        coEvery { deviceRepository.getDeviceName("AA:BB:CC:DD:EE:FF") } returns "Headphones"
        every { interruptModeRepository.currentMode() } returns InterruptMode.SILENT_ALL
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = context.getString(
            R.string.notification_automation_not_applied_disconnected,
            "Headphones",
        )

        coordinator.apply(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceConnected = false,
        )

        coVerify(exactly = 1) {
            eventRepository.addEvent(
                match {
                    it.type == BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES &&
                        it.mode == InterruptMode.NORMAL
                },
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = null,
                body = expected,
                showToggleAutomationAction = false,
                showEnableBluetoothAction = false,
            )
        }
    }

    @Test
    fun blockedNotificationExplainsBluetoothOff() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        coEvery { bluetoothRepository.connectedDevices() } returns emptyList()
        every { bluetoothRepository.isEnabled() } returns false
        every { interruptModeRepository.currentMode() } returns InterruptMode.SILENT_ALL
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = context.getString(
            R.string.notification_automation_not_applied_bluetooth_off,
        )

        coordinator.apply()

        coVerify(exactly = 1) {
            eventRepository.addEvent(
                match {
                    it.type == BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF &&
                        it.mode == InterruptMode.NORMAL
                },
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = null,
                body = expected,
                showToggleAutomationAction = false,
                showEnableBluetoothAction = true,
            )
        }
    }

    @Test
    fun blockedNotificationUsesGenericTextWithoutEvent() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        every { interruptModeRepository.currentMode() } returns InterruptMode.SILENT_ALL
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = context.getString(
            R.string.notification_automation_not_applied_connected_generic,
        )

        coordinator.apply()

        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = null,
                body = expected,
                showToggleAutomationAction = false,
                showEnableBluetoothAction = false,
            )
        }
    }

    @Test
    fun blockedNotificationIsSuppressedWhenRequested() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        every { interruptModeRepository.currentMode() } returns InterruptMode.SILENT_ALL

        coordinator.apply(suppressBlockedNotification = true)

        verify(exactly = 0) {
            notificationRepository.showAutomationNotification(
                mode = any(),
                body = any(),
                showToggleAutomationAction = any(),
                showEnableBluetoothAction = any(),
                showPolicySettingsAction = any(),
                attempting = any(),
            )
        }
        coVerify(exactly = 0) { eventRepository.addEvent(any()) }
        verify(exactly = 1) { scheduler.scheduleNextTransition(any()) }
    }

    @Test
    fun externalModeWithoutDevicesReappliesConnectedModeWhenForced() = runBlocking {
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.NORMAL,
            InterruptMode.NORMAL,
            InterruptMode.DO_NOT_DISTURB,
        )
        every { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) } returns true
        val context = ApplicationProvider.getApplicationContext<Context>()

        coordinator.onInterruptModeChanged()

        coVerify(exactly = 1) {
            eventRepository.addEvent(
                match {
                    it.type == BluetoothEventType.EXTERNAL_MODE_CHANGED &&
                        it.mode == InterruptMode.NORMAL
                },
            )
        }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.NORMAL,
                body = context.getString(R.string.notification_automation_external_change),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
            )
        }
        verify(exactly = 1) { interruptModeRepository.setMode(InterruptMode.DO_NOT_DISTURB) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = true,
            )
        }
        coVerify(exactly = 0) { eventRepository.replaceEvent(any(), any()) }

        nowMillis = 2_000
        coordinator.onInterruptModeChanged()

        coVerify(exactly = 1) { eventRepository.replaceEvent(any(), any()) }
        verify {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = false,
            )
        }
    }

    @Test
    fun externalModeWithDevicesReappliesNoneModeWhenForced() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(
            forceConnected = false,
            forceDisconnected = true,
        )
        coEvery { bluetoothRepository.connectedDevices() } returns emptyList()
        every { interruptModeRepository.currentMode() } returnsMany listOf(
            InterruptMode.DO_NOT_DISTURB,
            InterruptMode.DO_NOT_DISTURB,
            InterruptMode.NORMAL,
        )
        every { interruptModeRepository.setMode(InterruptMode.NORMAL) } returns true
        val context = ApplicationProvider.getApplicationContext<Context>()

        coordinator.onInterruptModeChanged()

        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = context.getString(R.string.notification_automation_external_change),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
            )
        }
        verify(exactly = 1) { interruptModeRepository.setMode(InterruptMode.NORMAL) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.NORMAL,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = true,
            )
        }

        nowMillis = 2_000
        coordinator.onInterruptModeChanged()

        coVerify(exactly = 1) { eventRepository.replaceEvent(any(), any()) }
        verify {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.NORMAL,
                body = any(),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                attempting = false,
            )
        }
    }

    @Test
    fun externalModeWithoutDevicesKeepsExternalNotificationWithoutForce() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(forceConnected = false)
        every { interruptModeRepository.currentMode() } returns InterruptMode.NORMAL
        val context = ApplicationProvider.getApplicationContext<Context>()

        coordinator.onInterruptModeChanged()

        verify(exactly = 0) { interruptModeRepository.setMode(any()) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.NORMAL,
                body = context.getString(R.string.notification_automation_external_change),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                showPolicySettingsAction = false,
            )
        }
    }

    @Test
    fun externalModeWithDevicesKeepsExternalNotificationWithoutForce() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(
            forceConnected = true,
            forceDisconnected = false,
        )
        coEvery { bluetoothRepository.connectedDevices() } returns emptyList()
        every { interruptModeRepository.currentMode() } returns InterruptMode.DO_NOT_DISTURB
        val context = ApplicationProvider.getApplicationContext<Context>()

        coordinator.onInterruptModeChanged()

        verify(exactly = 0) { interruptModeRepository.setMode(any()) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = InterruptMode.DO_NOT_DISTURB,
                body = context.getString(R.string.notification_automation_external_change),
                showToggleAutomationAction = true,
                showEnableBluetoothAction = false,
                showPolicySettingsAction = false,
            )
        }
    }

    @Test
    fun externalReapplyRespectsSchedule() = runBlocking {
        coEvery { settingsRepository.snapshot() } returns settings(allDay = false)
        every { interruptModeRepository.currentMode() } returns InterruptMode.NORMAL
        val context = ApplicationProvider.getApplicationContext<Context>()

        coordinator.onInterruptModeChanged()

        verify(exactly = 0) { interruptModeRepository.setMode(any()) }
        verify(exactly = 1) {
            notificationRepository.showAutomationNotification(
                mode = null,
                body = context.getString(R.string.notification_automation_outside_schedule),
                showToggleAutomationAction = false,
                showEnableBluetoothAction = false,
                showPolicySettingsAction = false,
            )
        }
    }

    private fun settings(
        forceConnected: Boolean = true,
        forceDisconnected: Boolean = false,
        allDay: Boolean = true,
    ) = AppSettings(
        automation = AutomationSettings(
            enabled = true,
            allDay = allDay,
            monitorAllDevices = true,
            modeWhenConnected = InterruptMode.DO_NOT_DISTURB,
            modeWhenNone = InterruptMode.NORMAL,
            forceModeOnDevicesConnected = forceConnected,
            forceModeOnDevicesDisconnected = forceDisconnected,
        ),
    )

    private fun device() = BluetoothDeviceInfo(
        address = "AA:BB:CC:DD:EE:FF",
        name = "Headphones",
        isConnected = true,
        isBonded = true,
    )
}
