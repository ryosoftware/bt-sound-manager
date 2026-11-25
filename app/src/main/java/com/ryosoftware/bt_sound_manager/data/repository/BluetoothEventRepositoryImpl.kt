package com.ryosoftware.bt_sound_manager.data.repository

import androidx.room.withTransaction
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.data.local.db.AppDatabase
import com.ryosoftware.bt_sound_manager.data.local.db.BluetoothEventDao
import com.ryosoftware.bt_sound_manager.data.local.db.BluetoothEventEntity
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.EventRetention
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BluetoothEventRepositoryImpl @Inject constructor(
    private val dao: BluetoothEventDao,
    private val database: AppDatabase,
    private val clock: AppClock,
    private val settingsRepository: SettingsRepository,
) : BluetoothEventRepository {

    override fun observeEvents(): Flow<List<BluetoothEvent>> =
        dao.observeAll().map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    override suspend fun addEvent(event: BluetoothEvent): Long {
        pruneExpired()
        return dao.insert(event.toEntity())
    }

    override suspend fun replaceEvent(removedId: Long, event: BluetoothEvent): Long {
        pruneExpired()
        return database.withTransaction {
            if (removedId > 0) {
                dao.deleteByIds(setOf(removedId))
            }
            dao.insert(event.toEntity())
        }
    }

    override suspend fun getLatestEvent(): BluetoothEvent? =
        dao.getLatest()?.toDomain()

    override suspend fun getAllEvents(): List<BluetoothEvent> =
        dao.getAll().mapNotNull { it.toDomain() }

    override suspend fun replaceAllEvents(events: List<BluetoothEvent>) {
        database.withTransaction {
            dao.deleteAll()
            if (events.isNotEmpty()) {
                dao.insertAll(events.map { it.toEntity() })
            }
        }
    }

    override suspend fun pruneExpired() {
        val days = settingsRepository.snapshot().events.retentionDays
        dao.deleteBefore(
            EventRetention.cutoffUtcMillis(
                nowMillis = clock.nowMillis(),
                zone = clock.zoneId(),
                days = days,
            ),
        )
    }

    override suspend fun deleteEvents(ids: Set<Long>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    override suspend fun deleteRange(startUtc: Long, endUtc: Long) {
        dao.deleteRange(startUtc, endUtc)
    }
}

private fun BluetoothEventEntity.toDomain(): BluetoothEvent? {
    val type = BluetoothEventType.fromCode(eventType) ?: return null
    return BluetoothEvent(
        id = id,
        timeUtc = timeUtc,
        type = type,
        deviceAddress = deviceAddress,
        deviceName = deviceName,
        mode = InterruptMode.fromStorage(mode),
    )
}

private fun BluetoothEvent.toEntity(): BluetoothEventEntity = BluetoothEventEntity(
    timeUtc = timeUtc,
    eventType = type.code,
    deviceAddress = deviceAddress,
    deviceName = deviceName,
    mode = mode?.storageValue,
)
