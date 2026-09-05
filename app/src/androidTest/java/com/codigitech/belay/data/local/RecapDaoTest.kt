package com.codigitech.belay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.RecapEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecapDaoTest {
  @get:Rule val dbRule = InMemoryBelayDatabaseRule()

  private fun dao() = dbRule.db.recapDao()

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

  private fun recap(id: String, weekStart: Long) =
    RecapEntity(
      recapId = id,
      challengeId = "c1",
      weekStart = weekStart,
      weekEnd = weekStart + 6,
      checkInsTotal = 5,
      checkInsPossible = 7,
      perfectDays = 3,
      perHabitSummaryJson = "{}",
      generatedAt = 0L,
    )

  @Test
  fun observeForChallenge_ordersByWeekStartDescending_mostRecentWeekFirst() = runTest {
    dao().upsert(recap("week-1", weekStart = 0L))
    dao().upsert(recap("week-3", weekStart = 14L))
    dao().upsert(recap("week-2", weekStart = 7L))

    val result = dao().observeForChallenge("c1").first()

    assertEquals(listOf("week-3", "week-2", "week-1"), result.map { it.recapId })
  }
}
