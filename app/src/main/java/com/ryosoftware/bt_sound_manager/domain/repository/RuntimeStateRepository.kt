package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.AutomationRuntimeState
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import kotlinx.coroutines.flow.Flow

interface RuntimeStateRepository {
    val state: Flow<AutomationRuntimeState>

    suspend fun snapshot(): AutomationRuntimeState

    /** The app set [mode]: it becomes the owned mode and nothing is pending. */
    suspend fun saveAppliedMode(mode: InterruptMode, elapsedRealtimeTics: Long)

    /**
     * [mode] was already active. It never grants ownership, which is only kept if
     * [mode] is the last applied one.
     */
    suspend fun saveCurrentMode(mode: InterruptMode, elapsedRealtimeTics: Long)

    suspend fun releaseModeOwnership()

    suspend fun savePendingMode(mode: InterruptMode)

    suspend fun clearPendingMode()

    suspend fun savePreviousMediaVolume(volume: Int)

    suspend fun clearLastExecution()
}
