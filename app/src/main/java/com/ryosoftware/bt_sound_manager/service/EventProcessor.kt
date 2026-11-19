package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationApplyScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.MonitorNotificationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventProcessor @Inject constructor(
    private val eventRepository: BluetoothEventRepository,
    private val deviceRepository: DeviceRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val notificationRepository: MonitorNotificationRepository,
    private val automationCoordinator: AutomationCoordinator,
    private val automationApplyScheduler: AutomationApplyScheduler,
    private val clock: AppClock,
) {

    suspend fun onBluetoothStateChanged(
        enabled: Boolean,
        eventTimeMillis: Long,
        dispatch: AutomationDispatch = AutomationDispatch.DIRECT,
    ) {
        val type = if (enabled) {
            BluetoothEventType.BLUETOOTH_ON
        } else {
            BluetoothEventType.BLUETOOTH_OFF
        }
        eventRepository.addEvent(
            BluetoothEvent(
                timeUtc = eventTimeMillis,
                type = type,
            ),
        )
        if (enabled) {
            notificationRepository.hideDeviceDisconnected()
            notificationRepository.showBluetoothEnabled()
        } else {
            bluetoothRepository.clearConnectionState()
            notificationRepository.hideDeviceConnected()
            notificationRepository.showBluetoothDisabled()
            if (dispatch == AutomationDispatch.DEFERRED) {
                automationApplyScheduler.scheduleDelayed(
                    eventTimeMillis = eventTimeMillis,
                    deviceAddress = null,
                    connected = false,
                )
            } else {
                automationCoordinator.applyDelayed(
                    eventTimeMillis = eventTimeMillis,
                    deviceAddress = null,
                    connected = false,
                )
            }
        }
    }

    suspend fun onDeviceStateChanged(
        address: String?,
        name: String?,
        connected: Boolean,
        eventTimeMillis: Long,
        dispatch: AutomationDispatch = AutomationDispatch.DIRECT,
    ) {
        val normalizedAddress = normalizeAddress(address) ?: return
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        bluetoothRepository.updateConnectionState(normalizedAddress, cleanName, connected)
        if (cleanName != null) {
            deviceRepository.saveDeviceName(normalizedAddress, cleanName)
        }
        val type = if (connected) {
            BluetoothEventType.DEVICE_CONNECTED
        } else {
            BluetoothEventType.DEVICE_DISCONNECTED
        }
        eventRepository.addEvent(
            BluetoothEvent(
                timeUtc = eventTimeMillis,
                type = type,
                deviceAddress = normalizedAddress,
                deviceName = cleanName,
            ),
        )

        val displayName = cleanName
            ?: deviceRepository.getDeviceName(normalizedAddress)
            ?: normalizedAddress
        if (connected) {
            notificationRepository.hideDeviceDisconnected()
            notificationRepository.showDeviceConnected(displayName, normalizedAddress)
        } else {
            notificationRepository.hideDeviceConnected()
            notificationRepository.showDeviceDisconnected(displayName, normalizedAddress)
        }

        if (dispatch == AutomationDispatch.DEFERRED) {
            automationApplyScheduler.scheduleDelayed(
                eventTimeMillis = eventTimeMillis,
                deviceAddress = normalizedAddress,
                connected = connected,
            )
        } else {
            automationCoordinator.applyDelayed(
                eventTimeMillis = eventTimeMillis,
                deviceAddress = normalizedAddress,
                connected = connected,
            )
        }
    }

    suspend fun onInterruptionFilterChanged(
        dispatch: AutomationDispatch = AutomationDispatch.DIRECT,
    ) {
        if (dispatch == AutomationDispatch.DEFERRED) {
            automationApplyScheduler.scheduleInterruptionFilterChanged()
        } else {
            automationCoordinator.onInterruptModeChanged()
        }
    }

    suspend fun onUserForeground(dispatch: AutomationDispatch = AutomationDispatch.DIRECT) {
        if (dispatch == AutomationDispatch.DEFERRED) {
            automationApplyScheduler.scheduleUserForeground()
        } else {
            automationCoordinator.onUserForeground()
        }
    }

    suspend fun onUserBackground() {
        // Nothing to do until the user comes back to the foreground.
    }

    suspend fun onBoot() {
        MonitorServiceState.setShutdownDetected(false)
        automationCoordinator.onBoot()
    }

    suspend fun onPackageReplaced() {
        MonitorServiceState.setShutdownDetected(false)
        automationCoordinator.apply(
            doNotCheckEnabled = false,
            doNotDisplayOutsideSchedule = true,
            forceNotification = true,
        )
    }

    suspend fun onShutdown() {
        MonitorServiceState.setShutdownDetected(true)
    }

    fun nowMillis(): Long = clock.nowMillis()
}
