package com.codigitech.belay.ui.createchallenge

/** Centralized user-facing copy for the create-challenge screen (see CLAUDE.md conventions). */
object CreateChallengeCopy {
  const val TITLE_LABEL = "Challenge name"
  const val TITLE_HINT = "e.g. Morning reset"
  const val HABITS_SECTION = "The habits"
  const val HABITS_HELPER = "Be specific enough that your witness could tell whether you did it."
  const val HABIT_NAME_HINT = "Habit"
  const val HABIT_DETAIL_HINT = "Detail (optional)"
  const val ADD_HABIT = "+  Add a habit"
  const val SET_REMINDER = "Set a reminder"
  const val CLEAR_REMINDER = "Clear"
  const val NOTIFICATION_RATIONALE_TITLE = "Stay in the loop"
  const val NOTIFICATION_RATIONALE_BODY =
    "Your witness can only cheer or nudge you if you allow notifications — and habit reminders need them too."
  const val NOTIFICATION_RATIONALE_CONFIRM = "Turn on notifications"
  const val NOTIFICATION_RATIONALE_DISMISS = "Not now"
  const val DURATION_SECTION = "How long"
  const val WITNESS_SECTION = "Who's watching"
  const val WITNESS_EMPTY = "No one paired yet — pair with a witness from onboarding first."
  const val GRACE_SECTION = "Grace days"
  const val GRACE_TITLE = "Imperfect days allowed"
  const val GRACE_DETAIL = "Decide now, not at 11 pm on day 14."
  const val SAVE = "Save"

  fun habitCount(count: Int): String = "$count of 5"

  fun durationLabel(days: Int): String = "$days days"

  /** Renders a stored "HH:mm" as e.g. "6:42 am", or the set-a-reminder prompt when there isn't one yet. */
  fun reminderLabel(time: String?): String {
    if (time == null) return SET_REMINDER
    val (hour24, minute) = time.split(":").let { it[0].toInt() to it[1].toInt() }
    val period = if (hour24 < 12) "am" else "pm"
    val hour12 = when (val h = hour24 % 12) {
      0 -> 12
      else -> h
    }
    return "%d:%02d %s".format(hour12, minute, period)
  }
}
