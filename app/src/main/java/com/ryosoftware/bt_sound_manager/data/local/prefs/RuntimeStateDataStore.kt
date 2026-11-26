package com.ryosoftware.bt_sound_manager.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ryosoftware.bt_sound_manager.domain.model.AutomationRuntimeState
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.RuntimeStateRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RuntimeStateDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : RuntimeStateRepository {

    override val state: Flow<AutomationRuntimeState> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toRuntimeState() }
        .distinctUntilChanged()

    override suspend fun snapshot(): AutomationRuntimeState =
        dataStore.data.first().toRuntimeState()

    override suspend fun saveAppliedMode(mode: InterruptMode, elapsedRealtimeTics: Long) {
        dataStore.edit {
            it[Keys.LAST_APPLIED_MODE] = mode.storageValue
            it[Keys.LAST_EXECUTION_TICS] = elapsedRealtimeTics
            it[Keys.OWNS_MODE] = true
            it.remove(Keys.PENDING_MODE)
        }
    }

    override suspend fun saveCurrentMode(mode: InterruptMode, elapsedRealtimeTics: Long) {
        dataStore.edit {
            val previous = it.toRuntimeState()
            it[Keys.OWNS_MODE] = previous.ownsMode && previous.lastAppliedMode == mode
            it[Keys.LAST_APPLIED_MODE] = mode.storageValue
            it[Keys.LAST_EXECUTION_TICS] = elapsedRealtimeTics
        }
    }

    override suspend fun releaseModeOwnership() {
        dataStore.edit { it[Keys.OWNS_MODE] = false }
    }

    override suspend fun savePendingMode(mode: InterruptMode) {
        dataStore.edit { it[Keys.PENDING_MODE] = mode.storageValue }
    }

    override suspend fun clearPendingMode() {
        dataStore.edit { it.remove(Keys.PENDING_MODE) }
    }

    override suspend fun savePreviousMediaVolume(volume: Int) {
        dataStore.edit { it[Keys.PREVIOUS_MEDIA_VOLUME] = volume }
    }

    override suspend fun clearLastExecution() {
        dataStore.edit {
            // The mode and its ownership survive a reboot; only the timestamp,
            // relative to the previous boot, is no longer valid.
            it.remove(Keys.LAST_EXECUTION_TICS)
        }
    }

    private fun Preferences.toRuntimeState(): AutomationRuntimeState {
        val lastAppliedMode = InterruptMode.fromStorage(this[Keys.LAST_APPLIED_MODE])
        return AutomationRuntimeState(
            lastAppliedMode = lastAppliedMode,
            lastExecutionElapsedTics = this[Keys.LAST_EXECUTION_TICS] ?: 0L,
            previousMediaVolume = this[Keys.PREVIOUS_MEDIA_VOLUME],
            // Data saved by earlier versions treated the last applied mode as owned.
            ownsMode = this[Keys.OWNS_MODE] ?: (lastAppliedMode != null),
            pendingMode = InterruptMode.fromStorage(this[Keys.PENDING_MODE]),
        )
    }

    private object Keys {
        val LAST_APPLIED_MODE = stringPreferencesKey("runtime.last_applied_mode")
        val LAST_EXECUTION_TICS = longPreferencesKey("runtime.last_execution_tics")
        val PREVIOUS_MEDIA_VOLUME = intPreferencesKey("runtime.previous_media_volume")
        val OWNS_MODE = booleanPreferencesKey("runtime.owns_mode")
        val PENDING_MODE = stringPreferencesKey("runtime.pending_mode")
    }
}
