package com.codigitech.belay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDaoTest {
  @get:Rule val dbRule = InMemoryBelayDatabaseRule()

  private fun dao() = dbRule.db.habitDao()

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

  private fun habit(id: String, sortOrder: Int, currentStreak: Int = 0) =
    HabitEntity(
      habitId = id,
      challengeId = "c1",
      name = "Habit $id",
      detail = null,
      icon = null,
      reminderTime = null,
      sortOrder = sortOrder,
      currentStreak = currentStreak,
    )

  @Test
  fun observeForChallenge_ordersBySortOrder_notInsertionOrder() = runTest {
    dao().upsertAll(listOf(habit("h-last", sortOrder = 2), habit("h-first", sortOrder = 0), habit("h-mid", sortOrder = 1)))

    val result = dao().observeForChallenge("c1").first()

    assertEquals(listOf("h-first", "h-mid", "h-last"), result.map { it.habitId })
  }

  @Test
  fun update_overwritesStreakWithoutTouchingOtherHabits() = runTest {
    dao().upsertAll(listOf(habit("h1", sortOrder = 0, currentStreak = 3), habit("h2", sortOrder = 1, currentStreak = 5)))

    dao().update(habit("h1", sortOrder = 0, currentStreak = 4))

    val result = dao().observeForChallenge("c1").first().associateBy { it.habitId }
    assertEquals(4, result.getValue("h1").currentStreak)
    assertEquals(5, result.getValue("h2").currentStreak)
  }
}
