package com.codigitech.belay.ui.witnessdetail

/** Centralized user-facing copy for the Witness detail screen (see CLAUDE.md conventions). */
object WitnessDetailCopy {
  const val WATCHING_LABEL = "You're watching"
  const val NOT_YET_TIME = "not yet"
  const val CHEER_LABEL = "Cheer"
  const val NUDGE_LABEL = "Nudge"
  const val NUDGE_HINT = "One nudge a day. Use it well."
  const val PROGRESS_LABEL = "Challenge progress"
  const val LOG_LABEL = "Log"
  const val FOOTNOTE = "No streak here, no score, nothing to lose. Their habits are the only thing on this screen."
  const val PERFECT_DAY = "Perfect day"
  const val PARTIAL_DAY = "Partial day"
  const val NOTHING_LOGGED = "Nothing logged"

  fun subtitle(challengerName: String, challengeTitle: String): String = "$challengerName · $challengeTitle"

  fun dayCount(dayNo: Int, totalDays: Int): String = "Day $dayNo of $totalDays"

  fun headline(challengerName: String, doneCount: Int, habitCount: Int): String =
    when {
      habitCount > 0 && doneCount == habitCount -> "$challengerName finished the day."
      doneCount > 0 -> "$challengerName is $doneCount of $habitCount."
      else -> "Nothing yet."
    }

  fun progressDetail(perfectDays: Int, checkInsTotal: Int, graceDaysLeft: Int): String =
    "$perfectDays perfect days · $checkInsTotal check-ins · $graceDaysLeft grace day${if (graceDaysLeft == 1) "" else "s"} left"

  fun logDetail(doneCount: Int, habitCount: Int): String =
    when {
      habitCount == 0 || doneCount == 0 -> NOTHING_LOGGED
      doneCount == habitCount -> PERFECT_DAY
      else -> PARTIAL_DAY
    }

  // PRD §6.7 — a witness looking at a challenge that has already run its course.
  const val ENDED_NOTE = "This challenge has finished. Nothing left to check off."

  /** PRD §7 accessibility — same reason as WatchingCopy.habitStatusDescription. */
  fun habitStatusDescription(name: String, time: String, checkedToday: Boolean): String =
    if (checkedToday) "$name, checked off at $time" else "$name, not checked off yet"
}
