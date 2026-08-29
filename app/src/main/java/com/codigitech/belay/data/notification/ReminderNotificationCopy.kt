package com.codigitech.belay.data.notification

/** Centralized copy for habit-reminder notifications (PRD §6.1, §7 localization note). */
object ReminderNotificationCopy {
  const val CHANNEL_NAME = "Habit reminders"
  const val CHANNEL_DESCRIPTION = "Reminds you to check off a habit at the time you picked for it"

  fun title(habitName: String): String = "Time for “$habitName”"

  const val TEXT = "Open Belay to check it off."
}
