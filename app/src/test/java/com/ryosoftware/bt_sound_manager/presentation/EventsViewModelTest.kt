package com.ryosoftware.bt_sound_manager.presentation

import app.cash.turbine.test
import app.cash.turbine.TurbineTestContext
import com.ryosoftware.bt_sound_manager.MainDispatcherRule
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.presentation.events.EventsEvent
import com.ryosoftware.bt_sound_manager.presentation.events.EventsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EventsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = object : AppClock {
        override fun nowMillis(): Long = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        override fun elapsedRealtime(): Long = 0
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    private class FakeEventRepository : BluetoothEventRepository {
        val state = MutableStateFlow<List<BluetoothEvent>>(emptyList())
        override fun observeEvents(): Flow<List<BluetoothEvent>> = state
        override suspend fun addEvent(event: BluetoothEvent): Long {
            state.value = state.value + event
            return event.id
        }

        override suspend fun replaceEvent(removedId: Long, event: BluetoothEvent): Long {
            state.value = state.value.filterNot { it.id == removedId } + event
            return event.id
        }

        override suspend fun getLatestEvent(): BluetoothEvent? =
            state.value.maxByOrNull { it.timeUtc }

        override suspend fun getAllEvents(): List<BluetoothEvent> = state.value

        override suspend fun replaceAllEvents(events: List<BluetoothEvent>) {
            state.value = events
        }

        override suspend fun pruneExpired() = Unit

        override suspend fun deleteEvents(ids: Set<Long>) {
            state.value = state.value.filterNot { it.id in ids }
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }

        override suspend fun deleteRange(startUtc: Long, endUtc: Long) {
            state.value = state.value.filterNot { it.timeUtc in startUtc until endUtc }
        }
    }

    private class FakeDeviceRepository : DeviceRepository {
        val names = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun observeDeviceNames(): Flow<Map<String, String>> = names
        override suspend fun getDeviceName(address: String): String? = names.value[address]
        override suspend fun saveDeviceName(address: String, name: String?) {
            names.value = if (name.isNullOrBlank()) {
                names.value - address
            } else {
                names.value + (address to name)
            }
        }
    }

    @Test
    fun groupsEventsByDayAndResolvesNames() = runTest {
        val events = FakeEventRepository()
        val names = FakeDeviceRepository()
        names.names.value = mapOf("AA:BB" to "Headphones")
        events.state.value = listOf(
            event(1, "2026-01-15T10:00:00Z", BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(2, "2026-01-14T10:00:00Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, names, clock)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(2, state.groups.size)
            assertEquals("Headphones", state.availableDevices.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filtersBluetoothStateAndDevices() = runTest {
        val events = FakeEventRepository()
        val names = FakeDeviceRepository()
        events.state.value = listOf(
            event(1, "2026-01-15T10:00:00Z", BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(2, "2026-01-15T10:01:00Z", BluetoothEventType.DEVICE_DISCONNECTED, "CC:DD"),
            event(3, "2026-01-15T10:02:00Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, names, clock)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleBluetoothState()
            viewModel.toggleDevice("AA:BB")
            val filtered = awaitItemUntil { it.visibleCount == 1 }
            assertEquals(1, filtered.visibleCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filtersAudioModeChanges() = runTest {
        val events = FakeEventRepository()
        val names = FakeDeviceRepository()
        events.state.value = listOf(
            event(1, "2026-01-15T10:00:00Z", BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, "AA:BB"),
            event(2, "2026-01-15T10:01:00Z", BluetoothEventType.EXTERNAL_MODE_CHANGED),
            event(3, "2026-01-15T10:02:00Z", BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(4, "2026-01-15T10:03:00Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, names, clock)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleAudioModeChanges()
            val filtered = awaitItemUntil { it.visibleCount == 2 }
            assertEquals(
                setOf(3L, 4L),
                filtered.groups.flatMap { it.events }.map { it.id }.toSet(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deviceFilterHidesAllEventsForThatDevice() = runTest {
        val events = FakeEventRepository()
        val names = FakeDeviceRepository()
        events.state.value = listOf(
            event(1, "2026-01-15T10:00:00Z", BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(2, "2026-01-15T10:01:00Z", BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, "AA:BB"),
            event(3, "2026-01-15T10:02:00Z", BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES),
            event(4, "2026-01-15T10:03:00Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, names, clock)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleDevice("AA:BB")
            val filtered = awaitItemUntil { it.visibleCount == 2 }
            assertEquals(
                setOf(3L, 4L),
                filtered.groups.flatMap { it.events }.map { it.id }.toSet(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteVisibleEmitsAndRemovesOnlyVisibleEvents() = runTest {
        val events = FakeEventRepository()
        val names = FakeDeviceRepository()
        events.state.value = listOf(
            event(1, "2026-01-15T10:00:00Z", BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(2, "2026-01-15T10:01:00Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, names, clock)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleBluetoothState()
            awaitItemUntil { it.visibleCount == 1 }
            viewModel.deleteVisible()
            assertEquals(EventsEvent.Deleted, viewModel.events.first())
            assertEquals(listOf(2L), events.state.value.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteVisibleAlsoRemovesHiddenEvents() = runTest {
        val events = FakeEventRepository()
        events.state.value = listOf(
            event(1, "2026-01-15T10:00:00.000Z", BluetoothEventType.BLUETOOTH_ON),
            event(2, "2026-01-15T10:00:00.100Z", BluetoothEventType.BLUETOOTH_OFF),
            event(3, "2026-01-15T10:00:00.200Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, FakeDeviceRepository(), clock)

        viewModel.uiState.test {
            assertEquals(1, awaitLoaded().visibleCount)
            viewModel.deleteVisible()
            assertEquals(EventsEvent.Deleted, viewModel.events.first())
            assertEquals(emptyList<Long>(), events.state.value.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteDayRemovesFilteredEventsOfThatDayOnly() = runTest {
        val events = FakeEventRepository()
        events.state.value = listOf(
            event(1, "2026-01-14T10:00:00Z", BluetoothEventType.BLUETOOTH_ON),
            event(2, "2026-01-15T10:00:00Z", BluetoothEventType.DEVICE_CONNECTED, "AA:BB"),
            event(3, "2026-01-15T10:01:00Z", BluetoothEventType.BLUETOOTH_ON),
        )
        val viewModel = EventsViewModel(events, FakeDeviceRepository(), clock)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleBluetoothState()
            awaitItemUntil { it.visibleCount == 1 }
            viewModel.deleteDay(LocalDate.parse("2026-01-15"))
            assertEquals(EventsEvent.Deleted, viewModel.events.first())
            assertEquals(listOf(1L), events.state.value.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun TurbineTestContext<com.ryosoftware.bt_sound_manager.presentation.events.EventsUiState>.awaitLoaded(): com.ryosoftware.bt_sound_manager.presentation.events.EventsUiState {
        var item = awaitItem()
        while (item.loading) item = awaitItem()
        return item
    }

    private suspend fun TurbineTestContext<com.ryosoftware.bt_sound_manager.presentation.events.EventsUiState>.awaitItemUntil(
        predicate: (com.ryosoftware.bt_sound_manager.presentation.events.EventsUiState) -> Boolean,
    ): com.ryosoftware.bt_sound_manager.presentation.events.EventsUiState {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    private fun event(
        id: Long,
        time: String,
        type: BluetoothEventType,
        address: String? = null,
    ) = BluetoothEvent(
        id = id,
        timeUtc = Instant.parse(time).toEpochMilli(),
        type = type,
        deviceAddress = address,
        deviceName = null,
    )
}
