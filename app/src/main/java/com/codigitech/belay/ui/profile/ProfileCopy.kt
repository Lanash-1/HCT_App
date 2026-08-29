package com.codigitech.belay.ui.profile

/** Centralized user-facing copy for the Profile screen (see CLAUDE.md conventions). */
object ProfileCopy {
  const val MODE_LABEL = "Mode"
  const val APPEARANCE_LABEL = "Appearance"
  const val PEOPLE_LABEL = "People"
  const val SETTINGS_LABEL = "Settings"
  const val HABITS_STAT_LABEL = "habits"
  const val BEST_STREAK_STAT_LABEL = "best streak"
  const val PEOPLE_WATCHED_STAT_LABEL = "you watch"
  const val CHALLENGER_LABEL = "Challenger"
  const val WITNESS_LABEL = "Witness"
  const val LIGHT_LABEL = "Light"
  const val DARK_LABEL = "Dark"
  const val SYSTEM_LABEL = "System"
  const val DAILY_REMINDER_LABEL = "Daily reminder"
  const val GRACE_DAYS_LEFT_LABEL = "Grace days left"
  const val NO_REMINDER_SET = "Not set"
  const val NO_ACTIVE_CHALLENGE = "—"
  const val SWITCH_NOTE = "Switching modes never deletes a challenge — it just changes what this phone shows."
  const val CHALLENGER_MODE_EXPLAINER =
    "Challenger mode shows your own stack of habits and hides the people you watch. Your witness still sees everything you log."
  const val WITNESS_MODE_EXPLAINER =
    "Witness mode hides your own challenge. You see the people you watch, and you can cheer or nudge — but you can't check anything off for them."

  fun pairCodeLine(pairCode: String, joinedLabel: String): String = "Pair code $pairCode · $joinedLabel"

  fun joinedLabel(month: String): String = "joined $month"

  fun witnessSubtitle(habitCount: Int): String = "Your witness · sees all $habitCount habit${if (habitCount == 1) "" else "s"}"

  fun watchingSubtitle(challengeTitle: String): String = "You're watching · $challengeTitle"

  fun nudgeToggleLabel(witnessName: String): String = "Let ${witnessName.ifBlank { "them" }} nudge me"
}
