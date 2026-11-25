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
import com.ryosoftware.bt_sound_manager.core.util.BluetoothPermissions
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackupContents
import com.ryosoftware.bt_sound_manager.domain.model.BackupSelection
import com.ryosoftware.bt_sound_manager.domain.model.BackupSummary
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.model.ThemeMode
import com.ryosoftware.bt_sound_manager.domain.repository.BackupRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.presentation.AppRecreationSignal
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import com.ryosoftware.bt_sound_manager.service.MonitorServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object BackupSaved : SettingsEvent

    data object BackupFailed : SettingsEvent

    data object RestoreInvalid : SettingsEvent

    data object RestoreFailed : SettingsEvent

    data class RestoreCompleted(
        val eventsRestored: Int,
        val settingsRestored: Boolean,
    ) : SettingsEvent
}

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
    val busy: Boolean = false,
    val pendingRestore: BackupSummary? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serviceController: MonitorServiceController,
    private val interruptModeRepository: InterruptModeRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val eventRepository: BluetoothEventRepository,
    private val backupRepository: BackupRepository,
    private val automationCoordinator: AutomationCoordinator,
    private val appRecreationSignal: AppRecreationSignal,
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

    private data class BackupActionState(
        val busy: Boolean = false,
        val pendingRestore: BackupSummary? = null,
    )

    private val systemState = MutableStateFlow(SystemState())
    private val backupActionState = MutableStateFlow(BackupActionState())
    private var pendingRestoreContent: BackupContents? = null
    private var hasRefreshedSystemState = false

    private val eventsChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private val baseUiState: Flow<SettingsUiState> = combine(
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
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        baseUiState,
        backupActionState,
    ) { base, action ->
        base.copy(
            busy = action.busy,
            pendingRestore = action.pendingRestore,
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
        val previous = systemState.value
        val updated = SystemState(
            notificationsEnabled = NotificationManagerCompat.from(context)
                .areNotificationsEnabled(),
            notificationPermissionMissing = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED,
            missingBluetoothPermissions = BluetoothPermissions.missingPermissions(context),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
            exactAlarmsAllowed = Build.VERSION.SDK_INT < 31 ||
                alarmManager?.canScheduleExactAlarms() == true,
            notificationPolicyAccess = interruptModeRepository.canAccessPolicy(),
        )
        systemState.value = updated
        if (!hasRefreshedSystemState) {
            hasRefreshedSystemState = true
            return
        }
        val permissionsChanged =
            previous.missingBluetoothPermissions != updated.missingBluetoothPermissions ||
                previous.notificationPolicyAccess != updated.notificationPolicyAccess
        if (!permissionsChanged) return
        val wasReady = previous.missingBluetoothPermissions.isEmpty() &&
            previous.notificationPolicyAccess
        val isReady = updated.missingBluetoothPermissions.isEmpty() &&
            updated.notificationPolicyAccess
        viewModelScope.launch {
            runCatching {
                serviceController.sync(settingsRepository.snapshot().background)
            }
            if (isReady && !wasReady && settingsRepository.snapshot().automation.enabled) {
                runCatching { automationCoordinator.apply() }
            }
        }
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
        if (value && !BluetoothPermissions.isGranted(context)) return
        viewModelScope.launch {
            settingsRepository.updateBackground { it.copy(detectBluetoothEvents = value) }
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

    fun suggestedBackupFileName(): String = backupRepository.suggestedFileName()

    fun createBackup(uri: String) {
        if (backupActionState.value.busy) return
        viewModelScope.launch {
            backupActionState.update { it.copy(busy = true) }
            try {
                backupRepository.export(uri)
                eventsChannel.send(SettingsEvent.BackupSaved)
            } catch (_: Exception) {
                eventsChannel.send(SettingsEvent.BackupFailed)
            } finally {
                backupActionState.update { it.copy(busy = false) }
            }
        }
    }

    fun inspectBackup(uri: String) {
        if (backupActionState.value.busy) return
        viewModelScope.launch {
            backupActionState.update { it.copy(busy = true) }
            try {
                val contents = backupRepository.read(uri)
                pendingRestoreContent = contents
                backupActionState.update {
                    it.copy(pendingRestore = BackupSummary.from(contents))
                }
            } catch (_: Exception) {
                eventsChannel.send(SettingsEvent.RestoreInvalid)
            } finally {
                backupActionState.update { it.copy(busy = false) }
            }
        }
    }

    fun cancelRestore() {
        if (backupActionState.value.busy) return
        pendingRestoreContent = null
        backupActionState.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore(selection: BackupSelection) {
        if (!selection.anySelected || backupActionState.value.busy) return
        val contents = pendingRestoreContent ?: return
        viewModelScope.launch {
            backupActionState.update { it.copy(busy = true) }
            var requestRecreation = false
            try {
                val result = backupRepository.restore(contents, selection)
                runCatching { eventRepository.pruneExpired() }
                if (result.settingsRestored) {
                    val background = settingsRepository.snapshot().background
                    runCatching { serviceController.sync(background) }
                    runCatching { automationCoordinator.apply() }
                }
                pendingRestoreContent = null
                backupActionState.update { it.copy(pendingRestore = null) }
                eventsChannel.send(
                    SettingsEvent.RestoreCompleted(
                        eventsRestored = result.eventsRestored,
                        settingsRestored = result.settingsRestored,
                    ),
                )
                requestRecreation = result.settingsRestored
            } catch (_: Exception) {
                eventsChannel.send(SettingsEvent.RestoreFailed)
            } finally {
                backupActionState.update { it.copy(busy = false) }
            }
            if (requestRecreation) {
                appRecreationSignal.request()
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private companion object {
        const val BATTERY_RECHECK_ATTEMPTS = 10
        const val BATTERY_RECHECK_DELAY_MS = 500L
    }
}
