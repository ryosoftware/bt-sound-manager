package com.ryosoftware.bt_sound_manager.domain.model

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

data class AutomationSettings(
    val enabled: Boolean = false,
    val allDay: Boolean = true,
    val startMinuteOfDay: Int = DEFAULT_START_MINUTE,
    val endMinuteOfDay: Int = DEFAULT_END_MINUTE,
    val monitorAllDevices: Boolean = false,
    val selectedDeviceAddresses: Set<String> = emptySet(),
    val modeWhenConnected: InterruptMode = InterruptMode.DO_NOT_DISTURB,
    val modeWhenNone: InterruptMode = InterruptMode.NORMAL,
    val forceModeOnDevicesConnected: Boolean = false,
    val forceModeOnDevicesDisconnected: Boolean = false,
    val restoreMediaVolume: Boolean = false,
) {
    val hasDeviceConfiguration: Boolean
        get() = monitorAllDevices || selectedDeviceAddresses.isNotEmpty()

    /**
     * With an app-owned Do Not Disturb rule, forcing the normal mode cannot do
     * anything: the app removes its own restriction anyway and cannot remove others.
     * The saved values are kept in case the modes change.
     */
    fun withoutForcedNormalMode(): AutomationSettings = copy(
        forceModeOnDevicesConnected = forceModeOnDevicesConnected &&
            modeWhenConnected != InterruptMode.NORMAL,
        forceModeOnDevicesDisconnected = forceModeOnDevicesDisconnected &&
            modeWhenNone != InterruptMode.NORMAL,
    )

    companion object {
        const val DEFAULT_START_MINUTE = 6 * 60
        const val DEFAULT_END_MINUTE = 22 * 60
    }
}

data class BackgroundSettings(
    val detectBluetoothEvents: Boolean = false,
    val reactivateOnUserChange: Boolean = false,
) {
    val anyEnabled: Boolean
        get() = detectBluetoothEvents
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

data class EventsSettings(
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        const val MIN_RETENTION_DAYS = 3
        const val MAX_RETENTION_DAYS = 90
        const val DEFAULT_RETENTION_DAYS = 7
    }
}

data class AppSettings(
    val automation: AutomationSettings = AutomationSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val events: EventsSettings = EventsSettings(),
)

data class AutomationRuntimeState(
    val lastAppliedMode: InterruptMode? = null,
    val lastExecutionElapsedTics: Long = 0L,
    val previousMediaVolume: Int? = null,
    /**
     * Whether [lastAppliedMode] is still the mode that the app itself set. Without it
     * the app does not revert the connected mode unless that direction is forced.
     */
    val ownsMode: Boolean = false,
    /** Mode requested by the app whose confirmation is still missing. */
    val pendingMode: InterruptMode? = null,
)
