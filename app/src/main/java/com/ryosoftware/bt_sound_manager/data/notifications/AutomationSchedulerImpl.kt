package com.ryosoftware.bt_sound_manager.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.core.util.AppActions
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationScheduler
import com.ryosoftware.bt_sound_manager.domain.usecase.ScheduleWindow
import com.ryosoftware.bt_sound_manager.receiver.AutomationAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
) : AutomationScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    override fun scheduleNextTransition(settings: AutomationSettings) {
        if (!settings.enabled || settings.allDay) {
            cancel()
            return
        }
        val nextTransition = ScheduleWindow.nextBoundaryMillis(
            nowMillis = clock.nowMillis(),
            settings = settings,
            zone = clock.zoneId(),
        ) ?: return
        val manager = alarmManager ?: return
        val pendingIntent = alarmPendingIntent()
        try {
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTransition,
                    pendingIntent,
                )
            } else {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTransition,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            manager.set(AlarmManager.RTC_WAKEUP, nextTransition, pendingIntent)
        }
    }

    override fun cancel() {
        alarmManager?.cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_AUTOMATION_ALARM,
        Intent(context, AutomationAlarmReceiver::class.java)
            .setAction(AppActions.ACTION_AUTOMATION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_AUTOMATION_ALARM = 301
    }
}
