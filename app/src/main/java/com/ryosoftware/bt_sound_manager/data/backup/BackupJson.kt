package com.ryosoftware.bt_sound_manager.data.backup

import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackgroundSettings
import com.ryosoftware.bt_sound_manager.domain.model.BackupContents
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.EventsSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.model.ThemeMode
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val BACKUP_FORMAT = "bt-sound-manager-backup"
internal const val BACKUP_SCHEMA_VERSION = 1

class BackupFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

@Singleton
class BackupJsonCodec @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(contents: BackupContents): String =
        json.encodeToString(contents.toDto()) + "\n"

    fun decode(text: String): BackupContents {
        val dto = try {
            json.decodeFromString<BackupFileDto>(text)
        } catch (error: SerializationException) {
            throw BackupFormatException("Invalid backup JSON", error)
        } catch (error: IllegalArgumentException) {
            throw BackupFormatException("Invalid backup JSON", error)
        }
        if (dto.format != BACKUP_FORMAT) {
            throw BackupFormatException("Unsupported backup format: ${dto.format}")
        }
        if (dto.schemaVersion != BACKUP_SCHEMA_VERSION) {
            throw BackupFormatException("Unsupported backup schema: ${dto.schemaVersion}")
        }
        return BackupContents(
            createdAtUtcMillis = parseInstant(dto.createdAt, "createdAt"),
            appVersion = dto.appVersion,
            events = dto.events.map { it.toDomain() },
            settings = dto.settings.toDomain(),
        )
    }

    private fun BackupContents.toDto(): BackupFileDto = BackupFileDto(
        format = BACKUP_FORMAT,
        schemaVersion = BACKUP_SCHEMA_VERSION,
        appVersion = appVersion,
        createdAt = Instant.ofEpochMilli(createdAtUtcMillis).toString(),
        events = events.sortedBy { it.timeUtc }.map { it.toDto() },
        settings = settings.toDto(),
    )

    private fun BluetoothEvent.toDto(): EventDto = EventDto(
        time = Instant.ofEpochMilli(timeUtc).toString(),
        type = type.toBackupValue(),
        deviceAddress = normalizeAddress(deviceAddress),
        deviceName = deviceName?.trim()?.takeIf { it.isNotEmpty() },
        mode = mode?.storageValue,
    )

    private fun EventDto.toDomain(): BluetoothEvent {
        val parsedType = parseEventType(type)
        val address = normalizeAddress(deviceAddress)
        if (parsedType.isDeviceEvent && address == null) {
            throw BackupFormatException("Device event without address")
        }
        return BluetoothEvent(
            timeUtc = parseInstant(time, "event time"),
            type = parsedType,
            deviceAddress = address,
            deviceName = deviceName?.trim()?.takeIf { it.isNotEmpty() },
            mode = parseEventMode(mode),
        )
    }

    private fun AppSettings.toDto(): SettingsDto = SettingsDto(
        automation = AutomationDto(
            enabled = automation.enabled,
            schedule = ScheduleDto(
                allDay = automation.allDay,
                start = formatMinuteOfDay(automation.startMinuteOfDay),
                end = formatMinuteOfDay(automation.endMinuteOfDay),
            ),
            monitorAllDevices = automation.monitorAllDevices,
            selectedDeviceAddresses = automation.selectedDeviceAddresses
                .mapNotNull(::normalizeAddress)
                .sorted(),
            modeWhenConnected = automation.modeWhenConnected.storageValue,
            modeWhenNone = automation.modeWhenNone.storageValue,
            forceMode = automation.forceMode,
            restoreMediaVolume = automation.restoreMediaVolume,
        ),
        background = BackgroundDto(
            detectBluetoothEvents = background.detectBluetoothEvents,
            reactivateOnUserChange = background.reactivateOnUserChange,
        ),
        appearance = AppearanceDto(
            themeMode = appearance.themeMode.storageValue,
            dynamicColor = appearance.dynamicColor,
        ),
        events = EventsDto(retentionDays = events.retentionDays),
    )

    private fun SettingsDto.toDomain(): AppSettings {
        val connectedMode = parseInterruptMode(automation.modeWhenConnected)
        val noneMode = parseInterruptMode(automation.modeWhenNone)
        val (safeConnectedMode, safeNoneMode) = if (connectedMode == noneMode) {
            InterruptMode.DO_NOT_DISTURB to InterruptMode.NORMAL
        } else {
            connectedMode to noneMode
        }
        return AppSettings(
            automation = AutomationSettings(
                enabled = automation.enabled,
                allDay = automation.schedule.allDay,
                startMinuteOfDay = parseMinuteOfDay(automation.schedule.start),
                endMinuteOfDay = parseMinuteOfDay(automation.schedule.end),
                monitorAllDevices = automation.monitorAllDevices,
                selectedDeviceAddresses = automation.selectedDeviceAddresses
                    .mapNotNull(::normalizeAddress)
                    .toSet(),
                modeWhenConnected = safeConnectedMode,
                modeWhenNone = safeNoneMode,
                forceMode = automation.forceMode,
                restoreMediaVolume = automation.restoreMediaVolume,
            ),
            background = BackgroundSettings(
                detectBluetoothEvents = background.detectBluetoothEvents ||
                    background.reactivateOnUserChange,
                reactivateOnUserChange = background.reactivateOnUserChange,
            ),
            appearance = AppearanceSettings(
                themeMode = parseThemeMode(appearance.themeMode),
                dynamicColor = appearance.dynamicColor,
            ),
            events = EventsSettings(
                retentionDays = events.retentionDays.coerceIn(
                    EventsSettings.MIN_RETENTION_DAYS,
                    EventsSettings.MAX_RETENTION_DAYS,
                ),
            ),
        )
    }

    private fun parseInstant(value: String, field: String): Long = try {
        Instant.parse(value).toEpochMilli()
    } catch (error: DateTimeParseException) {
        throw BackupFormatException("Invalid $field: $value", error)
    }

    private fun parseMinuteOfDay(value: String): Int {
        val parts = value.split(':')
        if (parts.size != 2) throw BackupFormatException("Invalid time: $value")
        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            throw BackupFormatException("Invalid time: $value")
        }
        return hour * 60 + minute
    }

    private fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safeMinute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        return String.format(Locale.ROOT, "%02d:%02d", safeMinute / 60, safeMinute % 60)
    }

    private fun parseInterruptMode(value: String): InterruptMode =
        InterruptMode.entries.firstOrNull { it.storageValue == value }
            ?: throw BackupFormatException("Invalid interrupt mode: $value")

    private fun parseThemeMode(value: String): ThemeMode =
        ThemeMode.entries.firstOrNull { it.storageValue == value }
            ?: throw BackupFormatException("Invalid theme mode: $value")

    private fun parseEventType(value: String): BluetoothEventType = when (value) {
        "bluetooth_on" -> BluetoothEventType.BLUETOOTH_ON
        "bluetooth_off" -> BluetoothEventType.BLUETOOTH_OFF
        "device_connected" -> BluetoothEventType.DEVICE_CONNECTED
        "device_disconnected" -> BluetoothEventType.DEVICE_DISCONNECTED
        "automation_applied_connected" -> BluetoothEventType.AUTOMATION_APPLIED_CONNECTED
        "automation_applied_no_devices" -> BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES
        "automation_applied_bluetooth_off" ->
            BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF

        else -> throw BackupFormatException("Invalid event type: $value")
    }

    private fun parseEventMode(value: String?): InterruptMode? {
        if (value == null) return null
        return InterruptMode.fromStorage(value)
            ?: throw BackupFormatException("Invalid event mode: $value")
    }

    private fun BluetoothEventType.toBackupValue(): String = when (this) {
        BluetoothEventType.BLUETOOTH_ON -> "bluetooth_on"
        BluetoothEventType.BLUETOOTH_OFF -> "bluetooth_off"
        BluetoothEventType.DEVICE_CONNECTED -> "device_connected"
        BluetoothEventType.DEVICE_DISCONNECTED -> "device_disconnected"
        BluetoothEventType.AUTOMATION_APPLIED_CONNECTED -> "automation_applied_connected"
        BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES -> "automation_applied_no_devices"
        BluetoothEventType.AUTOMATION_APPLIED_BLUETOOTH_OFF ->
            "automation_applied_bluetooth_off"
    }
}

@Serializable
internal data class BackupFileDto(
    val format: String,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: String,
    val events: List<EventDto> = emptyList(),
    val settings: SettingsDto,
)

@Serializable
internal data class EventDto(
    val time: String,
    val type: String,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val mode: String? = null,
)

@Serializable
internal data class SettingsDto(
    val automation: AutomationDto,
    val background: BackgroundDto,
    val appearance: AppearanceDto,
    val events: EventsDto,
)

@Serializable
internal data class AutomationDto(
    val enabled: Boolean,
    val schedule: ScheduleDto,
    val monitorAllDevices: Boolean,
    val selectedDeviceAddresses: List<String> = emptyList(),
    val modeWhenConnected: String,
    val modeWhenNone: String,
    val forceMode: Boolean,
    val restoreMediaVolume: Boolean,
)

@Serializable
internal data class ScheduleDto(
    val allDay: Boolean,
    val start: String,
    val end: String,
)

@Serializable
internal data class BackgroundDto(
    val detectBluetoothEvents: Boolean,
    val reactivateOnUserChange: Boolean,
)

@Serializable
internal data class AppearanceDto(
    val themeMode: String,
    val dynamicColor: Boolean,
)

@Serializable
internal data class EventsDto(
    val retentionDays: Int,
)
