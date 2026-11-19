package com.ryosoftware.bt_sound_manager.presentation.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
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
    val deviceNames: Map<String, String> = emptyMap(),
    val policyAccess: Boolean = false,
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
    private val automationCoordinator: AutomationCoordinator,
) : ViewModel() {

    private val policyAccess = MutableStateFlow(false)
    private val eventsChannel = Channel<AutomationEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<AutomationUiState> = combine(
        settingsRepository.settings,
        deviceRepository.observeDeviceNames(),
        policyAccess,
    ) { settings, names, access ->
        AutomationUiState(
            loading = false,
            automation = settings.automation,
            deviceNames = names,
            policyAccess = access,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutomationUiState(),
    )

    init {
        refreshPolicyAccess()
    }

    fun refreshPolicyAccess() {
        policyAccess.value = interruptModeRepository.canAccessPolicy()
    }

    fun setEnabled(value: Boolean) {
        if (value && !interruptModeRepository.canAccessPolicy()) {
            eventsChannel.trySend(AutomationEvent.PolicyRequired)
            return
        }
        viewModelScope.launch {
            settingsRepository.updateAutomation { it.copy(enabled = value) }
            automationCoordinator.apply(
                doNotCheckEnabled = value,
                doNotDisplayOutsideSchedule = false,
                forceNotification = true,
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

    fun setForceMode(value: Boolean) = update { it.copy(forceMode = value) }

    fun setRestoreMediaVolume(value: Boolean) = update {
        it.copy(restoreMediaVolume = value)
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

    private fun update(transform: (AutomationSettings) -> AutomationSettings) {
        viewModelScope.launch {
            settingsRepository.updateAutomation(transform)
            automationCoordinator.apply()
        }
    }
}
