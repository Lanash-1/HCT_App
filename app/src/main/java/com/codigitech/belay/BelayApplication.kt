package com.codigitech.belay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.codigitech.belay.data.notification.HabitReminderReceiver
import com.codigitech.belay.data.notification.PushChannels
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
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)

    // One channel per push event type (docs/TECH_STACK.md §4). Created up front rather than on
    // first push: Android drops a notification whose channel doesn't exist yet, so the very first
    // cheer someone receives would otherwise never appear.
    PushChannels.all.forEach { pushChannel ->
      notificationManager.createNotificationChannel(
        NotificationChannel(pushChannel.id, pushChannel.displayName, NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = pushChannel.description
        }
      )
    }
  }
}
