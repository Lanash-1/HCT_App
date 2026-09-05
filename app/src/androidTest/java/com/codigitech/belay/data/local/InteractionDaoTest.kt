package com.codigitech.belay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.InteractionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionDaoTest {
  @get:Rule val dbRule = InMemoryBelayDatabaseRule()

  private fun dao() = dbRule.db.interactionDao()

  @Before
  fun seedChallenge() = runTest {
    dbRule.db.challengeDao().upsert(
      ChallengeEntity(
        challengeId = "c1",
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Title",
        durationDays = 21,
        graceDaysTotal = 3,
        graceDaysUsed = 0,
        perfectDays = 0,
        startDate = 0L,
        status = "active",
      )
    )
  }

  private fun interaction(id: String, type: String, date: Long) =
    InteractionEntity(
      interactionId = id,
      challengeId = "c1",
      fromUserId = "user-2",
      type = type,
      date = date,
      message = "message",
      createdAt = 0L,
    )

  @Test
  fun nudgeCountForDate_countsOnlyNudgesOnThatDate_notCheersOrOtherDates() = runTest {
    dao().upsert(interaction("i1", type = "nudge", date = 5L))
    dao().upsert(interaction("i2", type = "nudge", date = 5L))
    dao().upsert(interaction("i3", type = "cheer", date = 5L))
    dao().upsert(interaction("i4", type = "nudge", date = 6L))

    assertEquals(2, dao().nudgeCountForDate("c1", 5L))
    assertEquals(0, dao().nudgeCountForDate("c1", 7L))
  }
}
