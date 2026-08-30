package com.codigitech.belay.domain.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeEdgeStatesTest {

  @Test
  fun `a challenge is over once its last day has elapsed`() {
    // A 7-day challenge starting on day 100 covers days 100..106.
    assertFalse(hasChallengeEnded(startDate = 100, durationDays = 7, today = 106))
    assertTrue(hasChallengeEnded(startDate = 100, durationDays = 7, today = 107))
  }

  @Test
  fun `a challenge starting today has not ended`() {
    assertFalse(hasChallengeEnded(startDate = 100, durationDays = 7, today = 100))
  }

  @Test
  fun `a clock that has gone backwards does not end the challenge early`() {
    // Time zone changes and manual clock edits both make "today" jump around.
    assertFalse(hasChallengeEnded(startDate = 100, durationDays = 7, today = 98))
  }

  @Test
  fun `days a witness has been away is counted from their last visit`() {
    assertEquals(3, witnessDaysAway(lastSeenEpochDay = 97, today = 100))
    assertEquals(0, witnessDaysAway(lastSeenEpochDay = 100, today = 100))
  }

  @Test
  fun `a witness who has never opened the app has no day count`() {
    // Distinct from "away for 0 days" — the UI says something different for each.
    assertNull(witnessDaysAway(lastSeenEpochDay = null, today = 100))
  }

  @Test
  fun `a witness last seen in the future counts as present, not away a negative number of days`() {
    assertEquals(0, witnessDaysAway(lastSeenEpochDay = 105, today = 100))
  }

  @Test
  fun `a witness is only flagged as away after several days, not after one quiet evening`() {
    assertFalse(isWitnessAway(daysAway = 2))
    assertTrue(isWitnessAway(daysAway = WITNESS_AWAY_THRESHOLD_DAYS))
    assertTrue(isWitnessAway(daysAway = 30))
  }

  @Test
  fun `a witness who has never opened the app is not flagged as away`() {
    // They've been invited, not absent — the app says "hasn't opened Belay yet" instead.
    assertFalse(isWitnessAway(daysAway = null))
  }

  @Test
  fun `grace is exhausted only once every grace day has been spent`() {
    assertFalse(isGraceExhausted(graceDaysTotal = 2, graceDaysUsed = 1))
    assertTrue(isGraceExhausted(graceDaysTotal = 2, graceDaysUsed = 2))
  }

  @Test
  fun `a challenge created with no grace days starts exhausted, because it is`() {
    assertTrue(isGraceExhausted(graceDaysTotal = 0, graceDaysUsed = 0))
  }

  @Test
  fun `over-spent grace still reads as exhausted rather than flipping back to available`() {
    assertTrue(isGraceExhausted(graceDaysTotal = 2, graceDaysUsed = 3))
  }
}
