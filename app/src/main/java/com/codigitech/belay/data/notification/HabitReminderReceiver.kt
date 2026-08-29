package com.codigitech.belay.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codigitech.belay.MainActivity
import com.codigitech.belay.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fires a habit's daily reminder notification, then reschedules itself for the same time
 * tomorrow — AlarmManager alarms are one-shot, so a "daily" reminder is really a alarm that
 * re-arms itself on every fire (and again on boot, via [BootCompletedReceiver]).
 */
@AndroidEntryPoint
class HabitReminderReceiver : BroadcastReceiver() {

  @Inject lateinit var reminderScheduler: ReminderScheduler

  override fun onReceive(context: Context, intent: Intent) {
    val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
    val habitName = intent.getStringExtra(EXTRA_HABIT_NAME) ?: return
    val time = intent.getStringExtra(EXTRA_TIME)

    showNotification(context, habitId, habitName)

    // Re-arm for tomorrow at the same time so the reminder keeps recurring.
    time?.let { reminderScheduler.scheduleHabitReminder(habitId, habitName, it) }
  }

  private fun showNotification(context: Context, habitId: String, habitName: String) {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      return
    }
    val openAppIntent =
      android.app.PendingIntent.getActivity(
        context,
        habitId.hashCode(),
        Intent(context, MainActivity::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(ReminderNotificationCopy.title(habitName))
        .setContentText(ReminderNotificationCopy.TEXT)
        .setContentIntent(openAppIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(habitId.hashCode(), notification)
  }

  companion object {
    const val CHANNEL_ID = "habit_reminders"
    const val EXTRA_HABIT_ID = "habit_id"
    const val EXTRA_HABIT_NAME = "habit_name"
    const val EXTRA_TIME = "time"
  }
}
