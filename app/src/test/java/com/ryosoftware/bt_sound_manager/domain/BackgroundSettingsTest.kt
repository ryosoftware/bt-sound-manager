package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSettingsTest {

    @Test
    fun enablingReactivateOnUserChangeEnablesDetection() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = false,
            reactivateOnUserChange = false,
        )

        val updated = settings.withReactivateOnUserChange(true)

        assertTrue(updated.reactivateOnUserChange)
        assertTrue(updated.detectBluetoothEvents)
    }

    @Test
    fun disablingReactivateOnUserChangeKeepsDetection() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = true,
            reactivateOnUserChange = true,
        )

        val updated = settings.withReactivateOnUserChange(false)

        assertFalse(updated.reactivateOnUserChange)
        assertTrue(updated.detectBluetoothEvents)
    }

    @Test
    fun disablingReactivateOnUserChangeKeepsDetectionOffWhenItWasOff() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = false,
            reactivateOnUserChange = true,
        )

        val updated = settings.withReactivateOnUserChange(false)

        assertFalse(updated.reactivateOnUserChange)
        assertFalse(updated.detectBluetoothEvents)
    }
}
