package com.ryosoftware.bt_sound_manager.presentation.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.bt_sound_manager.BuildConfig
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.util.displayDeviceName
import com.ryosoftware.bt_sound_manager.domain.model.BackupSelection
import com.ryosoftware.bt_sound_manager.domain.model.BackupSummary
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.model.ThemeMode
import com.ryosoftware.bt_sound_manager.presentation.components.BluetoothStatusCard
import com.ryosoftware.bt_sound_manager.presentation.components.LoadingState
import com.ryosoftware.bt_sound_manager.presentation.components.MonitorizeTopAppBar
import com.ryosoftware.bt_sound_manager.presentation.components.SectionCard
import com.ryosoftware.bt_sound_manager.presentation.components.SettingNavigationRow
import com.ryosoftware.bt_sound_manager.presentation.components.SettingSwitchRow
import com.ryosoftware.bt_sound_manager.presentation.components.formatDateTime
import androidx.core.net.toUri
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onConfigureAutomation: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshSystemState()
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshSystemState()
    }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshSystemState()
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onBatteryOptimizationRequestReturned()
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshSystemState()
    }
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.createBackup(it.toString()) }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.inspectBackup(it.toString()) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val backupEvents = viewModel.events
    LaunchedEffect(backupEvents) {
        backupEvents.collect { event ->
            val message = when (event) {
                SettingsEvent.BackupSaved -> context.getString(R.string.backup_saved)
                SettingsEvent.BackupFailed -> context.getString(R.string.backup_error)
                SettingsEvent.RestoreInvalid -> context.getString(R.string.restore_invalid)
                SettingsEvent.RestoreFailed -> context.getString(R.string.restore_error)
                is SettingsEvent.RestoreCompleted -> when {
                    event.settingsRestored && event.eventsRestored > 0 -> context.resources
                        .getQuantityString(
                            R.plurals.restore_completed_all,
                            event.eventsRestored,
                            event.eventsRestored,
                        )

                    event.settingsRestored -> context.getString(R.string.restore_completed_settings)
                    else -> context.resources.getQuantityString(
                        R.plurals.restore_completed_events,
                        event.eventsRestored,
                        event.eventsRestored,
                    )
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemState()
    }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }

    if (showRetentionDialog) {
        RetentionDaysDialog(
            initialDays = state.settings.events.retentionDays,
            onDismiss = { showRetentionDialog = false },
            onConfirm = { days ->
                viewModel.setRetentionDays(days)
                showRetentionDialog = false
            },
        )
    }

    state.pendingRestore?.let { summary ->
        RestoreBackupDialog(
            summary = summary,
            busy = state.busy,
            onDismiss = viewModel::cancelRestore,
            onConfirm = viewModel::confirmRestore,
        )
    }

    Scaffold(
        topBar = {
            MonitorizeTopAppBar(
                title = stringResource(R.string.settings_title),
                actions = {
                    Box {
                        IconButton(
                            onClick = { showBackupMenu = true },
                            enabled = !state.busy,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = showBackupMenu,
                            onDismissRequest = { showBackupMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.backup_data)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Backup,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showBackupMenu = false
                                    createBackupLauncher.launch(
                                        viewModel.suggestedBackupFileName(),
                                    )
                                },
                                enabled = !state.busy,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restore_data)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showBackupMenu = false
                                    restoreBackupLauncher.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/plain",
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                                enabled = !state.busy,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.missingBluetoothPermissions.isEmpty()) {
                item {
                    val connectedNames = state.connectedDevices
                        .map { device ->
                            displayDeviceName(
                                state.deviceNames[device.address],
                                device.name,
                                device.address,
                            )
                        }
                        .distinct()
                    BluetoothStatusCard(
                        bluetoothEnabled = state.bluetoothEnabled,
                        connectedDeviceNames = connectedNames,
                        onToggleBluetooth = {
                            val action = if (state.bluetoothEnabled) {
                                REQUEST_DISABLE_BLUETOOTH
                            } else {
                                BluetoothAdapter.ACTION_REQUEST_ENABLE
                            }
                            try {
                                bluetoothLauncher.launch(Intent(action))
                            } catch (_: Exception) {
                                openBluetoothSettings(context)
                            }
                        },
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.permissions_section)) {
                    SettingNavigationRow(
                        title = stringResource(R.string.bluetooth_permissions),
                        summary = stringResource(
                            if (state.missingBluetoothPermissions.isEmpty()) {
                                R.string.status_granted
                            } else {
                                R.string.status_not_granted
                            },
                        ),
                        onClick = {
                            if (state.missingBluetoothPermissions.isNotEmpty()) {
                                bluetoothPermissionLauncher.launch(
                                    state.missingBluetoothPermissions.toTypedArray(),
                                )
                            }
                        },
                        enabled = state.missingBluetoothPermissions.isNotEmpty(),
                        leadingIcon = Icons.Outlined.Bluetooth,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.notification_settings),
                        summary = if (
                            state.notificationsEnabled && !state.notificationPermissionMissing
                        ) {
                            stringResource(R.string.settings_notifications_enabled)
                        } else {
                            stringResource(R.string.notification_permission_required)
                        },
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && state.notificationPermissionMissing) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            } else {
                                openNotificationSettings(context)
                            }
                        },
                        enabled = state.notificationPermissionMissing ||
                            !state.notificationsEnabled,
                        leadingIcon = Icons.Outlined.Notifications,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.notification_policy_title),
                        summary = stringResource(
                            if (state.notificationPolicyAccess) {
                                R.string.status_granted
                            } else {
                                R.string.status_not_granted
                            },
                        ),
                        onClick = { openNotificationPolicySettings(context) },
                        enabled = !state.notificationPolicyAccess,
                        leadingIcon = Icons.Outlined.Lock,
                    )
                    if (Build.VERSION.SDK_INT >= 31) {
                        SettingNavigationRow(
                            title = stringResource(R.string.exact_alarm_title),
                            summary = stringResource(
                                if (state.exactAlarmsAllowed) {
                                    R.string.status_allowed
                                } else {
                                    R.string.status_not_allowed
                                },
                            ),
                            onClick = {
                                try {
                                    exactAlarmLauncher.launch(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            "package:${context.packageName}".toUri(),
                                        ),
                                    )
                                } catch (_: Exception) {
                                    viewModel.refreshSystemState()
                                }
                            },
                            enabled = !state.exactAlarmsAllowed,
                            leadingIcon = Icons.Outlined.Alarm,
                        )
                    }
                    @SuppressLint("BatteryLife")
                    SettingNavigationRow(
                        title = stringResource(R.string.battery_optimization),
                        summary = stringResource(
                            if (state.batteryOptimizationIgnored) {
                                R.string.battery_optimizations_disabled
                            } else {
                                R.string.battery_optimizations_enabled
                            },
                        ),
                        onClick = {
                            try {
                                batteryOptimizationLauncher.launch(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            } catch (_: Exception) {
                                viewModel.refreshSystemState()
                            }
                        },
                        enabled = !state.batteryOptimizationIgnored,
                        leadingIcon = Icons.Outlined.BatteryAlert,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.automation_section)) {
                    SettingNavigationRow(
                        title = stringResource(R.string.enable_automation),
                        summary = stringResource(
                            if (state.settings.automation.enabled) {
                                R.string.automation_enabled_summary
                            } else {
                                R.string.automation_disabled_summary
                            },
                        ),
                        onClick = onConfigureAutomation,
                        leadingIcon = Icons.Outlined.Schedule,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.background_section)) {
                    Text(
                        text = stringResource(R.string.background_optional_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.detect_bluetooth_events),
                        summary = stringResource(R.string.detect_bluetooth_events_summary),
                        checked = state.settings.background.detectBluetoothEvents,
                        onCheckedChange = viewModel::setDetectBluetoothEvents,
                        leadingIcon = Icons.Outlined.Bluetooth,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.reactivate_on_user_change),
                        summary = stringResource(R.string.reactivate_on_user_change_summary),
                        checked = state.settings.background.reactivateOnUserChange,
                        onCheckedChange = viewModel::setReactivateOnUserChange,
                        leadingIcon = Icons.Outlined.Schedule,
                    )
                    Text(
                        text = stringResource(
                            if (state.backgroundServiceRunning) {
                                R.string.settings_background_running
                            } else {
                                R.string.background_service_stopped
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.events_settings_section)) {
                    SettingNavigationRow(
                        title = stringResource(R.string.retention_days_title),
                        summary = pluralStringResource(
                            R.plurals.retention_days_value,
                            state.settings.events.retentionDays,
                            state.settings.events.retentionDays,
                        ),
                        onClick = { showRetentionDialog = true },
                        leadingIcon = Icons.Outlined.History,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.appearance_section)) {
                    RowLikeTitle(
                        title = stringResource(R.string.theme_mode),
                        icon = Icons.Outlined.DarkMode,
                    )
                    ThemeModeSelector(
                        selected = state.settings.appearance.themeMode,
                        onSelected = viewModel::setThemeMode,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        summary = stringResource(R.string.dynamic_color_summary),
                        checked = state.settings.appearance.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                        enabled = Build.VERSION.SDK_INT >= 31,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.about_section, BuildConfig.VERSION_NAME)) {
                    SettingNavigationRow(
                        title = stringResource(R.string.github_title),
                        summary = stringResource(R.string.github_summary),
                        onClick = { openUrl(context, GITHUB_URL) },
                        leadingIcon = Icons.Outlined.OpenInNew,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.app_info),
                        summary = stringResource(R.string.app_info_summary),
                        onClick = { openAppInfo(context) },
                        leadingIcon = Icons.Outlined.Info,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowLikeTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    androidx.compose.material3.ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(title) },
    )
}

@Composable
private fun RetentionDaysDialog(
    initialDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var days by remember(initialDays) {
        mutableIntStateOf(
            initialDays.coerceIn(
                EventsSettings.MIN_RETENTION_DAYS,
                EventsSettings.MAX_RETENTION_DAYS,
            ),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.retention_days_title)) },
        text = {
            Column {
                Text(
                    text = pluralStringResource(
                        R.plurals.retention_days_value,
                        days,
                        days,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = days.toFloat(),
                    onValueChange = { days = it.roundToInt() },
                    valueRange = EventsSettings.MIN_RETENTION_DAYS.toFloat()..
                        EventsSettings.MAX_RETENTION_DAYS.toFloat(),
                    steps = EventsSettings.MAX_RETENTION_DAYS -
                        EventsSettings.MIN_RETENTION_DAYS - 1,
                )
                Text(
                    text = stringResource(R.string.retention_days_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(days) }) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreBackupDialog(
    summary: BackupSummary,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BackupSelection) -> Unit,
) {
    val context = LocalContext.current
    var restoreEvents by remember(summary) { mutableStateOf(true) }
    var restoreSettings by remember(summary) { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.restore_body,
                        formatDateTime(context, summary.createdAtUtcMillis),
                        summary.appVersion,
                    ),
                )
                Text(
                    text = stringResource(R.string.restore_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RestoreCheckbox(
                    title = stringResource(R.string.restore_events),
                    summary = pluralStringResource(
                        R.plurals.events_count,
                        summary.eventCount,
                        summary.eventCount,
                    ),
                    checked = restoreEvents,
                    onCheckedChange = { restoreEvents = it },
                )
                RestoreCheckbox(
                    title = stringResource(R.string.restore_settings),
                    summary = stringResource(R.string.restore_settings_summary),
                    checked = restoreSettings,
                    onCheckedChange = { restoreSettings = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        BackupSelection(
                            events = restoreEvents,
                            settings = restoreSettings,
                        ),
                    )
                },
                enabled = (restoreEvents || restoreSettings) && !busy,
            ) {
                Text(stringResource(R.string.restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreCheckbox(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val modes = remember {
        listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )
    }
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        modes.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    } catch (_: Exception) {
        // Some OEMs do not expose app notification settings.
    }
}

private fun openNotificationPolicySettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    } catch (_: Exception) {
        // The settings screen is not available.
    }
}

private fun openAppInfo(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            ),
        )
    } catch (_: Exception) {
        // Some OEMs do not expose the app info screen.
    }
}

private fun openBluetoothSettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    } catch (_: Exception) {
        // Some OEMs do not expose Bluetooth settings.
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // No app can handle the link.
    }
}

private const val GITHUB_URL = "https://github.com/ryosoftware/bt-sound-manager"
private const val REQUEST_DISABLE_BLUETOOTH = "android.bluetooth.adapter.action.REQUEST_DISABLE"
