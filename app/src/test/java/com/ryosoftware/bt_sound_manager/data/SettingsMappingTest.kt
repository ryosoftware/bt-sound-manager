package com.ryosoftware.bt_sound_manager.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ryosoftware.bt_sound_manager.data.local.prefs.toAppSettings
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {

    @Test
    fun defaultsMatchProductBehaviour() {
        val settings = mutablePreferencesOf().toAppSettings()
        assertFalse(settings.automation.enabled)
        assertTrue(settings.automation.allDay)
        assertEquals(6 * 60, settings.automation.startMinuteOfDay)
        assertEquals(22 * 60, settings.automation.endMinuteOfDay)
        assertEquals(InterruptMode.DO_NOT_DISTURB, settings.automation.modeWhenConnected)
        assertEquals(InterruptMode.NORMAL, settings.automation.modeWhenNone)
        assertFalse(settings.automation.forceModeOnDevicesConnected)
        assertFalse(settings.automation.forceModeOnDevicesDisconnected)
        assertFalse(settings.automation.restoreMediaVolume)
        assertFalse(settings.background.detectBluetoothEvents)
        assertFalse(settings.background.reactivateOnUserChange)
        assertEquals(ThemeMode.SYSTEM, settings.appearance.themeMode)
        assertTrue(settings.appearance.dynamicColor)
        assertEquals(EventsSettings.DEFAULT_RETENTION_DAYS, settings.events.retentionDays)
    }

    @Test
    fun mapsStoredValuesAndNormalizesAddresses() {
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey("automation.enabled") to true,
            booleanPreferencesKey("automation.all_day") to false,
            intPreferencesKey("automation.start_minute") to (7 * 60),
            intPreferencesKey("automation.end_minute") to (23 * 60),
            booleanPreferencesKey("automation.monitor_all_devices") to true,
            stringPreferencesKey("automation.mode_connected") to "alarms-only",
            stringPreferencesKey("automation.mode_none") to "silent-all",
            booleanPreferencesKey("automation.force_mode_on_devices_connected") to true,
            booleanPreferencesKey("automation.force_mode_on_devices_disconnected") to false,
            booleanPreferencesKey("automation.restore_media_volume") to true,
            booleanPreferencesKey("background.detect_bluetooth") to true,
            booleanPreferencesKey("background.reactivate_on_user_change") to true,
            stringPreferencesKey("appearance.theme_mode") to "dark",
            booleanPreferencesKey("appearance.dynamic_color") to false,
            intPreferencesKey("events.retention_days") to 30,
        )

        val settings = preferences.toAppSettings()
        assertTrue(settings.automation.enabled)
        assertFalse(settings.automation.allDay)
        assertEquals(7 * 60, settings.automation.startMinuteOfDay)
        assertEquals(23 * 60, settings.automation.endMinuteOfDay)
        assertEquals(InterruptMode.ALARMS_ONLY, settings.automation.modeWhenConnected)
        assertEquals(InterruptMode.SILENT_ALL, settings.automation.modeWhenNone)
        assertTrue(settings.automation.forceModeOnDevicesConnected)
        assertFalse(settings.automation.forceModeOnDevicesDisconnected)
        assertTrue(settings.background.detectBluetoothEvents)
        assertTrue(settings.background.reactivateOnUserChange)
        assertEquals(ThemeMode.DARK, settings.appearance.themeMode)
        assertFalse(settings.appearance.dynamicColor)
        assertEquals(30, settings.events.retentionDays)
    }

    @Test
    fun legacyForceModeEnablesBothDirections() {
        val settings = mutablePreferencesOf(
            booleanPreferencesKey("automation.force_mode") to true,
        ).toAppSettings()

        assertTrue(settings.automation.forceModeOnDevicesConnected)
        assertTrue(settings.automation.forceModeOnDevicesDisconnected)
    }

    @Test
    fun newForceModeKeysTakePrecedenceOverLegacyKey() {
        val settings = mutablePreferencesOf(
            booleanPreferencesKey("automation.force_mode") to true,
            booleanPreferencesKey("automation.force_mode_on_devices_connected") to false,
            booleanPreferencesKey("automation.force_mode_on_devices_disconnected") to true,
        ).toAppSettings()

        assertFalse(settings.automation.forceModeOnDevicesConnected)
        assertTrue(settings.automation.forceModeOnDevicesDisconnected)
    }

    @Test
    fun clampsRetentionDaysToSupportedRange() {
        val belowMinimum = mutablePreferencesOf(
            intPreferencesKey("events.retention_days") to 1,
        ).toAppSettings()
        val aboveMaximum = mutablePreferencesOf(
            intPreferencesKey("events.retention_days") to 500,
        ).toAppSettings()

        assertEquals(EventsSettings.MIN_RETENTION_DAYS, belowMinimum.events.retentionDays)
        assertEquals(EventsSettings.MAX_RETENTION_DAYS, aboveMaximum.events.retentionDays)
    }
}
