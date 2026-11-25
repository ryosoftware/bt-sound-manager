package com.ryosoftware.bt_sound_manager.data.backup

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonCodecTest {

    private val codec = BackupJsonCodec()

    @Test
    fun roundTripKeepsEventsAndSettings() {
        val contents = backupContents()
        val encoded = codec.encode(contents)
        val decoded = codec.decode(encoded)

        assertEquals(contents.createdAtUtcMillis, decoded.createdAtUtcMillis)
        assertEquals(contents.appVersion, decoded.appVersion)
        assertEquals(
            contents.events.sortedBy { it.timeUtc }.map { it.copy(id = 0) },
            decoded.events,
        )
        assertEquals(contents.settings, decoded.settings)
        assertTrue(encoded.contains("\"type\": \"device_connected\""))
        assertTrue(encoded.contains("\"start\": \"06:00\""))
        assertTrue(encoded.contains("\"AA:BB:CC:DD:EE:FF\""))
        assertTrue(decoded.events.all { it.mode == null })
    }

    @Test
    fun roundTripKeepsAutomationEventMode() {
        val event = BluetoothEvent(
            timeUtc = Instant.parse("2026-11-19T18:40:03Z").toEpochMilli(),
            type = BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
            deviceName = "WH-1000XM5",
            mode = InterruptMode.DO_NOT_DISTURB,
        )
        val contents = backupContents().copy(events = listOf(event))

        val encoded = codec.encode(contents)
        val decoded = codec.decode(encoded)

        assertEquals(event, decoded.events.single())
        assertTrue(encoded.contains("\"type\": \"automation_applied_connected\""))
        assertTrue(encoded.contains("\"mode\": \"do-not-disturb\""))
    }

    @Test
    fun roundTripKeepsExternalModeChange() {
        val event = BluetoothEvent(
            timeUtc = Instant.parse("2026-11-19T18:40:03Z").toEpochMilli(),
            type = BluetoothEventType.EXTERNAL_MODE_CHANGED,
            mode = InterruptMode.ALARMS_ONLY,
        )
        val contents = backupContents().copy(events = listOf(event))

        val encoded = codec.encode(contents)
        val decoded = codec.decode(encoded)

        assertEquals(event, decoded.events.single())
        assertTrue(encoded.contains("\"type\": \"external_mode_changed\""))
        assertTrue(encoded.contains("\"mode\": \"alarms-only\""))
    }

    @Test
    fun rejectsInvalidEventModes() {
        val event = BluetoothEvent(
            timeUtc = Instant.parse("2026-11-19T18:40:03Z").toEpochMilli(),
            type = BluetoothEventType.AUTOMATION_APPLIED_NO_DEVICES,
            mode = InterruptMode.NORMAL,
        )
        val encoded = codec.encode(backupContents().copy(events = listOf(event)))
            .replace("\"mode\": \"normal\"", "\"mode\": \"teleported\"")

        assertThrows(BackupFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun doesNotExportRoomIds() {
        val encoded = codec.encode(backupContents())
        assertFalse(Regex("\"id\"\\s*:").containsMatchIn(encoded))
    }

    @Test
    fun acceptsAnEmptyEventList() {
        val contents = backupContents().copy(events = emptyList())
        val decoded = codec.decode(codec.encode(contents))
        assertTrue(decoded.events.isEmpty())
    }

    @Test
    fun repairsSettingsThatCannotBeApplied() {
        val contents = backupContents().copy(
            settings = backupContents().settings.copy(
                automation = backupContents().settings.automation.copy(
                    modeWhenConnected = InterruptMode.SILENT_ALL,
                    modeWhenNone = InterruptMode.SILENT_ALL,
                ),
                background = BackgroundSettings(
                    detectBluetoothEvents = false,
                    reactivateOnUserChange = true,
                ),
                events = EventsSettings(retentionDays = 500),
            ),
        )

        val decoded = codec.decode(codec.encode(contents))

        assertEquals(InterruptMode.DO_NOT_DISTURB, decoded.settings.automation.modeWhenConnected)
        assertEquals(InterruptMode.NORMAL, decoded.settings.automation.modeWhenNone)
        assertFalse(decoded.settings.background.detectBluetoothEvents)
        assertEquals(
            EventsSettings.MAX_RETENTION_DAYS,
            decoded.settings.events.retentionDays,
        )
    }

    @Test
    fun decodesLegacyForceModeIntoBothDirections() {
        val encoded = """
            {
              "format": "$BACKUP_FORMAT",
              "schemaVersion": 1,
              "appVersion": "1.0",
              "createdAt": "2026-11-19T18:42:10Z",
              "settings": {
                "automation": {
                  "enabled": true,
                  "schedule": { "allDay": true, "start": "06:00", "end": "22:00" },
                  "monitorAllDevices": true,
                  "selectedDeviceAddresses": [],
                  "modeWhenConnected": "do-not-disturb",
                  "modeWhenNone": "normal",
                  "forceMode": true,
                  "restoreMediaVolume": false
                },
                "background": {
                  "detectBluetoothEvents": false,
                  "reactivateOnUserChange": false
                },
                "appearance": {
                  "themeMode": "system",
                  "dynamicColor": true
                },
                "events": { "retentionDays": 7 }
              }
            }
        """.trimIndent()

        val decoded = codec.decode(encoded)

        assertTrue(decoded.settings.automation.forceModeOnDevicesConnected)
        assertTrue(decoded.settings.automation.forceModeOnDevicesDisconnected)
    }

    @Test
    fun rejectsUnknownSchemaVersion() {
        val encoded = codec.encode(backupContents())
            .replace("\"schemaVersion\": 3", "\"schemaVersion\": 99")

        assertThrows(BackupFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun rejectsUnknownFormat() {
        val encoded = codec.encode(backupContents())
            .replace(BACKUP_FORMAT, "another-format")

        assertThrows(BackupFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun roundTripKeepsAttemptAndFailureEvents() {
        val events = listOf(
            BluetoothEvent(
                timeUtc = Instant.parse("2026-11-19T18:40:03Z").toEpochMilli(),
                type = BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED,
                deviceName = "WH-1000XM5",
                mode = InterruptMode.DO_NOT_DISTURB,
            ),
            BluetoothEvent(
                timeUtc = Instant.parse("2026-11-19T18:40:08Z").toEpochMilli(),
                type = BluetoothEventType.AUTOMATION_FAILED_CONNECTED,
                deviceName = "WH-1000XM5",
                mode = InterruptMode.DO_NOT_DISTURB,
            ),
            BluetoothEvent(
                timeUtc = Instant.parse("2026-11-19T18:40:09Z").toEpochMilli(),
                type = BluetoothEventType.AUTOMATION_FAILED_NO_DEVICES,
                mode = InterruptMode.NORMAL,
            ),
            BluetoothEvent(
                timeUtc = Instant.parse("2026-11-19T18:40:10Z").toEpochMilli(),
                type = BluetoothEventType.AUTOMATION_FAILED_BLUETOOTH_OFF,
                mode = InterruptMode.SILENT_ALL,
            ),
            BluetoothEvent(
                timeUtc = Instant.parse("2026-11-19T18:40:11Z").toEpochMilli(),
                type = BluetoothEventType.AUTOMATION_FAILED_PERMISSIONS,
                mode = InterruptMode.ALARMS_ONLY,
            ),
        )
        val contents = backupContents().copy(events = events)

        val encoded = codec.encode(contents)
        val decoded = codec.decode(encoded)

        assertEquals(events, decoded.events)
        assertTrue(encoded.contains("\"type\": \"automation_attempting_connected\""))
        assertTrue(encoded.contains("\"type\": \"automation_failed_connected\""))
        assertTrue(encoded.contains("\"type\": \"automation_failed_no_devices\""))
        assertTrue(encoded.contains("\"type\": \"automation_failed_bluetooth_off\""))
        assertTrue(encoded.contains("\"type\": \"automation_failed_permissions\""))
    }

    @Test
    fun acceptsPreviousSchemaVersion() {
        val encoded = codec.encode(backupContents())
            .replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")

        val decoded = codec.decode(encoded)

        assertEquals(backupContents().settings, decoded.settings)
    }

    @Test
    fun rejectsUnknownEventTypes() {
        val encoded = codec.encode(backupContents())
            .replace("device_connected", "device_teleported")

        assertThrows(BackupFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun rejectsInvalidTimestamps() {
        val encoded = codec.encode(backupContents())
            .replace("\"createdAt\": \"2026-11-19T18:42:10Z\"", "\"createdAt\": \"yesterday\"")

        assertThrows(BackupFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun rejectsDeviceEventsWithoutAddress() {
        val encoded = codec.encode(backupContents())
            .replace("\"deviceAddress\": \"AA:BB:CC:DD:EE:FF\",", "")

        assertThrows(BackupFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    private fun backupContents(): BackupContents = BackupContents(
        createdAtUtcMillis = Instant.parse("2026-11-19T18:42:10Z").toEpochMilli(),
        appVersion = "1.0",
        events = listOf(
            BluetoothEvent(
                id = 42,
                timeUtc = Instant.parse("2026-11-19T18:40:03Z").toEpochMilli(),
                type = BluetoothEventType.DEVICE_CONNECTED,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "WH-1000XM5",
            ),
            BluetoothEvent(
                id = 43,
                timeUtc = Instant.parse("2026-11-19T18:35:00Z").toEpochMilli(),
                type = BluetoothEventType.BLUETOOTH_ON,
            ),
        ),
        settings = AppSettings(
            automation = AutomationSettings(
                enabled = true,
                allDay = false,
                startMinuteOfDay = 6 * 60,
                endMinuteOfDay = 22 * 60,
                monitorAllDevices = false,
                selectedDeviceAddresses = setOf("AA:BB:CC:DD:EE:FF"),
                modeWhenConnected = InterruptMode.ALARMS_ONLY,
                modeWhenNone = InterruptMode.SILENT_ALL,
                forceModeOnDevicesConnected = true,
                forceModeOnDevicesDisconnected = false,
                restoreMediaVolume = true,
            ),
            background = BackgroundSettings(
                detectBluetoothEvents = true,
                reactivateOnUserChange = false,
            ),
            appearance = AppearanceSettings(
                themeMode = ThemeMode.DARK,
                dynamicColor = false,
            ),
            events = EventsSettings(retentionDays = 30),
        ),
    )
}
