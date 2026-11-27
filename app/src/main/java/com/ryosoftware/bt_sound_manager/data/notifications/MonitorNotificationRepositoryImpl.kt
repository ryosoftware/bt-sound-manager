package com.ryosoftware.bt_sound_manager.data.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ryosoftware.bt_sound_manager.BluetoothEnableProxyActivity
import com.ryosoftware.bt_sound_manager.MainActivity
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.util.AppActions
import com.ryosoftware.bt_sound_manager.core.util.labelRes
import com.ryosoftware.bt_sound_manager.domain.model.AutomationScheduleStatus
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.MonitorNotificationRepository
import com.ryosoftware.bt_sound_manager.receiver.NotificationActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorNotificationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MonitorNotificationRepository {

    private val notificationManager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    override fun ensureChannels() {
        createChannel(
            id = CHANNEL_CONNECTED,
            nameRes = R.string.channel_connected,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            withSound = true,
        )
        createChannel(
            id = CHANNEL_DISCONNECTED,
            nameRes = R.string.channel_disconnected,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            withSound = true,
        )
        createChannel(
            id = CHANNEL_AUTOMATION,
            nameRes = R.string.channel_automation,
            importance = NotificationManager.IMPORTANCE_MIN,
            withSound = false,
        )
        createChannel(
            id = CHANNEL_BACKGROUND,
            nameRes = R.string.channel_background,
            importance = NotificationManager.IMPORTANCE_MIN,
            withSound = false,
        )
    }

    override fun showDeviceConnected(deviceName: String, address: String) {
        val body = context.getString(R.string.notification_connected_body)
        val notification = deviceNotification(
            channel = CHANNEL_CONNECTED,
            title = deviceName,
            body = body,
            requestCode = REQUEST_CONNECTED,
        )
        notify(ID_CONNECTED, notification)
    }

    override fun showDeviceDisconnected(deviceName: String, address: String) {
        val body = context.getString(R.string.notification_disconnected_body)
        val notification = deviceNotification(
            channel = CHANNEL_DISCONNECTED,
            title = deviceName,
            body = body,
            requestCode = REQUEST_DISCONNECTED,
        )
        notify(ID_DISCONNECTED, notification)
    }

    override fun hideDeviceConnected() {
        notificationManager?.cancel(ID_CONNECTED)
    }

    override fun hideDeviceDisconnected() {
        notificationManager?.cancel(ID_DISCONNECTED)
    }

    override fun showBluetoothEnabled() {
        val notification = NotificationCompat.Builder(context, CHANNEL_CONNECTED)
            .setSmallIcon(smallIconForChannel(CHANNEL_CONNECTED))
            .setContentTitle(context.getString(R.string.notification_bluetooth_on_title))
            .setContentText(context.getString(R.string.notification_bluetooth_on_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_bluetooth_on_body)),
            )
            .setContentIntent(
                mainPendingIntent(REQUEST_BLUETOOTH_ON, AppActions.DESTINATION_EVENTS),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        notify(ID_CONNECTED, notification)
    }

    override fun showBluetoothDisabled() {
        val enableIntent = PendingIntent.getActivity(
            context,
            REQUEST_ENABLE_BLUETOOTH,
            Intent(context, BluetoothEnableProxyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            pendingIntentFlags(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DISCONNECTED)
            .setSmallIcon(smallIconForChannel(CHANNEL_DISCONNECTED))
            .setContentTitle(context.getString(R.string.notification_bluetooth_off_title))
            .setContentText(context.getString(R.string.notification_bluetooth_off_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_bluetooth_off_body)),
            )
            .setContentIntent(mainPendingIntent(REQUEST_BLUETOOTH_OFF, null))
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(
                0,
                context.getString(R.string.action_enable_bluetooth),
                enableIntent,
            )
            .build()
        notify(ID_DISCONNECTED, notification)
    }

    override fun hideBluetoothDisabled() {
        notificationManager?.cancel(ID_DISCONNECTED)
    }

    override fun showAutomationNotification(
        mode: InterruptMode?,
        body: String,
        showToggleAutomationAction: Boolean,
        showEnableBluetoothAction: Boolean,
        showPolicySettingsAction: Boolean,
        attempting: Boolean,
        ruleSettingsId: String?,
    ) {
        val title = if (mode == null) {
            context.getString(R.string.automation_title)
        } else if (attempting) {
            context.getString(
                R.string.notification_automation_attempting_title,
                context.getString(mode.labelRes()),
            )
        } else {
            context.getString(
                R.string.notification_automation_title,
                context.getString(mode.labelRes()),
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_AUTOMATION)
            .setSmallIcon(smallIconForChannel(CHANNEL_AUTOMATION))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(mainPendingIntent(REQUEST_AUTOMATION, null))
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (ruleSettingsId != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            val ruleIntent = PendingIntent.getActivity(
                context,
                REQUEST_RULE_SETTINGS,
                Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS)
                    .putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID, ruleSettingsId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                pendingIntentFlags(),
            )
            builder.addAction(
                0,
                context.getString(R.string.action_open_rule_settings),
                ruleIntent,
            )
        }
        if (showToggleAutomationAction) {
            val toggleIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_TOGGLE_AUTOMATION,
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(AppActions.ACTION_TOGGLE_AUTOMATION),
                pendingIntentFlags(),
            )
            builder.addAction(
                0,
                context.getString(R.string.action_disable_automation),
                toggleIntent,
            )
        }
        if (showEnableBluetoothAction) {
            val enableIntent = PendingIntent.getActivity(
                context,
                REQUEST_ENABLE_BLUETOOTH,
                Intent(context, BluetoothEnableProxyActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                pendingIntentFlags(),
            )
            builder.addAction(
                0,
                context.getString(R.string.action_enable_bluetooth),
                enableIntent,
            )
        }
        if (showPolicySettingsAction) {
            val policyIntent = PendingIntent.getActivity(
                context,
                REQUEST_POLICY_SETTINGS,
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                pendingIntentFlags(),
            )
            builder.addAction(
                0,
                context.getString(R.string.allow),
                policyIntent,
            )
        }
        notify(ID_AUTOMATION, builder.build())
    }

    override fun hideAutomationNotification() {
        notificationManager?.cancel(ID_AUTOMATION)
    }

    override fun buildBackgroundNotification(status: AutomationScheduleStatus?): Notification {
        val text = backgroundNotificationText(status)
        return NotificationCompat.Builder(context, CHANNEL_BACKGROUND)
            .setSmallIcon(smallIconForChannel(CHANNEL_BACKGROUND))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainPendingIntent(REQUEST_BACKGROUND, null))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun updateBackgroundNotification(status: AutomationScheduleStatus?) {
        notify(ID_BACKGROUND, buildBackgroundNotification(status))
    }

    private fun backgroundNotificationText(status: AutomationScheduleStatus?): String =
        context.getString(
            when (status) {
                AutomationScheduleStatus.ALL_DAY -> R.string.notification_background_text_all_day
                AutomationScheduleStatus.INSIDE_SCHEDULE ->
                    R.string.notification_background_text_inside_schedule

                AutomationScheduleStatus.OUTSIDE_SCHEDULE ->
                    R.string.notification_background_text_outside_schedule

                null -> R.string.notification_background_text
            },
        )

    private fun createChannel(
        id: String,
        @StringRes nameRes: Int,
        importance: Int,
        withSound: Boolean,
    ) {
        val manager = notificationManager ?: return
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(id, context.getString(nameRes), importance).apply {
            if (withSound) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
            enableVibration(withSound)
            setShowBadge(withSound)
            enableLights(withSound)
            setBypassDnd(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= 29) setAllowBubbles(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun smallIconForChannel(channelId: String): Int = when (channelId) {
        CHANNEL_BACKGROUND -> R.drawable.ic_stat_notification_background_service
        CHANNEL_CONNECTED -> R.drawable.ic_stat_notification_device_connected
        CHANNEL_DISCONNECTED -> R.drawable.ic_stat_notification_device_disconnected
        CHANNEL_AUTOMATION -> R.drawable.ic_stat_notification_automation
        else -> R.drawable.ic_stat_bluetooth
    }

    private fun deviceNotification(
        channel: String,
        title: String,
        body: String,
        requestCode: Int,
    ): Notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(smallIconForChannel(channel))
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(mainPendingIntent(requestCode, AppActions.DESTINATION_EVENTS))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()

    private fun mainPendingIntent(requestCode: Int, destination: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (destination != null) {
                putExtra(AppActions.EXTRA_DESTINATION, destination)
            }
        }
        return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked at any time.
        }
    }

    private companion object {
        const val CHANNEL_CONNECTED = "device-connected-notifications"
        const val CHANNEL_DISCONNECTED = "device-disconnected-notifications"
        const val CHANNEL_AUTOMATION = "automation-notifications"
        const val CHANNEL_BACKGROUND = "background-service-notifications"

        const val ID_BACKGROUND = 101
        const val ID_CONNECTED = 102
        const val ID_DISCONNECTED = 103
        const val ID_AUTOMATION = 104

        const val REQUEST_CONNECTED = 201
        const val REQUEST_DISCONNECTED = 202
        const val REQUEST_AUTOMATION = 203
        const val REQUEST_BACKGROUND = 204
        const val REQUEST_ENABLE_BLUETOOTH = 205
        const val REQUEST_TOGGLE_AUTOMATION = 206
        const val REQUEST_BLUETOOTH_OFF = 207
        const val REQUEST_POLICY_SETTINGS = 208
        const val REQUEST_BLUETOOTH_ON = 209
        const val REQUEST_RULE_SETTINGS = 210
    }
}
