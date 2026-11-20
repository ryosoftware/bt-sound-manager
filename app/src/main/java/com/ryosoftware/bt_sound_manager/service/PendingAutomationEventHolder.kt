package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingAutomationEventHolder @Inject constructor(
    private val clock: AppClock,
) {
    private var pending: PendingAutomationEvent? = null

    @Synchronized
    fun set(event: BluetoothEvent, desiredMode: InterruptMode) {
        pending = PendingAutomationEvent(
            event = event,
            desiredMode = desiredMode,
            requestedAtElapsedTics = clock.elapsedRealtime(),
        )
    }

    @Synchronized
    fun takeConfirmed(currentMode: InterruptMode): BluetoothEvent? {
        val current = pending ?: return null
        if (isExpired(current)) {
            pending = null
            return null
        }
        if (current.desiredMode != currentMode) return null
        pending = null
        return current.event
    }

    @Synchronized
    fun clearExpired() {
        if (pending?.let(::isExpired) == true) pending = null
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    private fun isExpired(pendingEvent: PendingAutomationEvent): Boolean =
        clock.elapsedRealtime() - pendingEvent.requestedAtElapsedTics >
            CONFIRMATION_TIMEOUT_MILLIS

    private data class PendingAutomationEvent(
        val event: BluetoothEvent,
        val desiredMode: InterruptMode,
        val requestedAtElapsedTics: Long,
    )

    companion object {
        const val CONFIRMATION_TIMEOUT_MILLIS = 5_000L
    }
}
