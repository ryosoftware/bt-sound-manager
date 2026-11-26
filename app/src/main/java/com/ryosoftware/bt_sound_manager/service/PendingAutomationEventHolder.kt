package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import javax.inject.Inject
import javax.inject.Singleton

data class AutomationNotificationPayload(
    val mode: InterruptMode,
    val body: String,
    val showToggleAutomationAction: Boolean,
    val showEnableBluetoothAction: Boolean,
)

data class ConfirmedAutomation(
    val event: BluetoothEvent,
    val desiredMode: InterruptMode,
    val attemptEventId: Long,
    val notification: AutomationNotificationPayload,
    val requestedAtElapsedTics: Long,
)

@Singleton
class PendingAutomationEventHolder @Inject constructor(
    private val clock: AppClock,
) {
    private var pending: PendingAutomationEvent? = null

    @Synchronized
    fun set(
        event: BluetoothEvent,
        desiredMode: InterruptMode,
        attemptEventId: Long,
        notification: AutomationNotificationPayload,
    ) {
        pending = PendingAutomationEvent(
            event = event,
            desiredMode = desiredMode,
            attemptEventId = attemptEventId,
            notification = notification,
            requestedAtElapsedTics = clock.elapsedRealtime(),
        )
    }

    @Synchronized
    fun takeConfirmed(currentMode: InterruptMode): ConfirmedAutomation? {
        val current = pending ?: return null
        if (isExpired(current)) return null
        if (current.desiredMode != currentMode) return null
        pending = null
        return current.toConfirmed()
    }

    @Synchronized
    fun takeExpired(): ConfirmedAutomation? {
        val current = pending ?: return null
        if (!isExpired(current)) return null
        pending = null
        return current.toConfirmed()
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    private fun isExpired(pendingEvent: PendingAutomationEvent): Boolean =
        clock.elapsedRealtime() - pendingEvent.requestedAtElapsedTics >
            CONFIRMATION_TIMEOUT_MILLIS

    private fun PendingAutomationEvent.toConfirmed(): ConfirmedAutomation =
        ConfirmedAutomation(
            event = event,
            desiredMode = desiredMode,
            attemptEventId = attemptEventId,
            notification = notification,
            requestedAtElapsedTics = requestedAtElapsedTics,
        )

    private data class PendingAutomationEvent(
        val event: BluetoothEvent,
        val desiredMode: InterruptMode,
        val attemptEventId: Long,
        val notification: AutomationNotificationPayload,
        val requestedAtElapsedTics: Long,
    )

    companion object {
        const val CONFIRMATION_TIMEOUT_MILLIS = 5_000L
    }
}
