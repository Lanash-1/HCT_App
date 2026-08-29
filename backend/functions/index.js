'use strict';

/**
 * Firebase Cloud Functions for Belay. Business logic lives in lib/ (pure, Jest-tested);
 * this file is just the Firestore/Functions wiring around it.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { onCall, HttpsError } = require('firebase-functions/v2/https');

const { evaluateDayRollover } = require('./lib/evaluateDayRollover');
const { buildWeeklyRecap } = require('./lib/buildWeeklyRecap');
const { validateMessage } = require('./lib/validateMessage');
const { classifyChallengeForDeletion } = require('./lib/classifyChallengeForDeletion');

initializeApp();

function todayDateString() {
  return new Date().toISOString().slice(0, 10); // YYYY-MM-DD
}

function yesterdayDateString() {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

function lastSevenDaysEndingToday() {
  const dates = [];
  for (let i = 6; i >= 0; i--) {
    const date = new Date();
    date.setUTCDate(date.getUTCDate() - i);
    dates.push(date.toISOString().slice(0, 10));
  }
  return dates;
}

/**
 * Daily: evaluates the day that just elapsed for every active challenge and writes the
 * resulting streaks/grace/perfect-day counts (docs/DATA_MODEL.md — derived fields are written
 * only here, never computed client-side and pushed up).
 */
exports.dayRollover = onSchedule('every day 00:10', async () => {
  const db = getFirestore();
  const date = yesterdayDateString();

  const challengesSnap = await db.collection('challenges').where('status', '==', 'active').get();

  for (const challengeDoc of challengesSnap.docs) {
    const challenge = challengeDoc.data();

    const habitsSnap = await db.collection('habits').where('challenge_id', '==', challengeDoc.id).get();
    if (habitsSnap.empty) continue;

    const checkInsSnap = await db
      .collection('check_ins')
      .where('challenge_id', '==', challengeDoc.id)
      .where('date', '==', date)
      .get();
    const doneHabitIds = new Set(checkInsSnap.docs.filter((doc) => doc.data().done === true).map((doc) => doc.data().habit_id));

    const habits = habitsSnap.docs.map((doc) => ({
      habitId: doc.id,
      checked: doneHabitIds.has(doc.id),
      currentStreak: Number(doc.data().current_streak) || 0,
    }));

    const result = evaluateDayRollover({
      habits,
      grace: {
        graceDaysTotal: Number(challenge.grace_days_total) || 0,
        graceDaysUsed: Number(challenge.grace_days_used) || 0,
      },
    });

    const batch = db.batch();
    for (const update of result.habitUpdates) {
      batch.update(db.collection('habits').doc(update.habitId), {
        current_streak: update.newStreak,
        // Set only on the day a streak actually breaks; cleared otherwise so a stale flag from
        // an earlier day never lingers (PRD §6.2 — the client shows a recovery moment for this).
        streak_broken_at: update.streakBroken ? date : null,
      });
    }
    batch.update(challengeDoc.ref, {
      grace_days_used: result.newGraceDaysUsed,
      perfect_days: result.isPerfectDay ? (Number(challenge.perfect_days) || 0) + 1 : Number(challenge.perfect_days) || 0,
    });
    await batch.commit();
  }
});

/**
 * Sunday: generates the weekly recap (docs/DATA_MODEL.md `recaps`) for every active
 * challenge, covering the 7 days ending on the day this runs.
 */
exports.weeklyRecap = onSchedule('every sunday 00:00', async () => {
  const db = getFirestore();
  const weekDates = lastSevenDaysEndingToday();
  const weekStart = weekDates[0];
  const weekEnd = weekDates[weekDates.length - 1];

  const challengesSnap = await db.collection('challenges').where('status', '==', 'active').get();

  for (const challengeDoc of challengesSnap.docs) {
    const habitsSnap = await db.collection('habits').where('challenge_id', '==', challengeDoc.id).get();
    if (habitsSnap.empty) continue;

    const checkInsSnap = await db
      .collection('check_ins')
      .where('challenge_id', '==', challengeDoc.id)
      .where('date', '>=', weekStart)
      .where('date', '<=', weekEnd)
      .get();

    const recap = buildWeeklyRecap({
      habits: habitsSnap.docs.map((doc) => ({ habitId: doc.id, name: doc.data().name })),
      checkIns: checkInsSnap.docs.map((doc) => ({
        habitId: doc.data().habit_id,
        date: doc.data().date,
        done: doc.data().done === true,
      })),
      weekDates,
    });

    await db.collection('recaps').add({
      challenge_id: challengeDoc.id,
      week_start: weekStart,
      week_end: weekEnd,
      check_ins_total: recap.checkInsTotal,
      check_ins_possible: recap.checkInsPossible,
      perfect_days: recap.perfectDays,
      per_habit_summary: JSON.stringify(recap.perHabitSummary),
      generated_at: new Date().toISOString(),
    });
  }
});

/**
 * Callable function invoked by the Android client to send a cheer or nudge
 * (docs/DATA_MODEL.md `interactions`). Identity comes from the verified Firebase Auth ID
 * token (request.auth), not a client-supplied field. Re-enforces message validation and the
 * 1/day nudge rate limit server-side — the client (InteractionRepository.kt) does the same
 * checks for immediate UX feedback, but this is the authoritative check.
 */
exports.cheerNudge = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Must be signed in.');
  }
  const fromUserId = request.auth.uid;
  const { challenge_id: challengeId, type, message } = request.data || {};

  if (type !== 'cheer' && type !== 'nudge') {
    throw new HttpsError('invalid-argument', 'INVALID_TYPE');
  }
  const messageError = validateMessage(message);
  if (messageError) {
    throw new HttpsError('invalid-argument', messageError);
  }

  const db = getFirestore();
  const date = todayDateString();

  if (type === 'nudge') {
    const existing = await db
      .collection('interactions')
      .where('challenge_id', '==', challengeId)
      .where('type', '==', 'nudge')
      .where('date', '==', date)
      .get();
    if (!existing.empty) {
      throw new HttpsError('already-exists', 'ALREADY_NUDGED_TODAY');
    }
  }

  const docRef = await db.collection('interactions').add({
    challenge_id: challengeId,
    from_user_id: fromUserId,
    type,
    date,
    message,
    created_at: new Date().toISOString(),
  });

  return { status: 'success', interactionId: docRef.id };
});

/**
 * Callable function invoked by the Android client to delete an account and its data
 * (PRD §6.3, docs/PRIVACY_POLICY.md "Data retention & deletion") — non-negotiable for Play
 * Store release. Runs with admin Firestore access so it isn't bound by what a regular signed-in
 * user's security rules would allow (e.g. deleting someone else's challenge is never allowed
 * directly, but is required here when that challenge is the requester's own).
 */
exports.deleteAccount = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Must be signed in.');
  }
  const userId = request.auth.uid;
  const db = getFirestore();

  const [asChallenger, asWitness] = await Promise.all([
    db.collection('challenges').where('challenger_user_id', '==', userId).get(),
    db.collection('challenges').where('witness_user_id', '==', userId).get(),
  ]);

  const batch = db.batch();

  for (const challengeDoc of [...asChallenger.docs, ...asWitness.docs]) {
    const action = classifyChallengeForDeletion(challengeDoc.data(), userId);
    if (action === 'clear_witness') {
      batch.update(challengeDoc.ref, { witness_user_id: null });
      continue;
    }
    if (action !== 'cascade_delete') continue;

    const challengeId = challengeDoc.id;
    const [habits, checkIns, interactions, recaps] = await Promise.all([
      db.collection('habits').where('challenge_id', '==', challengeId).get(),
      db.collection('check_ins').where('challenge_id', '==', challengeId).get(),
      db.collection('interactions').where('challenge_id', '==', challengeId).get(),
      db.collection('recaps').where('challenge_id', '==', challengeId).get(),
    ]);
    for (const doc of [...habits.docs, ...checkIns.docs, ...interactions.docs, ...recaps.docs]) {
      batch.delete(doc.ref);
    }
    batch.delete(challengeDoc.ref);
  }

  const pairings = await db
    .collection('pairings')
    .where('from_user_id', '==', userId)
    .get()
    .then((snap) => snap.docs)
    .then(async (fromDocs) => {
      const toSnap = await db.collection('pairings').where('to_user_id', '==', userId).get();
      return [...fromDocs, ...toSnap.docs];
    });
  for (const doc of pairings) {
    batch.delete(doc.ref);
  }

  batch.delete(db.collection('users').doc(userId));

  await batch.commit();
  await getAuth().deleteUser(userId);

  return { status: 'success' };
});
