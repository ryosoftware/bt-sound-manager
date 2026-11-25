package com.ryosoftware.bt_sound_manager.presentation.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutomationUiState(
    val loading: Boolean = true,
    val automation: AutomationSettings = AutomationSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    val deviceNames: Map<String, String> = emptyMap(),
    val policyAccess: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
)

sealed interface AutomationEvent {
    data object PolicyRequired : AutomationEvent
    data object ModeConflict : AutomationEvent
    data object Error : AutomationEvent
}

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository,
    private val interruptModeRepository: InterruptModeRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val automationCoordinator: AutomationCoordinator,
    private val serviceController: MonitorServiceController,
) : ViewModel() {

    private val policyAccess = MutableStateFlow(false)
    private val bluetoothPermissionGranted = MutableStateFlow(false)
    private val eventsChannel = Channel<AutomationEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<AutomationUiState> = combine(
        settingsRepository.settings,
        deviceRepository.observeDeviceNames(),
        policyAccess,
        bluetoothPermissionGranted,
    ) { settings, names, access, bluetoothGranted ->
        AutomationUiState(
            loading = false,
            automation = settings.automation,
            background = settings.background,
            deviceNames = names,
            policyAccess = access,
            bluetoothPermissionGranted = bluetoothGranted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutomationUiState(),
    )

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        policyAccess.value = interruptModeRepository.canAccessPolicy()
        bluetoothPermissionGranted.value = bluetoothRepository.hasPermissions()
    }

    fun setEnabled(value: Boolean) {
        if (value && !bluetoothRepository.hasPermissions()) return
        if (value && !interruptModeRepository.canAccessPolicy()) {
            eventsChannel.trySend(AutomationEvent.PolicyRequired)
            return
        }
        viewModelScope.launch {
            settingsRepository.updateAutomation { it.copy(enabled = value) }
            automationCoordinator.apply(
                doNotCheckEnabled = value,
                doNotDisplayOutsideSchedule = false,
            )
        }
    }

    fun setAllDay(value: Boolean) = update { it.copy(allDay = value) }

    fun setStartTime(minuteOfDay: Int) = update {
        it.copy(startMinuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1))
    }

    fun setEndTime(minuteOfDay: Int) = update {
        it.copy(endMinuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1))
    }

    fun setMonitorAllDevices(value: Boolean) = update {
        it.copy(monitorAllDevices = value)
    }

    fun setForceModeOnDevicesConnected(value: Boolean) = update(
        suppressBlockedNotification = true,
    ) {
        it.copy(forceModeOnDevicesConnected = value)
    }

    fun setForceModeOnDevicesDisconnected(value: Boolean) = update(
        suppressBlockedNotification = true,
    ) {
        it.copy(forceModeOnDevicesDisconnected = value)
    }

    fun setRestoreMediaVolume(value: Boolean) = update {
        it.copy(restoreMediaVolume = value)
    }

    fun setReactivateOnUserChange(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBackground {
                it.copy(reactivateOnUserChange = value)
            }
            serviceController.sync(settingsRepository.snapshot().background)
        }
    }

    fun setModeWhenConnected(mode: InterruptMode) {
        val current = uiState.value.automation
        if (mode == current.modeWhenNone) {
            eventsChannel.trySend(AutomationEvent.ModeConflict)
            return
        }
        update { it.copy(modeWhenConnected = mode) }
    }

    fun setModeWhenNone(mode: InterruptMode) {
        val current = uiState.value.automation
        if (mode == current.modeWhenConnected) {
            eventsChannel.trySend(AutomationEvent.ModeConflict)
            return
        }
        update { it.copy(modeWhenNone = mode) }
    }

    private fun update(
        suppressBlockedNotification: Boolean = false,
        transform: (AutomationSettings) -> AutomationSettings,
    ) {
        viewModelScope.launch {
            settingsRepository.updateAutomation(transform)
            automationCoordinator.apply(
                suppressBlockedNotification = suppressBlockedNotification,
            )
        }
    }
}
