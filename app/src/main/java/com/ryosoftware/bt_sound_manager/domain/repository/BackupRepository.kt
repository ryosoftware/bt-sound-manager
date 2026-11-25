package com.ryosoftware.bt_sound_manager.domain.repository

import com.ryosoftware.bt_sound_manager.domain.model.BackupContents
import com.ryosoftware.bt_sound_manager.domain.model.BackupRestoreResult
import com.ryosoftware.bt_sound_manager.domain.model.BackupSelection
import com.ryosoftware.bt_sound_manager.domain.model.BackupSummary

interface BackupRepository {
    fun suggestedFileName(): String

    suspend fun export(uri: String): BackupSummary

    suspend fun read(uri: String): BackupContents

    suspend fun restore(
        contents: BackupContents,
        selection: BackupSelection,
    ): BackupRestoreResult
}
