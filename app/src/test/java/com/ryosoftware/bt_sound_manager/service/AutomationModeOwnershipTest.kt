package com.ryosoftware.bt_sound_manager.service

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.data.local.prefs.RuntimeStateDataStore
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationModeOwnershipTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val normal = InterruptMode.NORMAL
    private val dnd = InterruptMode.DO_NOT_DISTURB

    private var mode = normal
    private var connected = false
    private var automation = AutomationSettings(enabled = true, monitorAllDevices = true)

    private val clock = object : AppClock {
        override fun nowMillis(): Long = 1_000L
        override fun elapsedRealtime(): Long = 1_000L
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val runtime by lazy {
        RuntimeStateDataStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                folder.root.resolve("runtime.preferences_pb")
            },
        )
    }
    private val settingsRepository = mockk<SettingsRepository> {
        coEvery { snapshot() } answers { AppSettings(automation = automation) }
    }
    private val bluetoothRepository = mockk<BluetoothRepository> {
        every { hasPermissions() } returns true
        every { isEnabled() } returns true
        coEvery { connectedDevices() } answers {
            if (connected) {
                listOf(BluetoothDeviceInfo("AA:BB", "Headphones", true, true))
            } else {
                emptyList()
            }
        }
    }
    private val interruptModeRepository = mockk<InterruptModeRepository> {
        every { canAccessPolicy() } returns true
        every { currentMode() } answers { mode }
        every { setMode(any()) } answers {
            mode = firstArg()
            true
        }
    }

    /** A new instance stands for a new process: only [runtime] is kept. */
    private fun newCoordinator() = AutomationCoordinator(
        context = ApplicationProvider.getApplicationContext<Context>(),
        settingsRepository = settingsRepository,
        runtimeStateRepository = runtime,
        eventRepository = mockk(relaxed = true),
        bluetoothRepository = bluetoothRepository,
        deviceRepository = mockk(relaxed = true),
        interruptModeRepository = interruptModeRepository,
        volumeRepository = mockk(relaxed = true),
        notificationRepository = mockk(relaxed = true),
        statusHolder = AutomationStatusHolder(),
        modeChangeTracker = InterruptModeChangeTracker(clock),
        pendingAutomationEvents = PendingAutomationEventHolder(clock),
        scheduler = mockk(relaxed = true),
        applyScheduler = mockk(relaxed = true),
        clock = clock,
    )

    private val coordinator by lazy { newCoordinator() }

    @After
    fun tearDown() = scope.cancel()

    private suspend fun connect(value: Boolean, on: AutomationCoordinator = coordinator) {
        connected = value
        on.apply()
        on.onInterruptModeChanged()
    }

    private suspend fun userSets(value: InterruptMode) {
        mode = value
        coordinator.onInterruptModeChanged()
    }

    @Test
    fun keepsModeThatTheUserHadAlreadySet() = runBlocking {
        connect(false)
        userSets(dnd)
        connect(true)
        connect(false)

        assertEquals(dnd, mode)
    }

    @Test
    fun revertsModeSetByTheApp() = runBlocking {
        connect(false)
        connect(true)
        assertEquals(dnd, mode)
        connect(false)

        assertEquals(normal, mode)
    }

    @Test
    fun losesOwnershipWhenTheUserTogglesTheMode() = runBlocking {
        connect(false)
        connect(true)
        assertEquals(dnd, mode)
        userSets(normal)
        userSets(dnd)
        connect(false)

        assertEquals(dnd, mode)
    }

    @Test
    fun losesOwnershipWhenAChangeIsOnlySeenOnTheNextApply() = runBlocking {
        connect(false)
        connect(true)
        assertEquals(dnd, mode)
        mode = InterruptMode.SILENT_ALL
        coordinator.apply()
        mode = dnd
        connect(false)

        assertEquals(dnd, mode)
    }

    @Test
    fun keepsOwnershipAfterReboot() = runBlocking {
        connect(false)
        connect(true)
        assertEquals(dnd, mode)
        val rebooted = newCoordinator()
        rebooted.onBoot()
        connect(false, rebooted)

        assertEquals(normal, mode)
    }

    @Test
    fun recoversOwnershipIfTheProcessDiesBeforeConfirming() = runBlocking {
        connect(false)
        connected = true
        coordinator.apply()
        val restarted = newCoordinator()
        restarted.onAttemptTimeout()
        connect(false, restarted)

        assertEquals(normal, mode)
    }

    @Test
    fun stillAppliesConnectedModeAfterTheUserTogglesModesWithNoDevices() = runBlocking {
        connect(false)
        userSets(dnd)
        userSets(normal)
        connect(true)
        assertEquals(dnd, mode)
        connect(false)

        assertEquals(normal, mode)
    }

    @Test
    fun forcedDirectionAppliesEvenOverAModeOfTheUser() = runBlocking {
        automation = automation.copy(forceModeOnDevicesDisconnected = true)
        connect(false)
        userSets(dnd)
        connect(true)
        connect(false)

        assertEquals(normal, mode)
    }
}
