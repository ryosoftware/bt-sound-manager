package com.ryosoftware.bt_sound_manager.presentation.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.core.util.displayDeviceName
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.EventDayGroup
import com.ryosoftware.bt_sound_manager.domain.usecase.EventGrouping
import com.ryosoftware.bt_sound_manager.domain.usecase.EventMinimizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EventFilter(
    val hideBluetoothState: Boolean = false,
    val hideAudioModeChanges: Boolean = false,
    val hiddenDeviceAddresses: Set<String> = emptySet(),
) {
    fun isVisible(event: BluetoothEvent): Boolean {
        val address = event.deviceAddress
        if (address != null && address in hiddenDeviceAddresses) return false
        return when (event.type) {
            BluetoothEventType.BLUETOOTH_ON,
            BluetoothEventType.BLUETOOTH_OFF,
            -> !hideBluetoothState

            BluetoothEventType.DEVICE_CONNECTED,
            BluetoothEventType.DEVICE_DISCONNECTED,
            -> true

            else -> !hideAudioModeChanges
        }
    }
}

data class DeviceFilterOption(
    val address: String,
    val name: String,
)

data class EventsUiState(
    val loading: Boolean = true,
    val groups: List<EventDayGroup> = emptyList(),
    val visibleIds: Set<Long> = emptySet(),
    val visibleCount: Int = 0,
    val totalCount: Int = 0,
    val filter: EventFilter = EventFilter(),
    val availableDevices: List<DeviceFilterOption> = emptyList(),
    val deviceNames: Map<String, String> = emptyMap(),
)

sealed interface EventsEvent {
    data object Deleted : EventsEvent
    data object DeleteError : EventsEvent
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: BluetoothEventRepository,
    private val deviceRepository: DeviceRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val filter = MutableStateFlow(EventFilter())
    private val eventsChannel = Channel<EventsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<EventsUiState> = combine(
        eventRepository.observeEvents(),
        deviceRepository.observeDeviceNames(),
        filter,
    ) { rawEvents, names, currentFilter ->
        val minimized = EventMinimizer.minimize(rawEvents)
        val visible = minimized.filter(currentFilter::isVisible)
        val devices = minimized
            .mapNotNull { event ->
                val address = event.deviceAddress ?: return@mapNotNull null
                DeviceFilterOption(
                    address = address,
                    name = displayDeviceName(names[address], event.deviceName, address),
                )
            }
            .distinctBy { it.address }
            .sortedBy { it.name.lowercase() }
        EventsUiState(
            loading = false,
            groups = EventGrouping.group(visible, clock.zoneId()),
            visibleIds = visible.map { it.id }.toSet(),
            visibleCount = visible.size,
            totalCount = minimized.size,
            filter = currentFilter,
            availableDevices = devices,
            deviceNames = names,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EventsUiState(),
    )

    fun toggleBluetoothState() {
        filter.value = filter.value.let { current ->
            current.copy(hideBluetoothState = !current.hideBluetoothState)
        }
    }

    fun toggleAudioModeChanges() {
        filter.value = filter.value.let { current ->
            current.copy(hideAudioModeChanges = !current.hideAudioModeChanges)
        }
    }

    fun toggleDevice(address: String) {
        filter.value = filter.value.let { current ->
            current.copy(
                hiddenDeviceAddresses = if (address in current.hiddenDeviceAddresses) {
                    current.hiddenDeviceAddresses - address
                } else {
                    current.hiddenDeviceAddresses + address
                },
            )
        }
    }

    fun showAll() {
        filter.value = EventFilter()
    }

    fun hideAll() {
        val state = uiState.value
        filter.value = EventFilter(
            hideBluetoothState = true,
            hideAudioModeChanges = true,
            hiddenDeviceAddresses = state.availableDevices.map { it.address }.toSet(),
        )
    }

    fun deleteVisible() {
        val ids = uiState.value.visibleIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                eventRepository.deleteEvents(ids)
                eventsChannel.send(EventsEvent.Deleted)
            } catch (_: Exception) {
                eventsChannel.send(EventsEvent.DeleteError)
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            try {
                eventRepository.deleteAll()
                eventsChannel.send(EventsEvent.Deleted)
            } catch (_: Exception) {
                eventsChannel.send(EventsEvent.DeleteError)
            }
        }
    }

    fun deleteDay(date: LocalDate) {
        val ids = uiState.value.groups
            .firstOrNull { it.date == date }
            ?.events
            ?.mapNotNull { event -> event.id.takeIf { it > 0 } }
            ?.toSet()
            ?: emptySet()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                eventRepository.deleteEvents(ids)
                eventsChannel.send(EventsEvent.Deleted)
            } catch (_: Exception) {
                eventsChannel.send(EventsEvent.DeleteError)
            }
        }
    }
}
