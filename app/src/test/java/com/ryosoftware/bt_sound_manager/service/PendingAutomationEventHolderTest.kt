package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAutomationEventHolderTest {

    private var elapsedTics = 1_000L
    private val clock = object : AppClock {
        override fun nowMillis(): Long = elapsedTics

        override fun elapsedRealtime(): Long = elapsedTics

        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }
    private val holder = PendingAutomationEventHolder(clock)

    @Test
    fun keepsPendingEventUntilModeMatches() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB, 42L, notification())

        assertNull(holder.takeConfirmed(InterruptMode.NORMAL))
        assertEquals(
            ConfirmedAutomation(
                event = event(),
                desiredMode = InterruptMode.DO_NOT_DISTURB,
                attemptEventId = 42L,
                notification = notification(),
            ),
            holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB),
        )
    }

    @Test
    fun consumesPendingEventOnlyOnce() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB, 42L, notification())
        holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB)

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
    }

    @Test
    fun expiredEventIsNoLongerConfirmedButCanBeTakenAsExpired() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB, 42L, notification())
        elapsedTics += PendingAutomationEventHolder.CONFIRMATION_TIMEOUT_MILLIS + 1

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
        assertEquals(
            ConfirmedAutomation(
                event = event(),
                desiredMode = InterruptMode.DO_NOT_DISTURB,
                attemptEventId = 42L,
                notification = notification(),
            ),
            holder.takeExpired(),
        )
    }

    @Test
    fun takeExpiredDoesNothingBeforeTimeout() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB, 42L, notification())

        assertNull(holder.takeExpired())
        assertEquals(
            event(),
            holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB)?.event,
        )
    }

    @Test
    fun replacingPendingEventKeepsOnlyLatest() {
        val latest = event().copy(mode = InterruptMode.SILENT_ALL)
        holder.set(event(), InterruptMode.DO_NOT_DISTURB, 1L, notification())
        holder.set(latest, InterruptMode.SILENT_ALL, 2L, notification())

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
        assertEquals(
            ConfirmedAutomation(
                event = latest,
                desiredMode = InterruptMode.SILENT_ALL,
                attemptEventId = 2L,
                notification = notification(),
            ),
            holder.takeConfirmed(InterruptMode.SILENT_ALL),
        )
    }

    @Test
    fun clearDiscardsPendingEvent() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB, 42L, notification())

        holder.clear()

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
        assertNull(holder.takeExpired())
    }

    private fun notification() = AutomationNotificationPayload(
        mode = InterruptMode.DO_NOT_DISTURB,
        body = "Headphones is connected",
        showToggleAutomationAction = true,
        showEnableBluetoothAction = false,
    )

    private fun event() = BluetoothEvent(
        timeUtc = 1_000,
        type = BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
        mode = InterruptMode.DO_NOT_DISTURB,
    )
}
