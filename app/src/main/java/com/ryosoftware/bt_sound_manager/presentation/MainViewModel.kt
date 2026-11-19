package com.ryosoftware.bt_sound_manager.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.RefreshDeviceNames
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val serviceController: MonitorServiceController,
    private val deviceNameRefresher: RefreshDeviceNames,
    private val automationCoordinator: AutomationCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = settingsRepository.settings
        .map { it.appearance }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppearanceSettings(),
        )

    init {
        viewModelScope.launch {
            serviceController.sync(settingsRepository.snapshot().background)
        }
    }

    fun refreshDeviceNames() {
        viewModelScope.launch {
            runCatching { deviceNameRefresher() }
        }
    }

    fun applyAutomationOnStart() {
        viewModelScope.launch {
            runCatching { automationCoordinator.apply() }
        }
    }
}
