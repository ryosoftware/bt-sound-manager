package com.ryosoftware.bt_sound_manager.domain.repository

import android.app.Notification
import com.ryosoftware.bt_sound_manager.domain.model.AutomationScheduleStatus
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

interface MonitorNotificationRepository {
    fun ensureChannels()

    fun showDeviceConnected(deviceName: String, address: String)

    fun showDeviceDisconnected(deviceName: String, address: String)

    fun hideDeviceConnected()

    fun hideDeviceDisconnected()

    fun showBluetoothEnabled()

    fun showBluetoothDisabled()

    fun hideBluetoothDisabled()

    fun showAutomationNotification(
        mode: InterruptMode?,
        body: String,
        showToggleAutomationAction: Boolean,
        showEnableBluetoothAction: Boolean,
        showPolicySettingsAction: Boolean = false,
        attempting: Boolean = false,
        ruleSettingsId: String? = null,
    )

    fun hideAutomationNotification()

    fun buildBackgroundNotification(status: AutomationScheduleStatus?): Notification

    fun updateBackgroundNotification(status: AutomationScheduleStatus?)
}
