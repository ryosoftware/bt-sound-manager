package com.ryosoftware.bt_sound_manager.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.model.ThemeMode
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toAppSettings() }
        .distinctUntilChanged()

    override suspend fun snapshot(): AppSettings = dataStore.data.first().toAppSettings()

    override suspend fun updateAutomation(transform: (AutomationSettings) -> AutomationSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings().automation)
            preferences[Keys.AUTOMATION_ENABLED] = updated.enabled
            preferences[Keys.AUTOMATION_ALL_DAY] = updated.allDay
            preferences[Keys.AUTOMATION_START_MINUTE] = updated.startMinuteOfDay
            preferences[Keys.AUTOMATION_END_MINUTE] = updated.endMinuteOfDay
            preferences[Keys.AUTOMATION_MONITOR_ALL] = updated.monitorAllDevices
            preferences[Keys.AUTOMATION_SELECTED_DEVICES] =
                updated.selectedDeviceAddresses.mapNotNull(::normalizeAddress).toSet()
            preferences[Keys.AUTOMATION_MODE_CONNECTED] = updated.modeWhenConnected.storageValue
            preferences[Keys.AUTOMATION_MODE_NONE] = updated.modeWhenNone.storageValue
            preferences[Keys.AUTOMATION_FORCE_MODE] = updated.forceMode
            preferences[Keys.AUTOMATION_RESTORE_VOLUME] = updated.restoreMediaVolume
        }
    }

    override suspend fun updateBackground(transform: (BackgroundSettings) -> BackgroundSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings().background)
            preferences[Keys.BACKGROUND_DETECT_BLUETOOTH] = updated.detectBluetoothEvents
            preferences[Keys.BACKGROUND_USER_CHANGE] = updated.reactivateOnUserChange
        }
    }

    override suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings().appearance)
            preferences[Keys.THEME_MODE] = updated.themeMode.storageValue
            preferences[Keys.DYNAMIC_COLOR] = updated.dynamicColor
        }
    }

    override suspend fun updateEvents(transform: (EventsSettings) -> EventsSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings().events)
            preferences[Keys.EVENTS_RETENTION_DAYS] = updated.retentionDays.coerceIn(
                EventsSettings.MIN_RETENTION_DAYS,
                EventsSettings.MAX_RETENTION_DAYS,
            )
        }
    }

    override suspend fun replaceAll(settings: AppSettings) {
        val automation = settings.automation
        val background = settings.background
        val appearance = settings.appearance
        val events = settings.events
        val modesConflict = automation.modeWhenConnected == automation.modeWhenNone
        val connectedMode = if (modesConflict) {
            InterruptMode.DO_NOT_DISTURB
        } else {
            automation.modeWhenConnected
        }
        val noneMode = if (modesConflict) InterruptMode.NORMAL else automation.modeWhenNone
        dataStore.edit { preferences ->
            preferences[Keys.AUTOMATION_ENABLED] = automation.enabled
            preferences[Keys.AUTOMATION_ALL_DAY] = automation.allDay
            preferences[Keys.AUTOMATION_START_MINUTE] = automation.startMinuteOfDay.coerceIn(0, 24 * 60 - 1)
            preferences[Keys.AUTOMATION_END_MINUTE] = automation.endMinuteOfDay.coerceIn(0, 24 * 60 - 1)
            preferences[Keys.AUTOMATION_MONITOR_ALL] = automation.monitorAllDevices
            preferences[Keys.AUTOMATION_SELECTED_DEVICES] =
                automation.selectedDeviceAddresses.mapNotNull(::normalizeAddress).toSet()
            preferences[Keys.AUTOMATION_MODE_CONNECTED] = connectedMode.storageValue
            preferences[Keys.AUTOMATION_MODE_NONE] = noneMode.storageValue
            preferences[Keys.AUTOMATION_FORCE_MODE] = automation.forceMode
            preferences[Keys.AUTOMATION_RESTORE_VOLUME] = automation.restoreMediaVolume
            preferences[Keys.BACKGROUND_DETECT_BLUETOOTH] = background.detectBluetoothEvents ||
                background.reactivateOnUserChange
            preferences[Keys.BACKGROUND_USER_CHANGE] = background.reactivateOnUserChange
            preferences[Keys.THEME_MODE] = appearance.themeMode.storageValue
            preferences[Keys.DYNAMIC_COLOR] = appearance.dynamicColor
            preferences[Keys.EVENTS_RETENTION_DAYS] = events.retentionDays.coerceIn(
                EventsSettings.MIN_RETENTION_DAYS,
                EventsSettings.MAX_RETENTION_DAYS,
            )
        }
    }

    private object Keys {
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation.enabled")
        val AUTOMATION_ALL_DAY = booleanPreferencesKey("automation.all_day")
        val AUTOMATION_START_MINUTE = intPreferencesKey("automation.start_minute")
        val AUTOMATION_END_MINUTE = intPreferencesKey("automation.end_minute")
        val AUTOMATION_MONITOR_ALL = booleanPreferencesKey("automation.monitor_all_devices")
        val AUTOMATION_SELECTED_DEVICES = stringSetPreferencesKey("automation.selected_devices")
        val AUTOMATION_MODE_CONNECTED = stringPreferencesKey("automation.mode_connected")
        val AUTOMATION_MODE_NONE = stringPreferencesKey("automation.mode_none")
        val AUTOMATION_FORCE_MODE = booleanPreferencesKey("automation.force_mode")
        val AUTOMATION_RESTORE_VOLUME = booleanPreferencesKey("automation.restore_media_volume")

        val BACKGROUND_DETECT_BLUETOOTH = booleanPreferencesKey("background.detect_bluetooth")
        val BACKGROUND_USER_CHANGE = booleanPreferencesKey("background.reactivate_on_user_change")

        val THEME_MODE = stringPreferencesKey("appearance.theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")

        val EVENTS_RETENTION_DAYS = intPreferencesKey("events.retention_days")
    }
}

internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    val automationDefaults = defaults.automation
    val automation = AutomationSettings(
        enabled = this[booleanPreferencesKey("automation.enabled")] ?: automationDefaults.enabled,
        allDay = this[booleanPreferencesKey("automation.all_day")] ?: automationDefaults.allDay,
        startMinuteOfDay = (this[intPreferencesKey("automation.start_minute")]
            ?: automationDefaults.startMinuteOfDay).coerceIn(0, 24 * 60 - 1),
        endMinuteOfDay = (this[intPreferencesKey("automation.end_minute")]
            ?: automationDefaults.endMinuteOfDay).coerceIn(0, 24 * 60 - 1),
        monitorAllDevices = this[booleanPreferencesKey("automation.monitor_all_devices")]
            ?: automationDefaults.monitorAllDevices,
        selectedDeviceAddresses = this[stringSetPreferencesKey("automation.selected_devices")]
            ?.mapNotNull(::normalizeAddress)
            ?.toSet()
            ?: emptySet(),
        modeWhenConnected = InterruptMode.fromStorage(
            this[stringPreferencesKey("automation.mode_connected")],
        ) ?: automationDefaults.modeWhenConnected,
        modeWhenNone = InterruptMode.fromStorage(
            this[stringPreferencesKey("automation.mode_none")],
        ) ?: automationDefaults.modeWhenNone,
        forceMode = this[booleanPreferencesKey("automation.force_mode")]
            ?: automationDefaults.forceMode,
        restoreMediaVolume = this[booleanPreferencesKey("automation.restore_media_volume")]
            ?: automationDefaults.restoreMediaVolume,
    )
    val backgroundDefaults = defaults.background
    val background = BackgroundSettings(
        detectBluetoothEvents = this[booleanPreferencesKey("background.detect_bluetooth")]
            ?: backgroundDefaults.detectBluetoothEvents,
        reactivateOnUserChange = this[booleanPreferencesKey("background.reactivate_on_user_change")]
            ?: backgroundDefaults.reactivateOnUserChange,
    )
    val appearance = AppearanceSettings(
        themeMode = ThemeMode.fromStorage(this[stringPreferencesKey("appearance.theme_mode")]),
        dynamicColor = this[booleanPreferencesKey("appearance.dynamic_color")]
            ?: defaults.appearance.dynamicColor,
    )
    val events = EventsSettings(
        retentionDays = (this[intPreferencesKey("events.retention_days")]
            ?: EventsSettings.DEFAULT_RETENTION_DAYS).coerceIn(
            EventsSettings.MIN_RETENTION_DAYS,
            EventsSettings.MAX_RETENTION_DAYS,
        ),
    )
    return AppSettings(
        automation = automation,
        background = background,
        appearance = appearance,
        events = events,
    )
}
