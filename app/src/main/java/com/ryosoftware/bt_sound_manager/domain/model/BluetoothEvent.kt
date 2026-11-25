package com.ryosoftware.bt_sound_manager.domain.model

enum class BluetoothEventType(val code: Int) {
    BLUETOOTH_ON(1),
    BLUETOOTH_OFF(2),
    DEVICE_CONNECTED(3),
    DEVICE_DISCONNECTED(4),
    AUTOMATION_APPLIED_CONNECTED(5),
    AUTOMATION_APPLIED_NO_DEVICES(6),
    AUTOMATION_APPLIED_BLUETOOTH_OFF(7),
    EXTERNAL_MODE_CHANGED(8),
    AUTOMATION_ATTEMPTING_CONNECTED(9),
    AUTOMATION_ATTEMPTING_NO_DEVICES(10),
    AUTOMATION_ATTEMPTING_BLUETOOTH_OFF(11),
    AUTOMATION_FAILED_CONNECTED(12),
    AUTOMATION_FAILED_NO_DEVICES(13),
    AUTOMATION_FAILED_BLUETOOTH_OFF(14),
    AUTOMATION_FAILED_PERMISSIONS(15),
    ;

    val isDeviceEvent: Boolean
        get() = this == DEVICE_CONNECTED || this == DEVICE_DISCONNECTED

    val isAutomationApplied: Boolean
        get() = this == AUTOMATION_APPLIED_CONNECTED ||
            this == AUTOMATION_APPLIED_NO_DEVICES ||
            this == AUTOMATION_APPLIED_BLUETOOTH_OFF

    val isAutomationAttempting: Boolean
        get() = this == AUTOMATION_ATTEMPTING_CONNECTED ||
            this == AUTOMATION_ATTEMPTING_NO_DEVICES ||
            this == AUTOMATION_ATTEMPTING_BLUETOOTH_OFF

    val isAutomationFailure: Boolean
        get() = this == AUTOMATION_FAILED_CONNECTED ||
            this == AUTOMATION_FAILED_NO_DEVICES ||
            this == AUTOMATION_FAILED_BLUETOOTH_OFF ||
            this == AUTOMATION_FAILED_PERMISSIONS

    val isAutomationEvent: Boolean
        get() = isAutomationApplied || isAutomationAttempting || isAutomationFailure

    companion object {
        fun fromCode(code: Int): BluetoothEventType? = entries.firstOrNull { it.code == code }
    }
}

data class BluetoothEvent(
    val id: Long = 0,
    val timeUtc: Long,
    val type: BluetoothEventType,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val mode: InterruptMode? = null,
)
