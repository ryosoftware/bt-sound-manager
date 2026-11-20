package com.ryosoftware.bt_sound_manager.presentation.components

import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun mapsAutomationModeIcons() {
        assertEquals(R.drawable.ic_normal_mode_applied, automationModeIconRes(InterruptMode.NORMAL))
        assertEquals(
            R.drawable.ic_dnd_mode_applied,
            automationModeIconRes(InterruptMode.DO_NOT_DISTURB),
        )
        assertEquals(
            R.drawable.ic_only_alarms_mode_applied,
            automationModeIconRes(InterruptMode.ALARMS_ONLY),
        )
        assertEquals(
            R.drawable.ic_silent_mode_applied,
            automationModeIconRes(InterruptMode.SILENT_ALL),
        )
        assertEquals(
            R.drawable.ic_normal_mode_applied,
            automationModeIconRes(null),
        )
    }
}
