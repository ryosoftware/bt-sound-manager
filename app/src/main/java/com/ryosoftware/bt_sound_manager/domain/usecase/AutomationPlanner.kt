package com.ryosoftware.bt_sound_manager.domain.usecase

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

enum class AutomationOutcome {
    NOT_ENABLED,
    NO_DEVICES_CONFIGURED,
    OUTSIDE_SCHEDULE,
    APPLIED,
    ALREADY_CURRENT,
    WRONG_CURRENT_PROFILE,
    INSUFFICIENT_PERMISSIONS,
}

data class AutomationPlan(
    val outcome: AutomationOutcome,
    val desiredMode: InterruptMode,
    val shouldChange: Boolean,
    val shouldDisplayAppliedNotification: Boolean,
)

class AutomationPlanner {

    fun plan(
        settings: AutomationSettings,
        minuteOfDay: Int,
        connectedMonitoredDevices: Int,
        currentMode: InterruptMode?,
        lastAppliedMode: InterruptMode?,
        canAccessPolicy: Boolean,
        canAccessBluetooth: Boolean = true,
        doNotCheckEnabled: Boolean = false,
    ): AutomationPlan {
        if (!settings.enabled && !doNotCheckEnabled) {
            return AutomationPlan(
                outcome = AutomationOutcome.NOT_ENABLED,
                desiredMode = settings.modeWhenNone,
                shouldChange = false,
                shouldDisplayAppliedNotification = false,
            )
        }

        if (!settings.hasDeviceConfiguration) {
            return AutomationPlan(
                outcome = AutomationOutcome.NO_DEVICES_CONFIGURED,
                desiredMode = settings.modeWhenNone,
                shouldChange = false,
                shouldDisplayAppliedNotification = false,
            )
        }

        if (!ScheduleWindow.isWithin(settings, minuteOfDay)) {
            return AutomationPlan(
                outcome = AutomationOutcome.OUTSIDE_SCHEDULE,
                desiredMode = settings.modeWhenNone,
                shouldChange = false,
                shouldDisplayAppliedNotification = false,
            )
        }

        if (!canAccessBluetooth) {
            return AutomationPlan(
                outcome = AutomationOutcome.INSUFFICIENT_PERMISSIONS,
                desiredMode = settings.modeWhenNone,
                shouldChange = false,
                shouldDisplayAppliedNotification = false,
            )
        }

        val desiredMode = if (connectedMonitoredDevices == 0) {
            settings.modeWhenNone
        } else {
            settings.modeWhenConnected
        }

        if (currentMode == null) {
            return AutomationPlan(
                outcome = if (canAccessPolicy) {
                    AutomationOutcome.WRONG_CURRENT_PROFILE
                } else {
                    AutomationOutcome.INSUFFICIENT_PERMISSIONS
                },
                desiredMode = desiredMode,
                shouldChange = false,
                shouldDisplayAppliedNotification = false,
            )
        }

        val oppositeMode = if (desiredMode == settings.modeWhenConnected) {
            settings.modeWhenNone
        } else {
            settings.modeWhenConnected
        }
        val forceDesiredMode = if (desiredMode == settings.modeWhenConnected) {
            settings.forceModeOnDevicesConnected
        } else {
            settings.forceModeOnDevicesDisconnected
        }
        val appOwnsOppositeMode = currentMode == oppositeMode && lastAppliedMode == oppositeMode
        val shouldChange = currentMode != desiredMode &&
            (forceDesiredMode || appOwnsOppositeMode)

        if (shouldChange) {
            if (!canAccessPolicy) {
                return AutomationPlan(
                    outcome = AutomationOutcome.INSUFFICIENT_PERMISSIONS,
                    desiredMode = desiredMode,
                    shouldChange = true,
                    shouldDisplayAppliedNotification = false,
                )
            }
            return AutomationPlan(
                outcome = if (currentMode == desiredMode) {
                    AutomationOutcome.ALREADY_CURRENT
                } else {
                    AutomationOutcome.APPLIED
                },
                desiredMode = desiredMode,
                shouldChange = currentMode != desiredMode,
                shouldDisplayAppliedNotification = true,
            )
        }

        return if (currentMode == desiredMode) {
            AutomationPlan(
                outcome = AutomationOutcome.ALREADY_CURRENT,
                desiredMode = desiredMode,
                shouldChange = false,
                shouldDisplayAppliedNotification = true,
            )
        } else {
            AutomationPlan(
                outcome = AutomationOutcome.WRONG_CURRENT_PROFILE,
                desiredMode = desiredMode,
                shouldChange = false,
                shouldDisplayAppliedNotification = false,
            )
        }
    }
}
