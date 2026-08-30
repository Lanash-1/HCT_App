package com.codigitech.belay.ui.recap

/** Centralized user-facing copy for the Weekly recap screen (see CLAUDE.md conventions). */
object RecapCopy {
  const val TITLE = "Weekly recap"
  const val EMPTY_TITLE = "No recap yet"
  const val EMPTY_DETAIL = "Your first weekly recap lands here after your first Sunday in the challenge."
  const val SHARE_LABEL = "Share card"
  const val SAVE_LABEL = "Save"
  const val SAVED_CONFIRMATION = "Saved to your photos."
  const val SAVE_FAILED = "Couldn't save the card."
  const val SHARE_FAILED = "Couldn't prepare the card to share."
  const val AUTO_SEND_NOTE_SUFFIX = " automatically every Sunday."

  fun checkInsLine(checkInsTotal: Int, checkInsPossible: Int, perfectDays: Int): String =
    "$checkInsTotal of $checkInsPossible check-ins · $perfectDays perfect day${if (perfectDays == 1) "" else "s"}"

  fun witnessedByLine(witnessName: String): String = "Witnessed by $witnessName — every habit, every day."

  fun autoSendNote(witnessName: String): String = "Sent to $witnessName$AUTO_SEND_NOTE_SUFFIX"

  fun shareText(
    challengeTitle: String,
    weekRangeLabel: String,
    checkInsTotal: Int,
    checkInsPossible: Int,
    perfectDays: Int,
    witnessName: String,
  ): String =
    "$challengeTitle ($weekRangeLabel): $checkInsTotal of $checkInsPossible check-ins, " +
      "$perfectDays perfect day${if (perfectDays == 1) "" else "s"}. Witnessed by $witnessName."

  /**
   * PRD §7 accessibility: the per-habit week renders as seven coloured boxes, which a screen
   * reader can say nothing about. Names the missed days instead — that's the information the
   * boxes carry.
   *
   * Cells run Monday-first: a recap covers the seven days ending on the Sunday it is generated
   * (backend/functions weeklyRecap).
   */
  fun habitGridDescription(name: String, score: String, cells: List<Boolean>): String {
    val spokenScore = score.replace("/", " of ")
    val missed = cells.indices.filter { !cells[it] }.map { WEEKDAYS.getOrElse(it) { "day ${it + 1}" } }
    val tail = if (missed.isEmpty()) "Every day." else "Missed ${missed.joinToString(", ")}."
    return "$name: $spokenScore days. $tail"
  }

  private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
}
