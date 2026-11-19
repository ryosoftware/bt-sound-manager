package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun snapshot(): AppSettings

    suspend fun updateAutomation(transform: (AutomationSettings) -> AutomationSettings)

    suspend fun updateBackground(transform: (BackgroundSettings) -> BackgroundSettings)

    suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings)

    suspend fun updateEvents(transform: (EventsSettings) -> EventsSettings)
}
