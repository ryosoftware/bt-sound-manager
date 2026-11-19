package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationOutcome
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationPlannerTest {

    private val planner = AutomationPlanner()

    private fun settings(
        enabled: Boolean = true,
        allDay: Boolean = true,
        monitorAll: Boolean = true,
        selected: Set<String> = emptySet(),
    ) = AutomationSettings(
        enabled = enabled,
        allDay = allDay,
        monitorAllDevices = monitorAll,
        selectedDeviceAddresses = selected,
        modeWhenConnected = InterruptMode.DO_NOT_DISTURB,
        modeWhenNone = InterruptMode.NORMAL,
    )

    @Test
    fun disabledAutomationIsIgnored() {
        val result = planner.plan(
            settings = settings(enabled = false),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = null,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.NOT_ENABLED, result.outcome)
        assertFalse(result.shouldChange)
    }

    @Test
    fun noDevicesConfiguredIsIgnored() {
        val result = planner.plan(
            settings = settings(monitorAll = false, selected = emptySet()),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = null,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.NO_DEVICES_CONFIGURED, result.outcome)
    }

    @Test
    fun outsideScheduleIsIgnored() {
        val result = planner.plan(
            settings = settings(allDay = false).copy(
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 12 * 60,
            ),
            minuteOfDay = 13 * 60,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = null,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.OUTSIDE_SCHEDULE, result.outcome)
    }

    @Test
    fun connectedDeviceAppliesConnectedMode() {
        val result = planner.plan(
            settings = settings(),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = null,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.APPLIED, result.outcome)
        assertEquals(InterruptMode.DO_NOT_DISTURB, result.desiredMode)
        assertTrue(result.shouldChange)
        assertTrue(result.shouldDisplayAppliedNotification)
    }

    @Test
    fun noConnectedDeviceAppliesNoneMode() {
        val result = planner.plan(
            settings = settings(),
            minuteOfDay = 600,
            connectedMonitoredDevices = 0,
            currentMode = InterruptMode.DO_NOT_DISTURB,
            lastAppliedMode = InterruptMode.DO_NOT_DISTURB,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.APPLIED, result.outcome)
        assertEquals(InterruptMode.NORMAL, result.desiredMode)
    }

    @Test
    fun currentModeAlreadyDesiredShowsAppliedNotification() {
        val result = planner.plan(
            settings = settings(),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.DO_NOT_DISTURB,
            lastAppliedMode = InterruptMode.DO_NOT_DISTURB,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.ALREADY_CURRENT, result.outcome)
        assertFalse(result.shouldChange)
        assertTrue(result.shouldDisplayAppliedNotification)
    }

    @Test
    fun unrelatedCurrentModeIsNotOverwritten() {
        val result = planner.plan(
            settings = settings(),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.SILENT_ALL,
            lastAppliedMode = InterruptMode.NORMAL,
            canAccessPolicy = true,
        )
        assertEquals(AutomationOutcome.WRONG_CURRENT_PROFILE, result.outcome)
        assertFalse(result.shouldChange)
    }

    @Test
    fun forceNotificationReappliesMode() {
        val result = planner.plan(
            settings = settings(),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = InterruptMode.NORMAL,
            canAccessPolicy = true,
            forceNotification = true,
        )
        assertEquals(AutomationOutcome.APPLIED, result.outcome)
        assertTrue(result.shouldChange)
    }

    @Test
    fun missingPolicyAccessBlocksChange() {
        val result = planner.plan(
            settings = settings(),
            minuteOfDay = 600,
            connectedMonitoredDevices = 1,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = null,
            canAccessPolicy = false,
        )
        assertEquals(AutomationOutcome.INSUFFICIENT_PERMISSIONS, result.outcome)
    }

    @Test
    fun doNotCheckEnabledAllowsBootEvaluation() {
        val result = planner.plan(
            settings = settings(enabled = false),
            minuteOfDay = 600,
            connectedMonitoredDevices = 0,
            currentMode = InterruptMode.NORMAL,
            lastAppliedMode = null,
            canAccessPolicy = true,
            doNotCheckEnabled = true,
        )
        assertEquals(AutomationOutcome.ALREADY_CURRENT, result.outcome)
    }
}
