package com.codigitech.belay.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

class AlarmManagerReminderScheduler
@Inject
constructor(@param:ApplicationContext private val context: Context, private val alarmManager: AlarmManager) : ReminderScheduler {

  override fun scheduleHabitReminder(habitId: String, habitName: String, time: String) {
    val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return
    val triggerAtMillis = nextOccurrenceMillis(localTime)
    val pendingIntent = pendingIntentFor(habitId, habitName, time)
    // On API 31+ exact alarms need SCHEDULE_EXACT_ALARM to have actually been granted (users can
    // revoke it after install) — fall back to an inexact-but-still-doze-bypassing alarm rather
    // than crash with a SecurityException if it hasn't been.
    if (canScheduleExact()) {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
  }

  private fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

  override fun cancelHabitReminder(habitId: String) {
    alarmManager.cancel(pendingIntentFor(habitId, habitName = "", time = ""))
  }

  private fun pendingIntentFor(habitId: String, habitName: String, time: String): PendingIntent {
    val intent =
      Intent(context, HabitReminderReceiver::class.java).apply {
        putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habitId)
        putExtra(HabitReminderReceiver.EXTRA_HABIT_NAME, habitName)
        putExtra(HabitReminderReceiver.EXTRA_TIME, time)
      }
    return PendingIntent.getBroadcast(context, habitId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  /** The next wall-clock moment [localTime] occurs — today if still ahead, otherwise tomorrow. */
  private fun nextOccurrenceMillis(localTime: LocalTime): Long {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    var candidate = now.toLocalDate().atTime(localTime).atZone(ZoneId.systemDefault())
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    return candidate.toInstant().toEpochMilli()
  }
}
