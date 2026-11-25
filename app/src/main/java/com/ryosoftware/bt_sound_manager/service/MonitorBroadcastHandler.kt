package com.ryosoftware.bt_sound_manager.service

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorBroadcastHandler @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val eventProcessor: EventProcessor,
) {

    suspend fun handle(intent: Intent, source: MonitorBroadcastSource): Boolean {
        val action = intent.action ?: return false
        val settings = settingsRepository.snapshot()
        val serviceRunning = MonitorServiceState.isRunning.value
        val dispatch = if (source == MonitorBroadcastSource.RECEIVER) {
            AutomationDispatch.DEFERRED
        } else {
            AutomationDispatch.DIRECT
        }
        val canAccessBluetooth = bluetoothRepository.hasPermissions()
        val handleBluetooth = canAccessBluetooth && when (source) {
            MonitorBroadcastSource.SERVICE -> settings.background.detectBluetoothEvents
            MonitorBroadcastSource.RECEIVER ->
                !(serviceRunning && settings.background.detectBluetoothEvents)
        }
        val handleUser = canAccessBluetooth && when (source) {
            MonitorBroadcastSource.SERVICE ->
                settings.background.detectBluetoothEvents &&
                    settings.background.reactivateOnUserChange

            MonitorBroadcastSource.RECEIVER ->
                settings.background.detectBluetoothEvents &&
                    !(serviceRunning && settings.background.reactivateOnUserChange)
        }

        return when (action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                if (handleBluetooth) {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF,
                    )
                    when (state) {
                        BluetoothAdapter.STATE_ON -> eventProcessor.onBluetoothStateChanged(
                            enabled = true,
                            eventTimeMillis = eventProcessor.nowMillis(),
                            dispatch = dispatch,
                        )

                        BluetoothAdapter.STATE_TURNING_OFF -> eventProcessor.onBluetoothStateChanged(
                            enabled = false,
                            eventTimeMillis = eventProcessor.nowMillis(),
                            dispatch = dispatch,
                        )
                    }
                }
                false
            }

            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            -> {
                if (handleBluetooth) {
                    val device = intent.bluetoothDevice()
                    if (device != null) {
                        val address = try {
                            device.address
                        } catch (_: SecurityException) {
                            null
                        }
                        val name = try {
                            device.name
                        } catch (_: SecurityException) {
                            null
                        }
                        eventProcessor.onDeviceStateChanged(
                            address = address,
                            name = name,
                            connected = action == BluetoothDevice.ACTION_ACL_CONNECTED,
                            eventTimeMillis = eventProcessor.nowMillis(),
                            dispatch = dispatch,
                        )
                    }
                }
                false
            }

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                if (handleBluetooth) {
                    eventProcessor.onInterruptionFilterChanged(dispatch = dispatch)
                }
                false
            }

            Intent.ACTION_USER_FOREGROUND,
            Intent.ACTION_USER_PRESENT,
            -> {
                if (handleUser) {
                    eventProcessor.onUserForeground(dispatch = dispatch)
                }
                false
            }

            Intent.ACTION_USER_BACKGROUND -> {
                if (handleUser) {
                    eventProcessor.onUserBackground()
                }
                false
            }

            Intent.ACTION_SHUTDOWN -> {
                eventProcessor.onShutdown()
                true
            }

            else -> false
        }
    }

    fun intentFilter(): IntentFilter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        addAction(Intent.ACTION_USER_FOREGROUND)
        addAction(Intent.ACTION_USER_BACKGROUND)
        addAction(Intent.ACTION_USER_PRESENT)
        addAction(Intent.ACTION_SHUTDOWN)
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
