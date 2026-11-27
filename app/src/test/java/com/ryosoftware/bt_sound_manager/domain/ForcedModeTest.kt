package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForcedModeTest {

    private val forced = AutomationSettings(
        modeWhenConnected = InterruptMode.DO_NOT_DISTURB,
        modeWhenNone = InterruptMode.NORMAL,
        forceModeOnDevicesConnected = true,
        forceModeOnDevicesDisconnected = true,
    )

    @Test
    fun ignoresForcingTheNormalModeWithNoDevices() {
        val result = forced.withoutForcedNormalMode()

        assertTrue(result.forceModeOnDevicesConnected)
        assertFalse(result.forceModeOnDevicesDisconnected)
    }

    @Test
    fun ignoresForcingTheNormalModeWithDevices() {
        val result = forced.copy(
            modeWhenConnected = InterruptMode.NORMAL,
            modeWhenNone = InterruptMode.ALARMS_ONLY,
        ).withoutForcedNormalMode()

        assertFalse(result.forceModeOnDevicesConnected)
        assertTrue(result.forceModeOnDevicesDisconnected)
    }
}
