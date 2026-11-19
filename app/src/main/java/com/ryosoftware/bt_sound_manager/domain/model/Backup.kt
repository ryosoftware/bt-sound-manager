package com.ryosoftware.bt_sound_manager.domain.model

data class BackupContents(
    val createdAtUtcMillis: Long,
    val appVersion: String,
    val events: List<BluetoothEvent>,
    val settings: AppSettings,
)

data class BackupSelection(
    val events: Boolean = false,
    val settings: Boolean = false,
) {
    val anySelected: Boolean
        get() = events || settings
}

data class BackupSummary(
    val createdAtUtcMillis: Long,
    val appVersion: String,
    val eventCount: Int,
) {
    companion object {
        fun from(contents: BackupContents): BackupSummary = BackupSummary(
            createdAtUtcMillis = contents.createdAtUtcMillis,
            appVersion = contents.appVersion,
            eventCount = contents.events.size,
        )
    }
}

data class BackupRestoreResult(
    val eventsRestored: Int,
    val settingsRestored: Boolean,
)
