'use strict';

const { shouldAnnounceDayComplete } = require('./shouldAnnounceDayComplete');

describe('shouldAnnounceDayComplete', () => {
  test('announces when the final habit of the day gets checked', () => {
    expect(shouldAnnounceDayComplete({ becameDone: true, habitIds: ['h1', 'h2'], doneHabitIds: ['h1', 'h2'] })).toBe(true);
  });

  test('stays quiet while habits are still outstanding', () => {
    expect(shouldAnnounceDayComplete({ becameDone: true, habitIds: ['h1', 'h2'], doneHabitIds: ['h1'] })).toBe(false);
  });

  test('does not re-announce a day that was already complete before this write', () => {
    // e.g. a sync-queue replay of an already-synced check-in (PRD §6.6) rewrites an unchanged row.
    expect(shouldAnnounceDayComplete({ becameDone: false, habitIds: ['h1'], doneHabitIds: ['h1'] })).toBe(false);
  });

  test('does not announce when a habit is unchecked back to incomplete', () => {
    expect(shouldAnnounceDayComplete({ becameDone: false, habitIds: ['h1', 'h2'], doneHabitIds: ['h1'] })).toBe(false);
  });

  test('a challenge with no habits never counts as a complete day', () => {
    expect(shouldAnnounceDayComplete({ becameDone: true, habitIds: [], doneHabitIds: [] })).toBe(false);
  });

  test('ignores check-ins for habits no longer on the challenge', () => {
    expect(shouldAnnounceDayComplete({ becameDone: true, habitIds: ['h1'], doneHabitIds: ['h1', 'deleted-habit'] })).toBe(true);
  });
});
