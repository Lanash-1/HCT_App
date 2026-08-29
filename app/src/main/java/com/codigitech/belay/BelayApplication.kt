package com.codigitech.belay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.codigitech.belay.data.notification.HabitReminderReceiver
import com.codigitech.belay.data.notification.ReminderNotificationCopy
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BelayApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    val channel =
      NotificationChannel(HabitReminderReceiver.CHANNEL_ID, ReminderNotificationCopy.CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = ReminderNotificationCopy.CHANNEL_DESCRIPTION
      }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
}
