package com.ryosoftware.bt_sound_manager.data.backup

import android.content.Context
import android.net.Uri
import com.ryosoftware.bt_sound_manager.BuildConfig
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.domain.model.BackupContents
import com.ryosoftware.bt_sound_manager.domain.model.BackupRestoreResult
import com.ryosoftware.bt_sound_manager.domain.model.BackupSelection
import com.ryosoftware.bt_sound_manager.domain.model.BackupSummary
import com.ryosoftware.bt_sound_manager.domain.repository.BackupRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventRepository: BluetoothEventRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
    private val codec: BackupJsonCodec,
) : BackupRepository {

    override fun suggestedFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
            .withZone(clock.zoneId())
        return "bt-sound-manager-${formatter.format(Instant.ofEpochMilli(clock.nowMillis()))}.json"
    }

    override suspend fun export(uri: String): BackupSummary = withContext(Dispatchers.IO) {
        val contents = BackupContents(
            createdAtUtcMillis = clock.nowMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            events = eventRepository.getAllEvents(),
            settings = settingsRepository.snapshot(),
        )
        writeText(uri, codec.encode(contents))
        BackupSummary.from(contents)
    }

    override suspend fun read(uri: String): BackupContents = withContext(Dispatchers.IO) {
        codec.decode(readText(uri))
    }

    override suspend fun restore(
        contents: BackupContents,
        selection: BackupSelection,
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        if (selection.settings) {
            settingsRepository.replaceAll(contents.settings)
        }
        if (selection.events) {
            eventRepository.replaceAllEvents(contents.events)
        }
        BackupRestoreResult(
            eventsRestored = if (selection.events) contents.events.size else 0,
            settingsRestored = selection.settings,
        )
    }

    private fun writeText(uri: String, text: String) {
        val parsedUri = Uri.parse(uri)
        val stream = runCatching {
            context.contentResolver.openOutputStream(parsedUri, "wt")
        }.getOrNull()
            ?: context.contentResolver.openOutputStream(parsedUri)
            ?: throw IOException("Cannot open the backup file for writing")
        stream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(text) }
    }

    private fun readText(uri: String): String {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Cannot open the backup file")
        return stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    }
}
