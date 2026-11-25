package com.ryosoftware.bt_sound_manager.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MonitorServiceState {
    private val runningState = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = runningState.asStateFlow()

    private val shutdownState = MutableStateFlow(false)
    val shutdownDetected: StateFlow<Boolean> = shutdownState.asStateFlow()

    fun setRunning(value: Boolean) {
        runningState.value = value
    }

    fun setShutdownDetected(value: Boolean) {
        shutdownState.value = value
    }
}
