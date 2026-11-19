package com.ryosoftware.bt_sound_manager.presentation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class AppRecreationSignal @Inject constructor() {

    private val recreationRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = recreationRequests.asSharedFlow()

    fun request() {
        recreationRequests.tryEmit(Unit)
    }
}
