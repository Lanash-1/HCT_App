package com.codigitech.belay.domain.challenge

/**
 * The states the design's prototype never showed (PRD §6.7). Each is a real situation a shipped
 * app hits — a witness who never opened the invite, a challenge that ran out of days, grace spent
 * down to nothing — and each needs an answer that isn't an empty screen.
 *
 * Pure functions rather than fields on the entities: the same rules are read by the Today screen
 * and by witness mode, and "when is a challenge over" should not have two implementations.
 */

/** How long a witness may be silent before the challenger is told (PRD §6.7). */
const val WITNESS_AWAY_THRESHOLD_DAYS = 3

/** A challenge covers [durationDays] days starting on [startDate]; it's over once they've all elapsed. */
fun hasChallengeEnded(startDate: Long, durationDays: Int, today: Long): Boolean = today - startDate >= durationDays

/**
 * Days since the witness last opened the app, or null if they never have.
 *
 * Null is a distinct state, not zero: a witness who hasn't accepted yet gets different copy from
 * one who's been away. A last-seen in the future (clock skew between two devices) reads as
 * present rather than as a negative absence.
 */
fun witnessDaysAway(lastSeenEpochDay: Long?, today: Long): Int? =
  lastSeenEpochDay?.let { (today - it).coerceAtLeast(0).toInt() }

fun isWitnessAway(daysAway: Int?): Boolean = daysAway != null && daysAway >= WITNESS_AWAY_THRESHOLD_DAYS

/** Grace is decided at creation and only ever spent (PRD §4.6) — a challenge set up with none starts with none. */
fun isGraceExhausted(graceDaysTotal: Int, graceDaysUsed: Int): Boolean = graceDaysUsed >= graceDaysTotal
