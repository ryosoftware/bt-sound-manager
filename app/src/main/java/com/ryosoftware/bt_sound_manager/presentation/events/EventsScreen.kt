package com.ryosoftware.bt_sound_manager.presentation.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.util.displayDeviceName
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.presentation.components.EmptyState
import com.ryosoftware.bt_sound_manager.presentation.components.EventRow
import com.ryosoftware.bt_sound_manager.presentation.components.LoadingState
import com.ryosoftware.bt_sound_manager.presentation.components.MonitorizeTopAppBar
import com.ryosoftware.bt_sound_manager.presentation.components.eventTypeLabelRes
import com.ryosoftware.bt_sound_manager.presentation.components.formatDayLabel
import com.ryosoftware.bt_sound_manager.presentation.components.formatEventDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilter by remember { mutableStateOf(false) }
    var showDeleteMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeleteRequest?>(null) }
    val eventDeletedMessage = stringResource(R.string.event_deleted)
    val eventDeleteErrorMessage = stringResource(R.string.event_delete_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EventsEvent.Deleted -> snackbarHostState.showSnackbar(
                    eventDeletedMessage,
                )

                EventsEvent.DeleteError -> snackbarHostState.showSnackbar(
                    eventDeleteErrorMessage,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            MonitorizeTopAppBar(
                title = stringResource(R.string.events_title),
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.cd_filter),
                        )
                    }
                    Box {
                        IconButton(onClick = { showDeleteMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                            )
                        }
                        DropdownMenu(
                            expanded = showDeleteMenu,
                            onDismissRequest = { showDeleteMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_visible_events)) },
                                onClick = {
                                    showDeleteMenu = false
                                    pendingDelete = DeleteRequest.Visible
                                },
                                enabled = state.visibleCount > 0,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_all_events)) },
                                onClick = {
                                    showDeleteMenu = false
                                    pendingDelete = DeleteRequest.All
                                },
                                enabled = state.totalCount > 0,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            state.groups.isEmpty() -> EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.no_events),
                body = stringResource(R.string.no_events_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header-${group.date}") {
                        DayHeader(
                            date = group.date,
                            count = group.events.size,
                            onDelete = {
                                pendingDelete = DeleteRequest.Day(group.date, group.events.size)
                            },
                        )
                    }
                    items(group.events, key = { "event-${it.id}" }) { event ->
                        val deviceName = event.deviceAddress?.let { address ->
                            displayDeviceName(
                                state.deviceNames[address],
                                event.deviceName,
                                address,
                            )
                        }
                        EventRow(
                            event = event,
                            deviceName = deviceName,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showFilter) {
        FilterBottomSheet(
            state = state,
            onDismiss = { showFilter = false },
            onToggleType = viewModel::toggleType,
            onToggleDevice = viewModel::toggleDevice,
            onShowAll = viewModel::showAll,
            onHideAll = viewModel::hideAll,
        )
    }

    pendingDelete?.let { request ->
        DeleteConfirmationDialog(
            request = request,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                when (request) {
                    DeleteRequest.All -> viewModel.deleteAll()
                    DeleteRequest.Visible -> viewModel.deleteVisible()
                    is DeleteRequest.Day -> viewModel.deleteDay(request.date)
                }
            },
        )
    }

}

@Composable
private fun DayHeader(
    date: LocalDate,
    count: Int,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDayLabel(context, date).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = pluralStringResource(R.plurals.events_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete_day_events),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    state: EventsUiState,
    onDismiss: () -> Unit,
    onToggleType: (BluetoothEventType) -> Unit,
    onToggleDevice: (String) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onShowAll) {
                    Text(stringResource(R.string.filter_all))
                }
                TextButton(onClick = onHideAll) {
                    Text(stringResource(R.string.filter_none))
                }
            }
            Text(
                text = stringResource(R.string.filter_types),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            BluetoothEventType.entries.forEach { type ->
                FilterCheckbox(
                    label = stringResource(eventTypeLabelRes(type)),
                    checked = type !in state.filter.hiddenTypes,
                    onCheckedChange = { onToggleType(type) },
                )
            }
            if (state.availableDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.filter_devices),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.availableDevices.forEach { device ->
                    FilterCheckbox(
                        label = device.name,
                        checked = device.address !in state.filter.hiddenDeviceAddresses,
                        onCheckedChange = { onToggleDevice(device.address) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeleteConfirmationDialog(
    request: DeleteRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val title = when (request) {
        DeleteRequest.All -> stringResource(R.string.delete_all_events)
        DeleteRequest.Visible -> stringResource(R.string.delete_visible_events)
        is DeleteRequest.Day -> stringResource(
            R.string.delete_day_events,
        )
    }
    val body = when (request) {
        is DeleteRequest.Day -> stringResource(
            R.string.delete_confirm_day_body,
            formatEventDate(context, request.date),
        )

        else -> stringResource(R.string.delete_confirm_body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private sealed interface DeleteRequest {
    data object All : DeleteRequest
    data object Visible : DeleteRequest
    data class Day(val date: LocalDate, val count: Int) : DeleteRequest
}
