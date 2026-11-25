package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.AutomationRuntimeState
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import kotlinx.coroutines.flow.Flow

interface RuntimeStateRepository {
    val state: Flow<AutomationRuntimeState>

    suspend fun snapshot(): AutomationRuntimeState

    suspend fun saveAppliedMode(mode: InterruptMode, elapsedRealtimeTics: Long)

    suspend fun savePreviousMediaVolume(volume: Int)

    suspend fun clearLastExecution()
}
