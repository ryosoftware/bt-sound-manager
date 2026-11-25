package com.ryosoftware.bt_sound_manager.service

import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterruptModeChangeTracker @Inject constructor(
    private val clock: AppClock,
) {
    private var expectedMode: InterruptMode? = null
    private var expectedAtElapsedTics = 0L
    private var lastObservedMode: InterruptMode? = null

    @Synchronized
    fun markExpected(mode: InterruptMode) {
        expectedMode = mode
        expectedAtElapsedTics = clock.elapsedRealtime()
    }

    @Synchronized
    fun markApplied(mode: InterruptMode) {
        markExpected(mode)
        lastObservedMode = mode
    }

    @Synchronized
    fun cancelExpected() {
        expectedMode = null
    }

    @Synchronized
    fun isExternalChange(currentMode: InterruptMode, baselineMode: InterruptMode?): Boolean {
        val previousMode = lastObservedMode ?: baselineMode
        lastObservedMode = currentMode

        val expected = expectedMode
        if (expected != null &&
            clock.elapsedRealtime() - expectedAtElapsedTics > SUPPRESSION_WINDOW_MILLIS
        ) {
            expectedMode = null
        }

        if (currentMode == expectedMode) {
            expectedMode = null
            return false
        }
        if (currentMode == previousMode) return false
        return true
    }

    private companion object {
        const val SUPPRESSION_WINDOW_MILLIS = 2_000L
    }
}
