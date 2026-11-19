package com.ryosoftware.bt_sound_manager.domain.model

enum class InterruptMode(val storageValue: String) {
    NORMAL("normal"),
    DO_NOT_DISTURB("do-not-disturb"),
    ALARMS_ONLY("alarms-only"),
    SILENT_ALL("silent-all"),
    ;

    companion object {
        fun fromStorage(value: String?): InterruptMode? =
            entries.firstOrNull { it.storageValue == value }
    }
}
