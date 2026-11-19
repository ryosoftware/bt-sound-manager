package com.ryosoftware.bt_sound_manager.presentation.automation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.presentation.components.MonitorizeTopAppBar
import com.ryosoftware.bt_sound_manager.presentation.components.SectionCard
import com.ryosoftware.bt_sound_manager.presentation.components.SettingNavigationRow
import com.ryosoftware.bt_sound_manager.presentation.components.SettingSwitchRow
import com.ryosoftware.bt_sound_manager.presentation.components.formatMinuteOfDay
import com.ryosoftware.bt_sound_manager.presentation.components.isScheduleCrossingMidnight
import com.ryosoftware.bt_sound_manager.presentation.components.modeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    onSelectDevices: () -> Unit,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var pendingEnableAfterPolicy by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf<TimePickerTarget?>(null) }
    var modePicker by remember { mutableStateOf<ModePickerTarget?>(null) }
    val modeConflictMessage = stringResource(R.string.mode_conflict)
    val errorMessage = stringResource(R.string.error_generic)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AutomationEvent.PolicyRequired -> showPolicyDialog = true
                AutomationEvent.ModeConflict -> snackbarHostState.showSnackbar(
                    modeConflictMessage,
                )

                AutomationEvent.Error -> snackbarHostState.showSnackbar(
                    errorMessage,
                )
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPolicyAccess()
    }
    LaunchedEffect(state.policyAccess) {
        if (pendingEnableAfterPolicy && state.policyAccess) {
            pendingEnableAfterPolicy = false
            viewModel.setEnabled(true)
        }
    }

    Scaffold(
        topBar = {
            MonitorizeTopAppBar(
                title = stringResource(R.string.automation_screen_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.automation_section)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.enable_automation),
                        summary = stringResource(
                            if (state.automation.enabled) {
                                R.string.automation_enabled_summary
                            } else {
                                R.string.automation_disabled_summary
                            },
                        ),
                        checked = state.automation.enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.automation_schedule)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.all_day),
                        checked = state.automation.allDay,
                        onCheckedChange = viewModel::setAllDay,
                        enabled = state.automation.enabled,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.start_time),
                        summary = formatMinuteOfDay(
                            context,
                            state.automation.startMinuteOfDay,
                        ),
                        onClick = { timePicker = TimePickerTarget.START },
                        enabled = state.automation.enabled && !state.automation.allDay,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.end_time),
                        summary = formatMinuteOfDay(
                            context,
                            state.automation.endMinuteOfDay,
                        ).let { formatted ->
                            if (isScheduleCrossingMidnight(
                                    state.automation.startMinuteOfDay,
                                    state.automation.endMinuteOfDay,
                                )
                            ) {
                                stringResource(R.string.next_day_suffix, formatted)
                            } else {
                                formatted
                            }
                        },
                        onClick = { timePicker = TimePickerTarget.END },
                        enabled = state.automation.enabled && !state.automation.allDay,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.automation_devices)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.all_bluetooth_devices),
                        checked = state.automation.monitorAllDevices,
                        onCheckedChange = viewModel::setMonitorAllDevices,
                        enabled = state.automation.enabled,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.selected_devices),
                        summary = if (state.automation.selectedDeviceAddresses.isEmpty()) {
                            stringResource(R.string.no_devices_selected)
                        } else {
                            pluralStringResource(
                                R.plurals.selected_devices_count,
                                state.automation.selectedDeviceAddresses.size,
                                state.automation.selectedDeviceAddresses.size,
                            )
                        },
                        onClick = onSelectDevices,
                        enabled = state.automation.enabled &&
                            !state.automation.monitorAllDevices,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.automation_modes)) {
                    SettingNavigationRow(
                        title = stringResource(R.string.mode_when_connected),
                        summary = modeLabel(context, state.automation.modeWhenConnected),
                        onClick = { modePicker = ModePickerTarget.CONNECTED },
                        enabled = state.automation.enabled,
                    )
                    SettingNavigationRow(
                        title = stringResource(R.string.mode_when_none),
                        summary = modeLabel(context, state.automation.modeWhenNone),
                        onClick = { modePicker = ModePickerTarget.NONE },
                        enabled = state.automation.enabled,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.force_mode),
                        summary = stringResource(R.string.force_mode_summary),
                        checked = state.automation.forceMode,
                        onCheckedChange = viewModel::setForceMode,
                        enabled = state.automation.enabled,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.restore_media_volume),
                        summary = stringResource(R.string.restore_media_volume_summary),
                        checked = state.automation.restoreMediaVolume,
                        onCheckedChange = viewModel::setRestoreMediaVolume,
                        enabled = state.automation.enabled,
                    )
                }
            }
        }
    }

    timePicker?.let { target ->
        val initialMinute = when (target) {
            TimePickerTarget.START -> state.automation.startMinuteOfDay
            TimePickerTarget.END -> state.automation.endMinuteOfDay
        }
        AutomationTimePickerDialog(
            initialMinuteOfDay = initialMinute,
            onDismiss = { timePicker = null },
            onConfirm = { minuteOfDay ->
                when (target) {
                    TimePickerTarget.START -> viewModel.setStartTime(minuteOfDay)
                    TimePickerTarget.END -> viewModel.setEndTime(minuteOfDay)
                }
                timePicker = null
            },
        )
    }

    modePicker?.let { target ->
        val initialMode = when (target) {
            ModePickerTarget.CONNECTED -> state.automation.modeWhenConnected
            ModePickerTarget.NONE -> state.automation.modeWhenNone
        }
        ModeSelectionDialog(
            initialMode = initialMode,
            onDismiss = { modePicker = null },
            onConfirm = { mode ->
                when (target) {
                    ModePickerTarget.CONNECTED -> viewModel.setModeWhenConnected(mode)
                    ModePickerTarget.NONE -> viewModel.setModeWhenNone(mode)
                }
                modePicker = null
            },
        )
    }

    if (showPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            title = { Text(stringResource(R.string.automation_policy_required)) },
            text = { Text(stringResource(R.string.automation_policy_required_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPolicyDialog = false
                        pendingEnableAfterPolicy = true
                        openNotificationPolicySettings(context)
                    },
                ) {
                    Text(stringResource(R.string.allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPolicyDialog = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationTimePickerDialog(
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val context = LocalContext.current
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_picker_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timePickerState.hour * 60 + timePickerState.minute)
                },
            ) {
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
private fun ModeSelectionDialog(
    initialMode: InterruptMode,
    onDismiss: () -> Unit,
    onConfirm: (InterruptMode) -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(initialMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_selection_title)) },
        text = {
            Column {
                InterruptMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = mode }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == mode,
                            onClick = { selected = mode },
                        )
                        Text(
                            text = modeLabel(context, mode),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
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

private enum class TimePickerTarget { START, END }

private enum class ModePickerTarget { CONNECTED, NONE }

private fun openNotificationPolicySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    } catch (_: Exception) {
        // The settings screen is not available.
    }
}
