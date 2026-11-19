package com.ryosoftware.bt_sound_manager.presentation.components

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.util.labelRes
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatEventTime(context: Context, utcMillis: Long): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hms" else "hms"
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(utcMillis))
}

fun formatEventDate(context: Context, date: LocalDate): String =
    runCatching {
        date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault()),
        )
    }.getOrDefault(date.toString())

fun formatDayLabel(context: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> formatEventDate(context, date)
    }
}

fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60).coerceIn(0, 23))
        set(Calendar.MINUTE, (minuteOfDay % 60).coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

fun modeLabel(context: Context, mode: InterruptMode): String =
    context.getString(mode.labelRes())

@StringRes
fun eventTypeLabelRes(type: BluetoothEventType): Int = when (type) {
    BluetoothEventType.BLUETOOTH_ON -> R.string.event_bluetooth_on
    BluetoothEventType.BLUETOOTH_OFF -> R.string.event_bluetooth_off
    BluetoothEventType.DEVICE_CONNECTED -> R.string.event_device_connected
    BluetoothEventType.DEVICE_DISCONNECTED -> R.string.event_device_disconnected
}

fun isScheduleCrossingMidnight(startMinuteOfDay: Int, endMinuteOfDay: Int): Boolean =
    endMinuteOfDay < startMinuteOfDay
