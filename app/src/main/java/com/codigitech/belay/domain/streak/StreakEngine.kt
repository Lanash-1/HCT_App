package com.codigitech.belay.domain.streak

/** A single habit's check-in state and current streak going into a day-rollover evaluation. */
data class HabitDayResult(val habitId: String, val checked: Boolean, val currentStreak: Int)

/** Grace days are a challenge-level counter, not per-habit (see docs/DATA_MODEL.md `challenges`). */
data class ChallengeGraceState(val graceDaysTotal: Int, val graceDaysUsed: Int)

data class DayRolloverInput(val habits: List<HabitDayResult>, val grace: ChallengeGraceState)

data class HabitStreakUpdate(val habitId: String, val newStreak: Int)

data class DayRolloverResult(
  val habitUpdates: List<HabitStreakUpdate>,
  val newGraceDaysUsed: Int,
  val isPerfectDay: Boolean,
)

/**
 * Evaluates one elapsed challenge day. A day is perfect only if every habit was checked; a
 * miss consumes at most one grace day for the whole day (never per habit). While grace remains,
 * missed habits are frozen (streak unchanged); once grace is exhausted, missed habits reset to 0.
 */
fun evaluateDayRollover(input: DayRolloverInput): DayRolloverResult {
  val isPerfectDay = input.habits.all { it.checked }

  if (isPerfectDay) {
    return DayRolloverResult(
      habitUpdates = input.habits.map { HabitStreakUpdate(it.habitId, it.currentStreak + 1) },
      newGraceDaysUsed = input.grace.graceDaysUsed,
      isPerfectDay = true,
    )
  }

  val graceAvailable = input.grace.graceDaysUsed < input.grace.graceDaysTotal
  val newGraceDaysUsed = if (graceAvailable) input.grace.graceDaysUsed + 1 else input.grace.graceDaysUsed

  val habitUpdates =
    input.habits.map { habit ->
      val newStreak =
        when {
          habit.checked -> habit.currentStreak + 1
          graceAvailable -> habit.currentStreak
          else -> 0
        }
      HabitStreakUpdate(habit.habitId, newStreak)
    }

  return DayRolloverResult(habitUpdates = habitUpdates, newGraceDaysUsed = newGraceDaysUsed, isPerfectDay = false)
}
