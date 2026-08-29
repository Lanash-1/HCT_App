package com.codigitech.belay.ui.recap

/** Centralized user-facing copy for the Weekly recap screen (see CLAUDE.md conventions). */
object RecapCopy {
  const val TITLE = "Weekly recap"
  const val EMPTY_TITLE = "No recap yet"
  const val EMPTY_DETAIL = "Your first weekly recap lands here after your first Sunday in the challenge."
  const val SHARE_LABEL = "Share card"
  const val SAVE_LABEL = "Save"
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
}
