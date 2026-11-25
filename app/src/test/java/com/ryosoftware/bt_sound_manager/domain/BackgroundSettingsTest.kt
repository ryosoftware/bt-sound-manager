package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSettingsTest {

    @Test
    fun anyEnabledIsFalseWhenOnlyReactivateIsEnabled() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = false,
            reactivateOnUserChange = true,
        )

        assertFalse(settings.anyEnabled)
    }

    @Test
    fun anyEnabledIsTrueWhenDetectionIsEnabled() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = true,
            reactivateOnUserChange = false,
        )

        assertTrue(settings.anyEnabled)
    }

    @Test
    fun changingReactivateDoesNotAffectDetection() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = false,
            reactivateOnUserChange = false,
        )

        val updated = settings.copy(reactivateOnUserChange = true)

        assertTrue(updated.reactivateOnUserChange)
        assertFalse(updated.detectBluetoothEvents)
        assertFalse(updated.anyEnabled)
    }

    @Test
    fun disablingDetectionStopsEnablingTheServiceEvenWithReactivateEnabled() {
        val settings = BackgroundSettings(
            detectBluetoothEvents = true,
            reactivateOnUserChange = true,
        )

        val updated = settings.copy(detectBluetoothEvents = false)

        assertTrue(updated.reactivateOnUserChange)
        assertFalse(updated.anyEnabled)
    }
}
