package com.ryosoftware.bt_sound_manager.data.notifications

import android.app.NotificationManager
import android.content.Context
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidInterruptModeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : InterruptModeRepository {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    override fun canAccessPolicy(): Boolean = try {
        manager?.isNotificationPolicyAccessGranted == true
    } catch (_: SecurityException) {
        false
    }

    override fun currentMode(): InterruptMode? {
        if (!canAccessPolicy()) return null
        return try {
            when (manager?.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> InterruptMode.NORMAL
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> InterruptMode.DO_NOT_DISTURB
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> InterruptMode.ALARMS_ONLY
                NotificationManager.INTERRUPTION_FILTER_NONE -> InterruptMode.SILENT_ALL
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    override fun setMode(mode: InterruptMode): Boolean {
        if (!canAccessPolicy()) return false
        val filter = when (mode) {
            InterruptMode.NORMAL -> NotificationManager.INTERRUPTION_FILTER_ALL
            InterruptMode.DO_NOT_DISTURB -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            InterruptMode.ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            InterruptMode.SILENT_ALL -> NotificationManager.INTERRUPTION_FILTER_NONE
        }
        return try {
            manager?.setInterruptionFilter(filter)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
