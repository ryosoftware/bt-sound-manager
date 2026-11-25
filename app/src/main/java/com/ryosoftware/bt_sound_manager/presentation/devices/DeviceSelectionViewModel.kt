package com.ryosoftware.bt_sound_manager.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.RefreshDeviceNames
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectableDevice(
    val address: String,
    val name: String,
    val connected: Boolean,
    val bonded: Boolean,
)

data class DeviceSelectionUiState(
    val loading: Boolean = true,
    val devices: List<SelectableDevice> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
)

sealed interface DeviceSelectionEvent {
    data object Saved : DeviceSelectionEvent
    data object Error : DeviceSelectionEvent
}

@HiltViewModel
class DeviceSelectionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val deviceRepository: DeviceRepository,
    private val refreshDeviceNames: RefreshDeviceNames,
    private val automationCoordinator: AutomationCoordinator,
) : ViewModel() {

    private val devices = MutableStateFlow<List<SelectableDevice>>(emptyList())
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)
    private val eventsChannel = Channel<DeviceSelectionEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private var isSaving = false

    val uiState: StateFlow<DeviceSelectionUiState> = combine(
        devices,
        selected,
        query,
        loading,
        bluetoothRepository.observeConnectedDevices(),
    ) { baseDevices, selectedDevices, search, isLoading, liveConnectedDevices ->
        val liveConnected = liveConnectedDevices.associateBy { it.address }
        val merged = linkedMapOf<String, SelectableDevice>()
        baseDevices.forEach { device ->
            merged[device.address] = device.copy(
                connected = liveConnected.containsKey(device.address),
            )
        }
        liveConnected.values.forEach { info ->
            merged.putIfAbsent(
                info.address,
                SelectableDevice(
                    address = info.address,
                    name = info.name,
                    connected = true,
                    bonded = info.isBonded,
                ),
            )
        }
        selectedDevices.forEach { address ->
            merged.putIfAbsent(
                address,
                SelectableDevice(
                    address = address,
                    name = address,
                    connected = false,
                    bonded = false,
                ),
            )
        }
        DeviceSelectionUiState(
            loading = isLoading,
            devices = merged.values
                .filter {
                    search.isBlank() ||
                        it.name.contains(search, ignoreCase = true) ||
                        it.address.contains(search, ignoreCase = true)
                }
                .sortedBy { it.name.lowercase() },
            selected = selectedDevices,
            query = search,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceSelectionUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            try {
                val bluetoothDevices = runCatching { refreshDeviceNames() }
                    .getOrDefault(emptyList())
                val settings = settingsRepository.snapshot()
                selected.value = settings.automation.selectedDeviceAddresses
                    .mapNotNull(::normalizeAddress)
                    .toSet()

                val names = deviceRepository.observeDeviceNames().first()
                devices.value = mergeSelectableDevices(
                    bluetoothDevices = bluetoothDevices,
                    cachedNames = names,
                    selectedAddresses = selected.value,
                )
            } finally {
                loading.value = false
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleDevice(address: String) {
        val normalized = normalizeAddress(address) ?: return
        selected.value = if (normalized in selected.value) {
            selected.value - normalized
        } else {
            selected.value + normalized
        }
    }

    fun save() {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                val normalized = selected.value.mapNotNull(::normalizeAddress).toSet()
                settingsRepository.updateAutomation {
                    it.copy(selectedDeviceAddresses = normalized)
                }
                automationCoordinator.apply()
                eventsChannel.send(DeviceSelectionEvent.Saved)
            } catch (_: Exception) {
                isSaving = false
                eventsChannel.send(DeviceSelectionEvent.Error)
            }
        }
    }
}
