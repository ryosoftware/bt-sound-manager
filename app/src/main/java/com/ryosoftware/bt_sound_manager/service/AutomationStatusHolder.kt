package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.domain.model.AutomationScheduleStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AutomationStatusHolder @Inject constructor() {
    private val mutableStatus = MutableStateFlow<AutomationScheduleStatus?>(null)
    val status: StateFlow<AutomationScheduleStatus?> = mutableStatus.asStateFlow()

    fun update(status: AutomationScheduleStatus?) {
        mutableStatus.value = status
    }
}
