'use strict';

/**
 * Whether a check-in write just turned an incomplete day into a complete one — the moment the
 * witness gets told "X finished the day" (docs/TECH_STACK.md §4).
 *
 * Gated on the not-done → done transition rather than on the resulting state, because check-ins
 * are re-written on sync-queue replay (PRD §6.6); without that gate a witness would get the same
 * notification again every time an already-synced row was pushed a second time.
 *
 * @param {{becameDone: boolean, habitIds: string[], doneHabitIds: string[]}} input
 * @returns {boolean}
 */
function shouldAnnounceDayComplete({ becameDone, habitIds, doneHabitIds }) {
  if (!becameDone) return false;
  if (!habitIds || habitIds.length === 0) return false;
  const done = new Set(doneHabitIds);
  return habitIds.every((habitId) => done.has(habitId));
}

module.exports = { shouldAnnounceDayComplete };
