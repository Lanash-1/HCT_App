package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.InteractionDao
import com.codigitech.belay.data.local.entity.InteractionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInteractionDao : InteractionDao {
  val stored = mutableListOf<InteractionEntity>()

  override suspend fun upsert(interaction: InteractionEntity) {
    stored += interaction
  }

  override fun observeForChallenge(challengeId: String): Flow<List<InteractionEntity>> =
    MutableStateFlow(stored.filter { it.challengeId == challengeId })

  override suspend fun nudgeCountForDate(challengeId: String, date: Long): Int =
    stored.count { it.challengeId == challengeId && it.type == "nudge" && it.date == date }
}

class InteractionRepositoryTest {

  private val fixedClock = BelayClock { 86_400_000L } // epoch day 1
  private var nextId = 0
  private val sequentialIds = IdGenerator { "interaction-${nextId++}" }

  private fun repository(dao: InteractionDao = FakeInteractionDao()) =
    InteractionRepositoryImpl(interactionDao = dao, clock = fixedClock, idGenerator = sequentialIds)

  @Test
  fun `sending a cheer with a valid message persists it`() = runTest {
    val dao = FakeInteractionDao()

    val result = repository(dao).sendCheer(challengeId = "c1", fromUserId = "witness-1", message = "Nice work!")

    assertTrue(result is CheerOrNudgeResult.Success)
    val interaction = (result as CheerOrNudgeResult.Success).interaction
    assertEquals("cheer", interaction.type)
    assertEquals("Nice work!", interaction.message)
    assertEquals(dao.stored.single(), interaction)
  }

  @Test
  fun `a blank message is rejected for cheer or nudge`() = runTest {
    assertEquals(CheerOrNudgeResult.MessageBlank, repository().sendCheer("c1", "witness-1", "   "))
    assertEquals(CheerOrNudgeResult.MessageBlank, repository().sendNudge("c1", "witness-1", ""))
  }

  @Test
  fun `a message over 140 characters is rejected`() = runTest {
    val tooLong = "x".repeat(141)

    assertEquals(CheerOrNudgeResult.MessageTooLong, repository().sendCheer("c1", "witness-1", tooLong))
  }

  @Test
  fun `a message of exactly 140 characters is accepted`() = runTest {
    val exactly140 = "x".repeat(140)

    val result = repository().sendCheer("c1", "witness-1", exactly140)

    assertTrue(result is CheerOrNudgeResult.Success)
  }

  @Test
  fun `the first nudge of the day for a challenge succeeds`() = runTest {
    val result = repository().sendNudge(challengeId = "c1", fromUserId = "witness-1", message = "Don't forget!")

    assertTrue(result is CheerOrNudgeResult.Success)
    assertEquals("nudge", (result as CheerOrNudgeResult.Success).interaction.type)
  }

  @Test
  fun `a second nudge for the same challenge on the same day is rejected`() = runTest {
    val dao = FakeInteractionDao()
    repository(dao).sendNudge(challengeId = "c1", fromUserId = "witness-1", message = "First")

    val second = repository(dao).sendNudge(challengeId = "c1", fromUserId = "witness-1", message = "Second")

    assertEquals(CheerOrNudgeResult.AlreadyNudgedToday, second)
    assertEquals(1, dao.stored.size)
  }

  @Test
  fun `cheer is not subject to the once-per-day limit`() = runTest {
    val dao = FakeInteractionDao()
    repository(dao).sendCheer(challengeId = "c1", fromUserId = "witness-1", message = "First")

    val second = repository(dao).sendCheer(challengeId = "c1", fromUserId = "witness-1", message = "Second")

    assertTrue(second is CheerOrNudgeResult.Success)
    assertEquals(2, dao.stored.size)
  }
}
