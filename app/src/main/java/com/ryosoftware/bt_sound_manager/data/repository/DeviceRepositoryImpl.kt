package com.ryosoftware.bt_sound_manager.data.repository

import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.data.local.db.DeviceNameDao
import com.ryosoftware.bt_sound_manager.data.local.db.DeviceNameEntity
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val dao: DeviceNameDao,
) : DeviceRepository {

    override fun observeDeviceNames(): Flow<Map<String, String>> =
        dao.observeAll().map { entities ->
            entities.associate { it.address to it.name }
        }

    override suspend fun getDeviceName(address: String): String? {
        val normalized = normalizeAddress(address) ?: return null
        return dao.findName(normalized)
    }

    override suspend fun saveDeviceName(address: String, name: String?) {
        val normalized = normalizeAddress(address) ?: return
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanName == null) {
            dao.delete(normalized)
        } else {
            dao.upsert(DeviceNameEntity(address = normalized, name = cleanName))
        }
    }
}
