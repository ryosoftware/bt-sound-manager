package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

interface InterruptModeRepository {
    fun canAccessPolicy(): Boolean

    fun currentMode(): InterruptMode?

    fun setMode(mode: InterruptMode): Boolean
}
