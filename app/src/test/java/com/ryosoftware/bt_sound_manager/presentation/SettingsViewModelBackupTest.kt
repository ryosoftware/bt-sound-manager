package com.ryosoftware.bt_sound_manager.presentation

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ryosoftware.bt_sound_manager.MainDispatcherRule
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackupContents
import com.ryosoftware.bt_sound_manager.domain.model.BackupRestoreResult
import com.ryosoftware.bt_sound_manager.domain.model.BackupSelection
import com.ryosoftware.bt_sound_manager.domain.model.BackupSummary
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothSnapshot
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.repository.BackupRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.presentation.settings.SettingsViewModel
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsViewModelBackupTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val serviceController = mockk<MonitorServiceController>(relaxed = true)
    private val automationCoordinator = mockk<AutomationCoordinator>(relaxed = true)
    private val bluetoothRepository = mockk<BluetoothRepository>(relaxed = true)
    private val interruptModeRepository = mockk<InterruptModeRepository>(relaxed = true)

    private fun viewModel(
        settingsRepository: FakeSettingsRepository,
        eventRepository: FakeEventRepository,
        backupRepository: FakeBackupRepository,
        deviceRepository: DeviceRepository = FakeDeviceRepository(),
        appRecreationSignal: AppRecreationSignal = mockk(relaxed = true),
    ): SettingsViewModel {
        every { bluetoothRepository.observeSnapshot() } returns flowOf(
            BluetoothSnapshot(supported = false, enabled = false),
        )
        return SettingsViewModel(
            settingsRepository = settingsRepository,
            serviceController = serviceController,
            interruptModeRepository = interruptModeRepository,
            bluetoothRepository = bluetoothRepository,
            eventRepository = eventRepository,
            backupRepository = backupRepository,
            automationCoordinator = automationCoordinator,
            appRecreationSignal = appRecreationSignal,
            deviceRepository = deviceRepository,
            context = context,
        )
    }

    @Test
    fun restoreEventsOnlyPrunesWithCurrentSettingsAndDoesNotTouchAutomation() = runTest {
        val settings = FakeSettingsRepository(AppSettings())
        val events = FakeEventRepository()
        val backup = FakeBackupRepository(settings, events, backupContents(retentionDays = 30))

        val viewModel = viewModel(settings, events, backup)
        viewModel.inspectBackup("content://backup")
        viewModel.confirmRestore(BackupSelection(events = true, settings = false))

        assertTrue(events.pruned)
        assertEquals(1, events.replacedEvents.size)
        verify(exactly = 0) { serviceController.sync(any()) }
        coVerify(exactly = 0) { automationCoordinator.apply(any(), any(), any(), any()) }
    }

    @Test
    fun restoreSettingsOnlyPrunesWithRestoredRetentionAndReevaluates() = runTest {
        val settings = FakeSettingsRepository(AppSettings())
        val events = FakeEventRepository()
        val backup = FakeBackupRepository(settings, events, backupContents(retentionDays = 30))

        val viewModel = viewModel(settings, events, backup)
        viewModel.inspectBackup("content://backup")
        viewModel.confirmRestore(BackupSelection(events = false, settings = true))

        assertTrue(events.pruned)
        assertEquals(30, settings.state.value.events.retentionDays)
        verify { serviceController.sync(any()) }
        coVerify { automationCoordinator.apply(any(), any(), any(), any()) }
    }

    @Test
    fun restoreSettingsRequestsActivityRecreation() = runTest {
        val settings = FakeSettingsRepository(AppSettings())
        val events = FakeEventRepository()
        val backup = FakeBackupRepository(settings, events, backupContents(retentionDays = 30))
        val recreationSignal = mockk<AppRecreationSignal>(relaxed = true)

        val viewModel = viewModel(
            settingsRepository = settings,
            eventRepository = events,
            backupRepository = backup,
            appRecreationSignal = recreationSignal,
        )
        viewModel.inspectBackup("content://backup")
        viewModel.confirmRestore(BackupSelection(events = false, settings = true))

        verify(exactly = 1) { recreationSignal.request() }
    }

    @Test
    fun restoreEventsOnlyDoesNotRequestActivityRecreation() = runTest {
        val settings = FakeSettingsRepository(AppSettings())
        val events = FakeEventRepository()
        val backup = FakeBackupRepository(settings, events, backupContents(retentionDays = 30))
        val recreationSignal = mockk<AppRecreationSignal>(relaxed = true)

        val viewModel = viewModel(
            settingsRepository = settings,
            eventRepository = events,
            backupRepository = backup,
            appRecreationSignal = recreationSignal,
        )
        viewModel.inspectBackup("content://backup")
        viewModel.confirmRestore(BackupSelection(events = true, settings = false))

        verify(exactly = 0) { recreationSignal.request() }
    }

    @Test
    fun restoreBothSectionsAppliesSettingsBeforePruning() = runTest {
        val settings = FakeSettingsRepository(AppSettings())
        val events = FakeEventRepository()
        val backup = FakeBackupRepository(settings, events, backupContents(retentionDays = 45))

        val viewModel = viewModel(settings, events, backup)
        viewModel.inspectBackup("content://backup")
        viewModel.confirmRestore(BackupSelection(events = true, settings = true))

        assertTrue(events.pruned)
        assertEquals(45, settings.state.value.events.retentionDays)
        assertEquals(1, events.replacedEvents.size)
        verify { serviceController.sync(any()) }
    }

    @Test
    fun emptyBackupStillClearsTheEventTable() = runTest {
        val settings = FakeSettingsRepository(AppSettings())
        val events = FakeEventRepository()
        val backup = FakeBackupRepository(settings, events, backupContents(retentionDays = 7, events = emptyList()))

        val viewModel = viewModel(settings, events, backup)
        viewModel.inspectBackup("content://backup")
        viewModel.confirmRestore(BackupSelection(events = true, settings = false))

        assertTrue(events.replaced)
        assertTrue(events.replacedEvents.isEmpty())
    }

    private fun backupContents(
        retentionDays: Int,
        events: List<BluetoothEvent> = listOf(
            BluetoothEvent(
                timeUtc = Instant.parse("2026-11-19T18:40:03Z").toEpochMilli(),
                type = BluetoothEventType.DEVICE_CONNECTED,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "Headphones",
            ),
        ),
    ): BackupContents = BackupContents(
        createdAtUtcMillis = Instant.parse("2026-11-19T18:42:10Z").toEpochMilli(),
        appVersion = "1.0",
        events = events,
        settings = AppSettings(
            automation = AutomationSettings(enabled = true),
            background = BackgroundSettings(detectBluetoothEvents = true),
            appearance = AppearanceSettings(),
            events = EventsSettings(retentionDays = retentionDays),
        ),
    )

    private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
        val state = MutableStateFlow(initial)

        override val settings: Flow<AppSettings> = state

        override suspend fun snapshot(): AppSettings = state.value

        override suspend fun updateAutomation(
            transform: (AutomationSettings) -> AutomationSettings,
        ) {
            state.value = state.value.copy(automation = transform(state.value.automation))
        }

        override suspend fun updateBackground(
            transform: (BackgroundSettings) -> BackgroundSettings,
        ) {
            state.value = state.value.copy(background = transform(state.value.background))
        }

        override suspend fun updateAppearance(
            transform: (AppearanceSettings) -> AppearanceSettings,
        ) {
            state.value = state.value.copy(appearance = transform(state.value.appearance))
        }

        override suspend fun updateEvents(transform: (EventsSettings) -> EventsSettings) {
            state.value = state.value.copy(events = transform(state.value.events))
        }

        override suspend fun replaceAll(settings: AppSettings) {
            state.value = settings
        }
    }

    private class FakeEventRepository : BluetoothEventRepository {
        var replaced = false
        var replacedEvents: List<BluetoothEvent> = emptyList()
        var pruned = false

        override fun observeEvents(): Flow<List<BluetoothEvent>> = flowOf(emptyList())

        override suspend fun addEvent(event: BluetoothEvent): Long = 0

        override suspend fun replaceEvent(removedId: Long, event: BluetoothEvent): Long = 0

        override suspend fun getLatestEvent(): BluetoothEvent? = null

        override suspend fun getAllEvents(): List<BluetoothEvent> = emptyList()

        override suspend fun replaceAllEvents(events: List<BluetoothEvent>) {
            replaced = true
            replacedEvents = events
        }

        override suspend fun pruneExpired() {
            pruned = true
        }

        override suspend fun deleteEvents(ids: Set<Long>) = Unit

        override suspend fun deleteAll() = Unit

        override suspend fun deleteRange(startUtc: Long, endUtc: Long) = Unit
    }

    private class FakeDeviceRepository : DeviceRepository {
        override fun observeDeviceNames(): Flow<Map<String, String>> = flowOf(emptyMap())

        override suspend fun getDeviceName(address: String): String? = null

        override suspend fun saveDeviceName(address: String, name: String?) = Unit
    }

    private class FakeBackupRepository(
        private val settingsRepository: FakeSettingsRepository,
        private val eventRepository: FakeEventRepository,
        private val contents: BackupContents,
    ) : BackupRepository {

        override fun suggestedFileName(): String = "backup.json"

        override suspend fun export(uri: String): BackupSummary = BackupSummary.from(contents)

        override suspend fun read(uri: String): BackupContents = contents

        override suspend fun restore(
            contents: BackupContents,
            selection: BackupSelection,
        ): BackupRestoreResult {
            if (selection.settings) {
                settingsRepository.replaceAll(contents.settings)
            }
            if (selection.events) {
                eventRepository.replaceAllEvents(contents.events)
            }
            return BackupRestoreResult(
                eventsRestored = if (selection.events) contents.events.size else 0,
                settingsRestored = selection.settings,
            )
        }
    }
}
