package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptModeChangeTrackerTest {

    private var elapsedTics = 1_000L
    private val clock = object : AppClock {
        override fun nowMillis(): Long = elapsedTics

        override fun elapsedRealtime(): Long = elapsedTics

        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }
    private val tracker = InterruptModeChangeTracker(clock)

    @Test
    fun detectsChangeDifferentFromLastAppliedMode() {
        assertTrue(
            tracker.isExternalChange(
                currentMode = InterruptMode.DO_NOT_DISTURB,
                baselineMode = InterruptMode.NORMAL,
            ),
        )
    }

    @Test
    fun ignoresChangeMatchingBaseline() {
        assertFalse(
            tracker.isExternalChange(
                currentMode = InterruptMode.NORMAL,
                baselineMode = InterruptMode.NORMAL,
            ),
        )
    }

    @Test
    fun ignoresChangeExpectedByTheApp() {
        tracker.markExpected(InterruptMode.DO_NOT_DISTURB)

        assertFalse(
            tracker.isExternalChange(
                currentMode = InterruptMode.DO_NOT_DISTURB,
                baselineMode = InterruptMode.NORMAL,
            ),
        )
    }

    @Test
    fun ignoresOwnChangeAfterApply() {
        tracker.markApplied(InterruptMode.DO_NOT_DISTURB)

        assertFalse(
            tracker.isExternalChange(
                currentMode = InterruptMode.DO_NOT_DISTURB,
                baselineMode = InterruptMode.NORMAL,
            ),
        )
    }

    @Test
    fun detectsExternalChangeAfterOwnChange() {
        tracker.markApplied(InterruptMode.DO_NOT_DISTURB)

        assertTrue(
            tracker.isExternalChange(
                currentMode = InterruptMode.NORMAL,
                baselineMode = InterruptMode.DO_NOT_DISTURB,
            ),
        )
    }

    @Test
    fun ignoresRepeatedObservation() {
        tracker.isExternalChange(
            currentMode = InterruptMode.DO_NOT_DISTURB,
            baselineMode = InterruptMode.NORMAL,
        )

        assertFalse(
            tracker.isExternalChange(
                currentMode = InterruptMode.DO_NOT_DISTURB,
                baselineMode = InterruptMode.NORMAL,
            ),
        )
    }

    @Test
    fun expiredExpectationNoLongerSuppressesChange() {
        tracker.markExpected(InterruptMode.DO_NOT_DISTURB)
        elapsedTics += 2_001

        assertTrue(
            tracker.isExternalChange(
                currentMode = InterruptMode.DO_NOT_DISTURB,
                baselineMode = InterruptMode.NORMAL,
            ),
        )
    }
}
