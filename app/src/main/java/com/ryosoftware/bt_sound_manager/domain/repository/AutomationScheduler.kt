package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings

interface AutomationScheduler {
    fun scheduleNextTransition(settings: AutomationSettings)

    fun cancel()
}
