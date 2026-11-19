package com.ryosoftware.bt_sound_manager.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.presentation.theme.EventBluetoothOnContainer
import com.ryosoftware.bt_sound_manager.presentation.theme.EventBluetoothOnContent
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDarkBluetoothOnContainer
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDarkBluetoothOnContent
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDarkDeviceConnectedContainer
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDarkDeviceConnectedContent
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDarkDeviceDisconnectedContainer
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDarkDeviceDisconnectedContent
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDeviceConnectedContainer
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDeviceConnectedContent
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDeviceDisconnectedContainer
import com.ryosoftware.bt_sound_manager.presentation.theme.EventDeviceDisconnectedContent
import com.ryosoftware.bt_sound_manager.presentation.theme.LocalDarkTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventRow(
    event: BluetoothEvent,
    deviceName: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val containerColor = eventContainerColor(event.type)
    val contentColor = eventContentColor(event.type)
    val eventLabel = stringResource(eventTypeLabelRes(event.type))
    val address = event.deviceAddress?.takeIf { it.isNotBlank() }
    val title = if (event.type.isDeviceEvent) {
        deviceName?.takeIf { it.isNotBlank() } ?: address.orEmpty()
    } else {
        eventLabel
    }
    val supportingLines = if (event.type.isDeviceEvent) {
        buildList {
            address?.takeIf { it != title }?.let(::add)
            add(eventLabel)
        }
    } else {
        emptyList()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            modifier = Modifier
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(containerColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (
                            event.type == BluetoothEventType.BLUETOOTH_OFF ||
                            event.type == BluetoothEventType.DEVICE_DISCONNECTED
                        ) {
                            Icons.Outlined.BluetoothDisabled
                        } else {
                            Icons.Outlined.Bluetooth
                        },
                        contentDescription = stringResource(com.ryosoftware.bt_sound_manager.R.string.cd_event_icon),
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            headlineContent = {
                Text(text = title)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    supportingLines.forEach { line ->
                        Text(text = line)
                    }
                    Text(
                        text = formatEventTime(context, event.timeUtc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun eventContainerColor(type: BluetoothEventType): Color {
    val dark = LocalDarkTheme.current
    return when (type) {
        BluetoothEventType.BLUETOOTH_ON ->
            if (dark) EventDarkBluetoothOnContainer else EventBluetoothOnContainer

        BluetoothEventType.BLUETOOTH_OFF -> MaterialTheme.colorScheme.errorContainer
        BluetoothEventType.DEVICE_CONNECTED ->
            if (dark) EventDarkDeviceConnectedContainer else EventDeviceConnectedContainer

        BluetoothEventType.DEVICE_DISCONNECTED ->
            if (dark) EventDarkDeviceDisconnectedContainer else EventDeviceDisconnectedContainer
    }
}

@Composable
private fun eventContentColor(type: BluetoothEventType): Color {
    val dark = LocalDarkTheme.current
    return when (type) {
        BluetoothEventType.BLUETOOTH_ON ->
            if (dark) EventDarkBluetoothOnContent else EventBluetoothOnContent

        BluetoothEventType.BLUETOOTH_OFF -> MaterialTheme.colorScheme.onErrorContainer
        BluetoothEventType.DEVICE_CONNECTED ->
            if (dark) EventDarkDeviceConnectedContent else EventDeviceConnectedContent

        BluetoothEventType.DEVICE_DISCONNECTED ->
            if (dark) EventDarkDeviceDisconnectedContent else EventDeviceDisconnectedContent
    }
}
