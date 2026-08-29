'use strict';

/**
 * Daily cron: evaluates the day that just elapsed for every active challenge and writes the
 * resulting streaks/grace/perfect-day counts (docs/DATA_MODEL.md — derived fields are written
 * only here, never computed client-side and pushed up).
 *
 * Expects Data Store tables matching docs/DATA_MODEL.md exactly: `challenges`
 * (status, grace_days_total, grace_days_used, perfect_days), `habits` (challenge_id,
 * current_streak), `check_ins` (challenge_id, habit_id, date, done). These tables must be
 * created in the Catalyst console before this function can run for real — table creation
 * isn't available from the CLI.
 */

const catalyst = require('zcatalyst-sdk-node');
const { evaluateDayRollover } = require('./lib/evaluateDayRollover');

function yesterdayDateString() {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10); // YYYY-MM-DD
}

module.exports = async (cronDetails, context) => {
  try {
    const app = catalyst.initialize(context);
    const zcql = app.zcql();
    const datastore = app.datastore();
    const date = yesterdayDateString();

    const challengeRows = await zcql.executeZCQLQuery(
      "SELECT ROWID, grace_days_total, grace_days_used, perfect_days FROM challenges WHERE status = 'active'"
    );

    for (const row of challengeRows) {
      const challenge = row.challenges;
      const challengeId = challenge.ROWID;

      const habitRows = await zcql.executeZCQLQuery(
        `SELECT ROWID, current_streak FROM habits WHERE challenge_id = ${challengeId}`
      );
      if (habitRows.length === 0) continue;

      const checkInRows = await zcql.executeZCQLQuery(
        `SELECT habit_id, done FROM check_ins WHERE challenge_id = ${challengeId} AND date = '${date}'`
      );
      const doneHabitIds = new Set(
        checkInRows.filter((r) => r.check_ins.done === true).map((r) => String(r.check_ins.habit_id))
      );

      const habits = habitRows.map((r) => ({
        habitId: r.habits.ROWID,
        checked: doneHabitIds.has(String(r.habits.ROWID)),
        currentStreak: Number(r.habits.current_streak) || 0,
      }));

      const result = evaluateDayRollover({
        habits,
        grace: {
          graceDaysTotal: Number(challenge.grace_days_total) || 0,
          graceDaysUsed: Number(challenge.grace_days_used) || 0,
        },
      });

      await datastore.table('habits').updateRows(
        result.habitUpdates.map((update) => ({ ROWID: update.habitId, current_streak: update.newStreak }))
      );

      await datastore.table('challenges').updateRow({
        ROWID: challengeId,
        grace_days_used: result.newGraceDaysUsed,
        perfect_days: result.isPerfectDay ? (Number(challenge.perfect_days) || 0) + 1 : Number(challenge.perfect_days) || 0,
      });
    }

    context.closeWithSuccess();
  } catch (err) {
    console.error('dayRollover failed', err);
    context.closeWithFailure();
  }
};
