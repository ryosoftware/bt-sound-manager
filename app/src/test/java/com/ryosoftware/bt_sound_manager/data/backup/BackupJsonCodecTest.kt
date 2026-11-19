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
        assertTrue(decoded.settings.background.detectBluetoothEvents)
        assertEquals(
            EventsSettings.MAX_RETENTION_DAYS,
            decoded.settings.events.retentionDays,
        )
    }

    @Test
    fun rejectsUnknownSchemaVersion() {
        val encoded = codec.encode(backupContents())
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")

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
                forceMode = true,
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
