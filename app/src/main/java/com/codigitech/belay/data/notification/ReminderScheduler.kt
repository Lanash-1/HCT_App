package com.codigitech.belay.data.notification

/** Local, per-habit reminder scheduling (PRD §6.1) — not server-pushed, so this never touches Firestore/FCM. */
interface ReminderScheduler {
  /** Schedules a daily reminder for [habitId] at [time] (HH:mm, device-local). Replaces any existing schedule for the same habit. */
  fun scheduleHabitReminder(habitId: String, habitName: String, time: String)

  fun cancelHabitReminder(habitId: String)
}
