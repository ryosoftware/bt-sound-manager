package com.ryosoftware.bt_sound_manager.domain.repository

interface AutomationApplyScheduler {
    fun scheduleDelayed(eventTimeMillis: Long, deviceAddress: String?, connected: Boolean)

    fun scheduleInterruptionFilterChanged()

    fun scheduleUserForeground()
}
