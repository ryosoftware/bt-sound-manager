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
        holder.set(event(), InterruptMode.DO_NOT_DISTURB)

        assertNull(holder.takeConfirmed(InterruptMode.NORMAL))
        assertEquals(
            event(),
            holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB),
        )
    }

    @Test
    fun consumesPendingEventOnlyOnce() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB)
        holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB)

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
    }

    @Test
    fun discardsPendingEventAfterTimeout() {
        holder.set(event(), InterruptMode.DO_NOT_DISTURB)
        elapsedTics += PendingAutomationEventHolder.CONFIRMATION_TIMEOUT_MILLIS + 1

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
    }

    @Test
    fun replacingPendingEventKeepsOnlyLatest() {
        val latest = event().copy(mode = InterruptMode.SILENT_ALL)
        holder.set(event(), InterruptMode.DO_NOT_DISTURB)
        holder.set(latest, InterruptMode.SILENT_ALL)

        assertNull(holder.takeConfirmed(InterruptMode.DO_NOT_DISTURB))
        assertEquals(latest, holder.takeConfirmed(InterruptMode.SILENT_ALL))
    }

    private fun event() = BluetoothEvent(
        timeUtc = 1_000,
        type = BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
        mode = InterruptMode.DO_NOT_DISTURB,
    )
}
