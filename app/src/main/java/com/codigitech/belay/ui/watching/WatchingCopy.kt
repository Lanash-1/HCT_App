package com.codigitech.belay.ui.watching

/** Centralized user-facing copy for the Watching screen (see CLAUDE.md conventions). */
object WatchingCopy {
  const val TITLE = "Witness mode"
  const val NOT_YET_TIME = "—"
  const val EMPTY_TITLE = "Not watching anyone yet"
  const val EMPTY_DETAIL = "Pair with a challenger to see their day here."
  const val FOOTNOTE = "No streak here, no score, nothing to lose. Witness mode only ever shows other people's days."
  const val CHEER_LABEL = "Cheer"
  const val NUDGE_LABEL = "Nudge"

  fun watchingCount(count: Int): String = if (count == 1) "Watching 1" else "Watching $count"

  fun subtitle(title: String, dayNo: Int, totalDays: Int): String = "$title · day $dayNo of $totalDays"

  fun countPill(done: Int, total: Int): String = "$done/$total"

  // PRD §7 accessibility: a habit's state shows as a check time or an em dash, and the count as
  // "2/5" — both are shapes on screen rather than something a screen reader can read out.
  fun habitStatusDescription(name: String, time: String, checkedToday: Boolean): String =
    if (checkedToday) "$name, checked off at $time" else "$name, not checked off yet"

  fun countPillDescription(done: Int, total: Int): String = "$done of $total habits done"
}
