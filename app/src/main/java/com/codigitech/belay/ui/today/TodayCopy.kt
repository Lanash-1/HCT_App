package com.codigitech.belay.ui.today

/** Centralized user-facing copy for the Today screen (see CLAUDE.md conventions). */
object TodayCopy {
  const val EMPTY_TITLE = "No active challenge"
  const val EMPTY_DETAIL = "Create a challenge to start checking in."
  const val PERFECT_DAYS_LABEL = "Perfect days"
  const val GRACE_LEFT_LABEL = "Grace left"
  const val DAYS_TO_GO_LABEL = "Days to go"
  const val NUDGE_DISMISS_LABEL = "Got it"
  const val RECOVERY_TITLE = "Your streak reset"
  const val RECOVERY_DETAIL = "Grace ran out, so this one's starting over. That's the deal — it doesn't erase what you already built."
  const val RECOVERY_CONTINUE = "Start again"

  // PRD §6.7 edge states — the situations the prototype's happy path never showed.
  const val NO_WITNESS_STATUS = "No witness yet · share your code to add one"
  const val GRACE_EXHAUSTED_TITLE = "No grace days left"
  const val GRACE_EXHAUSTED_DETAIL = "Miss a habit now and its streak goes back to zero. Nothing else changes."
  const val ENDED_TITLE = "This challenge is over"
  const val ENDED_DETAIL = "Every day of it is counted. Start another when you're ready."

  fun witnessNotOpenedYet(witnessName: String): String = "$witnessName hasn't opened Belay yet"

  fun witnessAway(witnessName: String, daysAway: Int): String =
    "$witnessName hasn't looked in for $daysAway day${if (daysAway == 1) "" else "s"}"

  fun progressLabel(checked: Int, total: Int): String = "$checked/$total"

  // PRD §7 accessibility: the ring is a drawn arc around a bare "3/5", and a habit row is a
  // checkbox next to loose text — neither says anything useful to a screen reader as-is.
  fun progressRingDescription(checked: Int, total: Int): String =
    when {
      total == 0 -> "No habits yet"
      checked == total -> "All $total habits checked off today"
      else -> "$checked of $total habits checked off today"
    }

  fun habitRowDescription(name: String, detail: String?, streak: Int, checked: Boolean): String {
    val what = if (detail.isNullOrBlank()) name else "$name, $detail"
    val state = if (checked) "Checked off." else "Not checked off yet."
    val streakPart = if (streak == 0) "No streak yet." else "$streak day streak."
    return "$what. $state $streakPart"
  }

  fun recoveryHabitList(names: List<String>): String = names.joinToString(", ")

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
