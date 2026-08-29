'use strict';

/**
 * Evaluates one elapsed challenge day. Mirrors the Kotlin reference implementation in
 * app/src/main/java/com/codigitech/belay/domain/streak/StreakEngine.kt — keep both in sync;
 * this is the server-authoritative version (docs/DATA_MODEL.md: derived fields are written
 * only by Catalyst Functions, never computed client-side and pushed up).
 *
 * A day is perfect only if every habit was checked. A miss consumes at most one grace day for
 * the whole day (never per habit). While grace remains, missed habits are frozen (streak
 * unchanged); once grace is exhausted, missed habits reset to 0.
 *
 * @param {{
 *   habits: Array<{habitId: string|number, checked: boolean, currentStreak: number}>,
 *   grace: {graceDaysTotal: number, graceDaysUsed: number},
 * }} input
 * @returns {{
 *   habitUpdates: Array<{habitId: string|number, newStreak: number}>,
 *   newGraceDaysUsed: number,
 *   isPerfectDay: boolean,
 * }}
 */
function evaluateDayRollover(input) {
  const { habits, grace } = input;
  const isPerfectDay = habits.every((habit) => habit.checked);

  if (isPerfectDay) {
    return {
      habitUpdates: habits.map((habit) => ({ habitId: habit.habitId, newStreak: habit.currentStreak + 1 })),
      newGraceDaysUsed: grace.graceDaysUsed,
      isPerfectDay: true,
    };
  }

  const graceAvailable = grace.graceDaysUsed < grace.graceDaysTotal;
  const newGraceDaysUsed = graceAvailable ? grace.graceDaysUsed + 1 : grace.graceDaysUsed;

  const habitUpdates = habits.map((habit) => {
    let newStreak;
    if (habit.checked) {
      newStreak = habit.currentStreak + 1;
    } else if (graceAvailable) {
      newStreak = habit.currentStreak;
    } else {
      newStreak = 0;
    }
    return { habitId: habit.habitId, newStreak };
  });

  return { habitUpdates, newGraceDaysUsed, isPerfectDay: false };
}

module.exports = { evaluateDayRollover };
