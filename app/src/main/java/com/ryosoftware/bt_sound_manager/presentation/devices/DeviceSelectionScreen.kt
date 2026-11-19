package com.ryosoftware.bt_sound_manager.presentation.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.presentation.components.EmptyState
import com.ryosoftware.bt_sound_manager.presentation.components.LoadingState
import com.ryosoftware.bt_sound_manager.presentation.components.MonitorizeTopAppBar

@Composable
fun DeviceSelectionScreen(
    onBack: () -> Unit,
    viewModel: DeviceSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var savedHandled by remember { mutableStateOf(false) }
    val errorMessage = stringResource(R.string.error_generic)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DeviceSelectionEvent.Saved -> {
                    if (!savedHandled) {
                        savedHandled = true
                        onBack()
                    }
                }

                DeviceSelectionEvent.Error -> snackbarHostState.showSnackbar(
                    errorMessage,
                )
            }
        }
    }

    BackHandler {
        viewModel.save()
    }

    Scaffold(
        topBar = {
            MonitorizeTopAppBar(
                title = stringResource(R.string.selected_devices),
                onBack = viewModel::save,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.search_devices)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.cd_search),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            when {
                state.loading -> LoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                )

                state.devices.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Devices,
                    title = stringResource(R.string.no_devices_found),
                    body = stringResource(R.string.no_paired_devices),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.devices, key = { it.address }) { device ->
                        SelectableDeviceRow(
                            device = device,
                            checked = device.address in state.selected,
                            onToggle = { viewModel.toggleDevice(device.address) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableDeviceRow(
    device: SelectableDevice,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val status = when {
        device.connected -> stringResource(R.string.connected)
        device.bonded -> stringResource(R.string.paired)
        else -> null
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
            )
        },
        headlineContent = { Text(device.name) },
        supportingContent = {
            Column {
                Text(device.address)
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = if (device.connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}
