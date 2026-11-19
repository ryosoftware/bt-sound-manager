package com.ryosoftware.bt_sound_manager.presentation.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.model.ThemeMode
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import com.ryosoftware.bt_sound_manager.service.MonitorServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val deviceNames: Map<String, String> = emptyMap(),
    val notificationsEnabled: Boolean = true,
    val notificationPermissionMissing: Boolean = false,
    val missingBluetoothPermissions: List<String> = emptyList(),
    val batteryOptimizationIgnored: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val notificationPolicyAccess: Boolean = false,
    val backgroundServiceRunning: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val connectedDevices: List<BluetoothDeviceInfo> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serviceController: MonitorServiceController,
    private val interruptModeRepository: InterruptModeRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val eventRepository: BluetoothEventRepository,
    deviceRepository: DeviceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private data class SystemState(
        val notificationsEnabled: Boolean = true,
        val notificationPermissionMissing: Boolean = false,
        val missingBluetoothPermissions: List<String> = emptyList(),
        val batteryOptimizationIgnored: Boolean = false,
        val exactAlarmsAllowed: Boolean = true,
        val notificationPolicyAccess: Boolean = false,
    )

    private val systemState = MutableStateFlow(SystemState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        deviceRepository.observeDeviceNames(),
        systemState,
        MonitorServiceState.isRunning,
        bluetoothRepository.observeSnapshot(),
    ) { settings, deviceNames, system, serviceRunning, bluetoothSnapshot ->
        SettingsUiState(
            loading = false,
            settings = settings,
            deviceNames = deviceNames,
            notificationsEnabled = system.notificationsEnabled,
            notificationPermissionMissing = system.notificationPermissionMissing,
            missingBluetoothPermissions = system.missingBluetoothPermissions,
            batteryOptimizationIgnored = system.batteryOptimizationIgnored,
            exactAlarmsAllowed = system.exactAlarmsAllowed,
            notificationPolicyAccess = system.notificationPolicyAccess,
            backgroundServiceRunning = serviceRunning,
            bluetoothEnabled = bluetoothSnapshot.enabled,
            connectedDevices = bluetoothSnapshot.connectedDevices,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        refreshSystemState()
    }

    fun refreshSystemState() {
        bluetoothRepository.refreshSnapshot()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        systemState.value = SystemState(
            notificationsEnabled = NotificationManagerCompat.from(context)
                .areNotificationsEnabled(),
            notificationPermissionMissing = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED,
            missingBluetoothPermissions = requiredBluetoothPermissions()
                .filterNot(::isPermissionGranted),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
            exactAlarmsAllowed = Build.VERSION.SDK_INT < 31 ||
                alarmManager?.canScheduleExactAlarms() == true,
            notificationPolicyAccess = interruptModeRepository.canAccessPolicy(),
        )
    }

    fun onBatteryOptimizationRequestReturned() {
        viewModelScope.launch {
            refreshSystemState()
            repeat(BATTERY_RECHECK_ATTEMPTS) {
                if (isIgnoringBatteryOptimizations()) {
                    systemState.update { it.copy(batteryOptimizationIgnored = true) }
                    return@launch
                }
                delay(BATTERY_RECHECK_DELAY_MS)
            }
        }
    }

    fun setDetectBluetoothEvents(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBackground { it.copy(detectBluetoothEvents = value) }
            serviceController.sync(settingsRepository.snapshot().background)
            refreshSystemState()
        }
    }

    fun setReactivateOnUserChange(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBackground { it.withReactivateOnUserChange(value) }
            serviceController.sync(settingsRepository.snapshot().background)
            refreshSystemState()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateAppearance { it.copy(themeMode = mode) }
        }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAppearance { it.copy(dynamicColor = value) }
        }
    }

    fun setRetentionDays(days: Int) {
        val value = days.coerceIn(
            EventsSettings.MIN_RETENTION_DAYS,
            EventsSettings.MAX_RETENTION_DAYS,
        )
        viewModelScope.launch {
            settingsRepository.updateEvents { it.copy(retentionDays = value) }
            eventRepository.pruneExpired()
        }
    }

    private fun requiredBluetoothPermissions(): List<String> = if (Build.VERSION.SDK_INT >= 31) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        emptyList()
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private companion object {
        const val BATTERY_RECHECK_ATTEMPTS = 10
        const val BATTERY_RECHECK_DELAY_MS = 500L
    }
}
