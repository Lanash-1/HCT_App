package com.codigitech.belay.ui.today

/** Centralized user-facing copy for the Today screen (see CLAUDE.md conventions). */
object TodayCopy {
  const val EMPTY_TITLE = "No active challenge"
  const val EMPTY_DETAIL = "Create a challenge to start checking in."
  const val PERFECT_DAYS_LABEL = "Perfect days"
  const val GRACE_LEFT_LABEL = "Grace left"
  const val DAYS_TO_GO_LABEL = "Days to go"
  const val NUDGE_DISMISS_LABEL = "Got it"

  fun progressLabel(checked: Int, total: Int): String = "$checked/$total"

  fun witnessStatusText(witnessName: String, checked: Int, total: Int): String {
    val statusShort =
      when {
        total == 0 || checked == 0 -> "waiting"
        checked == total -> "saw all $total"
        else -> "watching you finish"
      }
    return "$witnessName is watching · $statusShort"
  }
}
