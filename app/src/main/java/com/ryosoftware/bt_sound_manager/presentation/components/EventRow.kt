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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.ui.res.painterResource
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
import com.ryosoftware.bt_sound_manager.R
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
    val modeName = event.mode?.let { modeLabel(context, it) }
    val title = when (event.type) {
        BluetoothEventType.DEVICE_CONNECTED,
        BluetoothEventType.DEVICE_DISCONNECTED,
        -> deviceName?.takeIf { it.isNotBlank() } ?: address.orEmpty()

        BluetoothEventType.AUTOMATION_APPLIED_CONNECTED -> {
            val connectedName = event.deviceName?.takeIf { it.isNotBlank() }
            if (modeName != null && connectedName != null) {
                context.getString(
                    R.string.event_automation_applied_connected,
                    modeName,
                    connectedName,
                )
            } else {
                eventLabel
            }
        }

        BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES ->
            modeName?.let {
                context.getString(R.string.event_automation_applied_no_devices, it)
            } ?: eventLabel

        BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF ->
            modeName?.let {
                context.getString(R.string.event_automation_applied_bluetooth_off, it)
            } ?: eventLabel

        BluetoothEventType.EXTERNAL_MODE_CHANGED ->
            modeName?.let {
                context.getString(R.string.event_external_mode_changed, it)
            } ?: eventLabel

        BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED -> {
            val connectedName = event.deviceName?.takeIf { it.isNotBlank() }
            if (modeName != null && connectedName != null) {
                context.getString(
                    R.string.event_automation_attempting_connected,
                    modeName,
                    connectedName,
                )
            } else {
                eventLabel
            }
        }

        BluetoothEventType.AUTOMATION_ATTEMPTING_NO_DEVICES ->
            modeName?.let {
                context.getString(R.string.event_automation_attempting_no_devices, it)
            } ?: eventLabel

        BluetoothEventType.AUTOMATION_ATTEMPTING_BLUETOOTH_OFF ->
            modeName?.let {
                context.getString(R.string.event_automation_attempting_bluetooth_off, it)
            } ?: eventLabel

        BluetoothEventType.AUTOMATION_FAILED_CONNECTED -> {
            val connectedName = event.deviceName?.takeIf { it.isNotBlank() }
            if (modeName != null && connectedName != null) {
                context.getString(
                    R.string.event_automation_failed_connected,
                    modeName,
                    connectedName,
                )
            } else {
                eventLabel
            }
        }

        BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES ->
            modeName?.let {
                context.getString(R.string.event_automation_failed_no_devices, it)
            } ?: eventLabel

        BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF ->
            modeName?.let {
                context.getString(R.string.event_automation_failed_bluetooth_off, it)
            } ?: eventLabel

        BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS ->
            modeName?.let {
                context.getString(R.string.event_automation_failed_permissions, it)
            } ?: eventLabel

        BluetoothEventType.BLUETOOTH_ON,
        BluetoothEventType.BLUETOOTH_OFF,
        -> eventLabel
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
                    val eventIconPainter = when (event.type) {
                        BluetoothEventType.DEVICE_CONNECTED -> painterResource(R.drawable.ic_bluetooth_device_connected)
                        BluetoothEventType.DEVICE_DISCONNECTED -> painterResource(R.drawable.ic_bluetooth_device_disconnected)
                        BluetoothEventType.BLUETOOTH_ON -> painterResource(R.drawable.ic_bluetooth_on)
                        BluetoothEventType.BLUETOOTH_OFF -> painterResource(R.drawable.ic_bluetooth_off)
                        BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
                        BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
                        BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF,
                        BluetoothEventType.EXTERNAL_MODE_CHANGED,
                        BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED,
                        BluetoothEventType.AUTOMATION_ATTEMPTING_NO_DEVICES,
                        BluetoothEventType.AUTOMATION_ATTEMPTING_BLUETOOTH_OFF,
                        BluetoothEventType.AUTOMATION_FAILED_CONNECTED,
                        BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES,
                        BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF,
                        BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS,
                        -> painterResource(automationModeIconRes(event.mode))
                    }
                    Icon(
                        painter = eventIconPainter,
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

        BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
        BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
        BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF,
        -> MaterialTheme.colorScheme.tertiaryContainer

        BluetoothEventType.EXTERNAL_MODE_CHANGED ->
            MaterialTheme.colorScheme.secondaryContainer

        BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED,
        BluetoothEventType.AUTOMATION_ATTEMPTING_NO_DEVICES,
        BluetoothEventType.AUTOMATION_ATTEMPTING_BLUETOOTH_OFF,
        -> MaterialTheme.colorScheme.surfaceVariant

        BluetoothEventType.AUTOMATION_FAILED_CONNECTED,
        BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES,
        BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF,
        BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS,
        -> MaterialTheme.colorScheme.errorContainer
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

        BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
        BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
        BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF,
        -> MaterialTheme.colorScheme.onTertiaryContainer

        BluetoothEventType.EXTERNAL_MODE_CHANGED ->
            MaterialTheme.colorScheme.onSecondaryContainer

        BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED,
        BluetoothEventType.AUTOMATION_ATTEMPTING_NO_DEVICES,
        BluetoothEventType.AUTOMATION_ATTEMPTING_BLUETOOTH_OFF,
        -> MaterialTheme.colorScheme.onSurfaceVariant

        BluetoothEventType.AUTOMATION_FAILED_CONNECTED,
        BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES,
        BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF,
        BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS,
        -> MaterialTheme.colorScheme.onErrorContainer
    }
}
