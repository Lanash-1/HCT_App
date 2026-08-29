'use strict';

const { buildWeeklyRecap } = require('./buildWeeklyRecap');

const WEEK = ['2026-01-05', '2026-01-06', '2026-01-07', '2026-01-08', '2026-01-09', '2026-01-10', '2026-01-11'];

describe('buildWeeklyRecap', () => {
  test('every habit checked every day is a perfect week', () => {
    const habits = [{ habitId: 'h1', name: 'Run' }, { habitId: 'h2', name: 'Read' }];
    const checkIns = habits.flatMap((h) => WEEK.map((date) => ({ habitId: h.habitId, date, done: true })));

    const result = buildWeeklyRecap({ habits, checkIns, weekDates: WEEK });

    expect(result.checkInsTotal).toBe(14);
    expect(result.checkInsPossible).toBe(14);
    expect(result.perfectDays).toBe(7);
    expect(result.perHabitSummary).toEqual([
      { habitId: 'h1', name: 'Run', score: 7, dailyCells: WEEK.map(() => true) },
      { habitId: 'h2', name: 'Read', score: 7, dailyCells: WEEK.map(() => true) },
    ]);
  });

  test('no check-ins at all', () => {
    const habits = [{ habitId: 'h1', name: 'Run' }];

    const result = buildWeeklyRecap({ habits, checkIns: [], weekDates: WEEK });

    expect(result.checkInsTotal).toBe(0);
    expect(result.checkInsPossible).toBe(7);
    expect(result.perfectDays).toBe(0);
    expect(result.perHabitSummary[0].dailyCells).toEqual(WEEK.map(() => false));
  });

  test('one habit missed on one day breaks that day\'s perfect-day status only', () => {
    const habits = [{ habitId: 'h1', name: 'Run' }, { habitId: 'h2', name: 'Read' }];
    const checkIns = [
      ...WEEK.map((date) => ({ habitId: 'h1', date, done: true })),
      ...WEEK.filter((date) => date !== '2026-01-07').map((date) => ({ habitId: 'h2', date, done: true })),
    ];

    const result = buildWeeklyRecap({ habits, checkIns, weekDates: WEEK });

    expect(result.checkInsTotal).toBe(13);
    expect(result.perfectDays).toBe(6);
  });
});
