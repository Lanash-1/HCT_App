'use strict';

const { evaluateDayRollover } = require('./evaluateDayRollover');

// Same test table as app/src/test/java/com/codigitech/belay/domain/streak/StreakEngineTest.kt —
// keep both in sync.
describe('evaluateDayRollover', () => {
  test('complete day increments every habit streak and marks perfect day', () => {
    const result = evaluateDayRollover({
      habits: [
        { habitId: 'h1', checked: true, currentStreak: 3 },
        { habitId: 'h2', checked: true, currentStreak: 0 },
      ],
      grace: { graceDaysTotal: 2, graceDaysUsed: 0 },
    });

    expect(result.habitUpdates).toEqual([
      { habitId: 'h1', newStreak: 4, streakBroken: false },
      { habitId: 'h2', newStreak: 1, streakBroken: false },
    ]);
    expect(result.newGraceDaysUsed).toBe(0);
    expect(result.isPerfectDay).toBe(true);
  });

  test('single miss with grace remaining freezes the missed habit and consumes one grace day', () => {
    const result = evaluateDayRollover({
      habits: [
        { habitId: 'h1', checked: true, currentStreak: 5 },
        { habitId: 'h2', checked: false, currentStreak: 7 },
      ],
      grace: { graceDaysTotal: 2, graceDaysUsed: 0 },
    });

    expect(result.habitUpdates).toEqual([
      { habitId: 'h1', newStreak: 6, streakBroken: false },
      { habitId: 'h2', newStreak: 7, streakBroken: false },
    ]);
    expect(result.newGraceDaysUsed).toBe(1);
    expect(result.isPerfectDay).toBe(false);
  });

  test('single miss with grace exhausted resets only the missed habit', () => {
    const result = evaluateDayRollover({
      habits: [
        { habitId: 'h1', checked: true, currentStreak: 5 },
        { habitId: 'h2', checked: false, currentStreak: 7 },
      ],
      grace: { graceDaysTotal: 2, graceDaysUsed: 2 },
    });

    expect(result.habitUpdates).toEqual([
      { habitId: 'h1', newStreak: 6, streakBroken: false },
      { habitId: 'h2', newStreak: 0, streakBroken: true },
    ]);
    expect(result.newGraceDaysUsed).toBe(2);
    expect(result.isPerfectDay).toBe(false);
  });

  test('grace-exhausted miss on a habit already at zero streak does not count as a break', () => {
    const result = evaluateDayRollover({
      habits: [{ habitId: 'h1', checked: false, currentStreak: 0 }],
      grace: { graceDaysTotal: 1, graceDaysUsed: 1 },
    });

    expect(result.habitUpdates).toEqual([{ habitId: 'h1', newStreak: 0, streakBroken: false }]);
  });

  test('multiple misses in one day with grace remaining consume only a single grace day', () => {
    const result = evaluateDayRollover({
      habits: [
        { habitId: 'h1', checked: false, currentStreak: 3 },
        { habitId: 'h2', checked: false, currentStreak: 9 },
        { habitId: 'h3', checked: true, currentStreak: 1 },
      ],
      grace: { graceDaysTotal: 3, graceDaysUsed: 1 },
    });

    expect(result.habitUpdates).toEqual([
      { habitId: 'h1', newStreak: 3, streakBroken: false },
      { habitId: 'h2', newStreak: 9, streakBroken: false },
      { habitId: 'h3', newStreak: 2, streakBroken: false },
    ]);
    expect(result.newGraceDaysUsed).toBe(2);
    expect(result.isPerfectDay).toBe(false);
  });

  test('multiple misses in one day with grace exhausted reset every missed habit', () => {
    const result = evaluateDayRollover({
      habits: [
        { habitId: 'h1', checked: false, currentStreak: 3 },
        { habitId: 'h2', checked: false, currentStreak: 9 },
        { habitId: 'h3', checked: true, currentStreak: 1 },
      ],
      grace: { graceDaysTotal: 1, graceDaysUsed: 1 },
    });

    expect(result.habitUpdates).toEqual([
      { habitId: 'h1', newStreak: 0, streakBroken: true },
      { habitId: 'h2', newStreak: 0, streakBroken: true },
      { habitId: 'h3', newStreak: 2, streakBroken: false },
    ]);
    expect(result.newGraceDaysUsed).toBe(1);
    expect(result.isPerfectDay).toBe(false);
  });

  test('zero grace days total means any miss immediately resets', () => {
    const result = evaluateDayRollover({
      habits: [{ habitId: 'h1', checked: false, currentStreak: 10 }],
      grace: { graceDaysTotal: 0, graceDaysUsed: 0 },
    });

    expect(result.habitUpdates).toEqual([{ habitId: 'h1', newStreak: 0, streakBroken: true }]);
    expect(result.newGraceDaysUsed).toBe(0);
    expect(result.isPerfectDay).toBe(false);
  });

  test('a fully complete day never touches the grace counter even if grace was already partly used', () => {
    const result = evaluateDayRollover({
      habits: [{ habitId: 'h1', checked: true, currentStreak: 2 }],
      grace: { graceDaysTotal: 3, graceDaysUsed: 2 },
    });

    expect(result.newGraceDaysUsed).toBe(2);
    expect(result.isPerfectDay).toBe(true);
  });
});
