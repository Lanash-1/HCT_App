'use strict';

/**
 * Sunday cron: generates the weekly recap (docs/DATA_MODEL.md `recaps`) for every active
 * challenge, covering the 7 days ending on the day this runs.
 *
 * Expects Data Store tables matching docs/DATA_MODEL.md — must be created in the Catalyst
 * console before this function can run for real.
 */

const catalyst = require('zcatalyst-sdk-node');
const { buildWeeklyRecap } = require('./lib/buildWeeklyRecap');

function lastSevenDaysEndingToday() {
  const dates = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setUTCDate(d.getUTCDate() - i);
    dates.push(d.toISOString().slice(0, 10));
  }
  return dates;
}

module.exports = async (cronDetails, context) => {
  try {
    const app = catalyst.initialize(context);
    const zcql = app.zcql();
    const datastore = app.datastore();
    const weekDates = lastSevenDaysEndingToday();
    const weekStart = weekDates[0];
    const weekEnd = weekDates[weekDates.length - 1];

    const challengeRows = await zcql.executeZCQLQuery(
      "SELECT ROWID FROM challenges WHERE status = 'active'"
    );

    for (const row of challengeRows) {
      const challengeId = row.challenges.ROWID;

      const habitRows = await zcql.executeZCQLQuery(
        `SELECT ROWID, name FROM habits WHERE challenge_id = ${challengeId}`
      );
      if (habitRows.length === 0) continue;

      const checkInRows = await zcql.executeZCQLQuery(
        `SELECT habit_id, date, done FROM check_ins WHERE challenge_id = ${challengeId} AND date >= '${weekStart}' AND date <= '${weekEnd}'`
      );

      const recap = buildWeeklyRecap({
        habits: habitRows.map((r) => ({ habitId: r.habits.ROWID, name: r.habits.name })),
        checkIns: checkInRows.map((r) => ({
          habitId: r.check_ins.habit_id,
          date: r.check_ins.date,
          done: r.check_ins.done === true,
        })),
        weekDates,
      });

      await datastore.table('recaps').insertRow({
        challenge_id: challengeId,
        week_start: weekStart,
        week_end: weekEnd,
        check_ins_total: recap.checkInsTotal,
        check_ins_possible: recap.checkInsPossible,
        perfect_days: recap.perfectDays,
        per_habit_summary: JSON.stringify(recap.perHabitSummary),
      });
    }

    context.closeWithSuccess();
  } catch (err) {
    console.error('weeklyRecap failed', err);
    context.closeWithFailure();
  }
};
