'use strict';

/**
 * Builds one week's recap for a challenge (docs/DATA_MODEL.md `recaps`, matching the
 * shareable recap screen's 7-cell-per-habit grid).
 *
 * @param {{
 *   habits: Array<{habitId: string, name: string}>,
 *   checkIns: Array<{habitId: string, date: string, done: boolean}>,
 *   weekDates: string[],
 * }} input weekDates is the 7 ISO date strings (YYYY-MM-DD) covered by the recap, in order.
 * @returns {{
 *   checkInsTotal: number,
 *   checkInsPossible: number,
 *   perfectDays: number,
 *   perHabitSummary: Array<{habitId: string, name: string, score: number, dailyCells: boolean[]}>,
 * }}
 */
function buildWeeklyRecap({ habits, checkIns, weekDates }) {
  const checkInsPossible = habits.length * weekDates.length;
  const doneKeys = new Set(checkIns.filter((c) => c.done).map((c) => `${c.habitId}|${c.date}`));

  let checkInsTotal = 0;
  const perHabitSummary = habits.map((habit) => {
    const dailyCells = weekDates.map((date) => doneKeys.has(`${habit.habitId}|${date}`));
    const score = dailyCells.filter(Boolean).length;
    checkInsTotal += score;
    return { habitId: habit.habitId, name: habit.name, score, dailyCells };
  });

  let perfectDays = 0;
  for (const date of weekDates) {
    const allChecked = habits.every((habit) => doneKeys.has(`${habit.habitId}|${date}`));
    if (allChecked) perfectDays += 1;
  }

  return { checkInsTotal, checkInsPossible, perfectDays, perHabitSummary };
}

module.exports = { buildWeeklyRecap };
