package com.codigitech.belay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengeDaoTest {
  @get:Rule val dbRule = InMemoryBelayDatabaseRule()

  private fun dao() = dbRule.db.challengeDao()

  private fun challenge(
    id: String,
    challengerUserId: String = "user-1",
    witnessUserId: String? = "user-2",
    startDate: Long = 0L,
    status: String = "active",
  ) = ChallengeEntity(
    challengeId = id,
    challengerUserId = challengerUserId,
    witnessUserId = witnessUserId,
    title = "Title",
    durationDays = 21,
    graceDaysTotal = 3,
    graceDaysUsed = 0,
    perfectDays = 0,
    startDate = startDate,
    status = status,
  )

  @Test
  fun observeActiveForChallenger_picksMostRecentActiveChallenge_ignoringOthersAndCompleted() = runTest {
    dao().upsert(challenge("old", startDate = 1L))
    dao().upsert(challenge("new", startDate = 5L))
    dao().upsert(challenge("completed", startDate = 9L, status = "completed"))
    dao().upsert(challenge("other-user", challengerUserId = "user-9", startDate = 20L))

    val result = dao().observeActiveForChallenger("user-1").first()

    assertEquals("new", result?.challengeId)
  }

  @Test
  fun observeActiveForChallenger_returnsNull_whenNoneMatch() = runTest {
    dao().upsert(challenge("completed", status = "completed"))

    assertNull(dao().observeActiveForChallenger("user-1").first())
  }

  @Test
  fun observeWitnessed_returnsOnlyActiveChallengesForThatWitness() = runTest {
    dao().upsert(challenge("mine-active", witnessUserId = "witness-1", status = "active"))
    dao().upsert(challenge("mine-completed", witnessUserId = "witness-1", status = "completed"))
    dao().upsert(challenge("not-mine", witnessUserId = "witness-2", status = "active"))

    val result = dao().observeWitnessed("witness-1").first()

    assertEquals(listOf("mine-active"), result.map { it.challengeId })
  }

  @Test
  fun getActiveForChallenger_returnsActiveRowsForThatChallengerOnly() = runTest {
    dao().upsert(challenge("active-1", startDate = 1L))
    dao().upsert(challenge("abandoned", startDate = 2L, status = "abandoned"))
    dao().upsert(challenge("other-user", challengerUserId = "user-9"))

    val result = dao().getActiveForChallenger("user-1")

    assertEquals(listOf("active-1"), result.map { it.challengeId })
  }

  @Test
  fun update_overwritesMutableFieldsWithoutChangingPrimaryKey() = runTest {
    dao().upsert(challenge("c1", startDate = 1L))

    dao().update(challenge("c1", startDate = 1L, status = "completed"))

    assertEquals("completed", dao().observe("c1").first()?.status)
  }

  @Test
  fun deletingChallenge_cascadesToItsHabits() = runTest {
    dao().upsert(challenge("c1"))
    dbRule.db.habitDao().upsertAll(
      listOf(
        HabitEntity(
          habitId = "h1",
          challengeId = "c1",
          name = "Read",
          detail = null,
          icon = null,
          reminderTime = null,
          sortOrder = 0,
          currentStreak = 0,
        )
      )
    )

    dbRule.db.openHelper.writableDatabase.execSQL("DELETE FROM challenges WHERE challengeId = 'c1'")

    val remaining = dbRule.db.habitDao().observeForChallenge("c1").first()
    assertTrue(remaining.isEmpty())
  }
}
