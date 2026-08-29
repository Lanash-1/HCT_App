package com.codigitech.belay.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test table for the day-rollover rule set (see docs/TECH_STACK.md §7 and the plan decision
 * it was written against):
 *
 * - Complete day (every habit checked): every habit's streak +1, perfect day.
 * - Incomplete day, grace remaining: grace used +1; checked habits +1; missed habits frozen
 *   (streak unchanged); not a perfect day.
 * - Incomplete day, grace exhausted: checked habits +1; missed habits reset to 0.
 *
 * Grace is a challenge-level counter, not per-habit, and is consumed at most once per day
 * regardless of how many habits were missed that day.
 */
class StreakEngineTest {

  private fun habit(id: String, checked: Boolean, currentStreak: Int) =
    HabitDayResult(habitId = id, checked = checked, currentStreak = currentStreak)

  @Test
  fun `complete day increments every habit streak and marks perfect day`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits = listOf(habit("h1", checked = true, currentStreak = 3), habit("h2", checked = true, currentStreak = 0)),
          grace = ChallengeGraceState(graceDaysTotal = 2, graceDaysUsed = 0),
        )
      )

    assertEquals(listOf(HabitStreakUpdate("h1", 4, streakBroken = false), HabitStreakUpdate("h2", 1, streakBroken = false)), result.habitUpdates)
    assertEquals(0, result.newGraceDaysUsed)
    assertEquals(true, result.isPerfectDay)
  }

  @Test
  fun `single miss with grace remaining freezes the missed habit and consumes one grace day`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits = listOf(habit("h1", checked = true, currentStreak = 5), habit("h2", checked = false, currentStreak = 7)),
          grace = ChallengeGraceState(graceDaysTotal = 2, graceDaysUsed = 0),
        )
      )

    assertEquals(listOf(HabitStreakUpdate("h1", 6, streakBroken = false), HabitStreakUpdate("h2", 7, streakBroken = false)), result.habitUpdates)
    assertEquals(1, result.newGraceDaysUsed)
    assertEquals(false, result.isPerfectDay)
  }

  @Test
  fun `single miss with grace exhausted resets only the missed habit`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits = listOf(habit("h1", checked = true, currentStreak = 5), habit("h2", checked = false, currentStreak = 7)),
          grace = ChallengeGraceState(graceDaysTotal = 2, graceDaysUsed = 2),
        )
      )

    assertEquals(listOf(HabitStreakUpdate("h1", 6, streakBroken = false), HabitStreakUpdate("h2", 0, streakBroken = true)), result.habitUpdates)
    assertEquals(2, result.newGraceDaysUsed)
    assertEquals(false, result.isPerfectDay)
  }

  @Test
  fun `grace-exhausted miss on a habit already at zero streak does not count as a break`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits = listOf(habit("h1", checked = false, currentStreak = 0)),
          grace = ChallengeGraceState(graceDaysTotal = 1, graceDaysUsed = 1),
        )
      )

    assertEquals(listOf(HabitStreakUpdate("h1", 0, streakBroken = false)), result.habitUpdates)
  }

  @Test
  fun `multiple misses in one day with grace remaining consume only a single grace day`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits =
            listOf(
              habit("h1", checked = false, currentStreak = 3),
              habit("h2", checked = false, currentStreak = 9),
              habit("h3", checked = true, currentStreak = 1),
            ),
          grace = ChallengeGraceState(graceDaysTotal = 3, graceDaysUsed = 1),
        )
      )

    assertEquals(
      listOf(
        HabitStreakUpdate("h1", 3, streakBroken = false),
        HabitStreakUpdate("h2", 9, streakBroken = false),
        HabitStreakUpdate("h3", 2, streakBroken = false),
      ),
      result.habitUpdates,
    )
    assertEquals(2, result.newGraceDaysUsed)
    assertEquals(false, result.isPerfectDay)
  }

  @Test
  fun `multiple misses in one day with grace exhausted reset every missed habit`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits =
            listOf(
              habit("h1", checked = false, currentStreak = 3),
              habit("h2", checked = false, currentStreak = 9),
              habit("h3", checked = true, currentStreak = 1),
            ),
          grace = ChallengeGraceState(graceDaysTotal = 1, graceDaysUsed = 1),
        )
      )

    assertEquals(
      listOf(
        HabitStreakUpdate("h1", 0, streakBroken = true),
        HabitStreakUpdate("h2", 0, streakBroken = true),
        HabitStreakUpdate("h3", 2, streakBroken = false),
      ),
      result.habitUpdates,
    )
    assertEquals(1, result.newGraceDaysUsed)
    assertEquals(false, result.isPerfectDay)
  }

  @Test
  fun `zero grace days total means any miss immediately resets`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits = listOf(habit("h1", checked = false, currentStreak = 10)),
          grace = ChallengeGraceState(graceDaysTotal = 0, graceDaysUsed = 0),
        )
      )

    assertEquals(listOf(HabitStreakUpdate("h1", 0, streakBroken = true)), result.habitUpdates)
    assertEquals(0, result.newGraceDaysUsed)
    assertEquals(false, result.isPerfectDay)
  }

  @Test
  fun `a fully complete day never touches the grace counter even if grace was already partly used`() {
    val result =
      evaluateDayRollover(
        DayRolloverInput(
          habits = listOf(habit("h1", checked = true, currentStreak = 2)),
          grace = ChallengeGraceState(graceDaysTotal = 3, graceDaysUsed = 2),
        )
      )

    assertEquals(2, result.newGraceDaysUsed)
    assertEquals(true, result.isPerfectDay)
  }
}
