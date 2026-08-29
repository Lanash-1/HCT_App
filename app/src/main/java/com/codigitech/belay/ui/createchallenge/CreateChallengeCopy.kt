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
  const val DURATION_SECTION = "How long"
  const val WITNESS_SECTION = "Who's watching"
  const val WITNESS_EMPTY = "No one paired yet — pair with a witness from onboarding first."
  const val GRACE_SECTION = "Grace days"
  const val GRACE_TITLE = "Imperfect days allowed"
  const val GRACE_DETAIL = "Decide now, not at 11 pm on day 14."
  const val SAVE = "Save"

  fun habitCount(count: Int): String = "$count of 5"

  fun durationLabel(days: Int): String = "$days days"
}
