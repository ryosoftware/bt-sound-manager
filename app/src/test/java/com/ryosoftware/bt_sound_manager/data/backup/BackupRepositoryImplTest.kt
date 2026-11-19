package com.ryosoftware.bt_sound_manager.data.backup

import android.content.Context
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackupContents
import com.ryosoftware.bt_sound_manager.domain.model.BackupSelection
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRepositoryImplTest {

    private val clock = object : AppClock {
        override fun nowMillis(): Long = Instant.parse("2026-11-19T18:42:10Z").toEpochMilli()
        override fun elapsedRealtime(): Long = 0
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    private fun repository(
        events: FakeEventRepository = FakeEventRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): BackupRepositoryImpl = BackupRepositoryImpl(
        context = mockk<Context>(relaxed = true),
        eventRepository = events,
        settingsRepository = settings,
        clock = clock,
        codec = BackupJsonCodec(),
    )

    @Test
    fun restoresOnlyTheSelectedEvents() = runTest {
        val events = FakeEventRepository()
        val settings = FakeSettingsRepository()
        val contents = backupContents()

        val result = repository(events, settings).restore(
            contents = contents,
            selection = BackupSelection(events = true, settings = false),
        )

        assertEquals(contents.events.size, result.eventsRestored)
        assertFalse(result.settingsRestored)
        assertEquals(contents.events, events.replacedEvents)
        assertNull(settings.replacedSettings)
    }

    @Test
    fun restoresOnlyTheSelectedSettings() = runTest {
        val events = FakeEventRepository()
        val settings = FakeSettingsRepository()
        val contents = backupContents()

        val result = repository(events, settings).restore(
            contents = contents,
            selection = BackupSelection(events = false, settings = true),
        )

        assertEquals(0, result.eventsRestored)
        assertTrue(result.settingsRestored)
        assertNull(events.replacedEvents)
        assertEquals(contents.settings, settings.replacedSettings)
    }

    @Test
    fun restoresBothSections() = runTest {
        val events = FakeEventRepository()
        val settings = FakeSettingsRepository()
        val contents = backupContents()

        val result = repository(events, settings).restore(
            contents = contents,
            selection = BackupSelection(events = true, settings = true),
        )

        assertEquals(contents.events.size, result.eventsRestored)
        assertTrue(result.settingsRestored)
        assertNotNull(events.replacedEvents)
        assertNotNull(settings.replacedSettings)
    }

    @Test
    fun suggestedFileNameUsesTheClockAndTheJsonExtension() {
        val name = repository().suggestedFileName()

        assertEquals("bt-sound-manager-2026-11-19-184210.json", name)
    }

    private fun backupContents(): BackupContents = BackupContents(
        createdAtUtcMillis = clock.nowMillis(),
        appVersion = "1.0",
        events = listOf(
            BluetoothEvent(
                timeUtc = clock.nowMillis(),
                type = BluetoothEventType.DEVICE_CONNECTED,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "Headphones",
            ),
        ),
        settings = AppSettings(
            automation = AutomationSettings(enabled = true),
            background = BackgroundSettings(detectBluetoothEvents = true),
            appearance = AppearanceSettings(),
            events = EventsSettings(retentionDays = 14),
        ),
    )

    private class FakeEventRepository : BluetoothEventRepository {
        var replacedEvents: List<BluetoothEvent>? = null
        private val state = MutableStateFlow<List<BluetoothEvent>>(emptyList())

        override fun observeEvents(): Flow<List<BluetoothEvent>> = state

        override suspend fun addEvent(event: BluetoothEvent): Long = 0

        override suspend fun getAllEvents(): List<BluetoothEvent> = state.value

        override suspend fun replaceAllEvents(events: List<BluetoothEvent>) {
            replacedEvents = events
            state.value = events
        }

        override suspend fun pruneExpired() = Unit

        override suspend fun deleteEvents(ids: Set<Long>) = Unit

        override suspend fun deleteAll() {
            state.value = emptyList()
        }

        override suspend fun deleteRange(startUtc: Long, endUtc: Long) = Unit
    }

    private class FakeSettingsRepository : SettingsRepository {
        var replacedSettings: AppSettings? = null
        private val state = MutableStateFlow(AppSettings())

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
            replacedSettings = settings
            state.value = settings
        }
    }
}
