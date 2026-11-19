package com.ryosoftware.bt_sound_manager.core.util

import androidx.annotation.StringRes
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode

@StringRes
fun InterruptMode.labelRes(): Int = when (this) {
    InterruptMode.NORMAL -> R.string.mode_normal
    InterruptMode.DO_NOT_DISTURB -> R.string.mode_dnd
    InterruptMode.ALARMS_ONLY -> R.string.mode_alarms
    InterruptMode.SILENT_ALL -> R.string.mode_silent
}
