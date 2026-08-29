package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.ChallengeDao
import com.codigitech.belay.data.local.dao.HabitDao
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.remote.ChallengeRemoteDataSource
import com.codigitech.belay.data.remote.HabitRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeChallengeDao : ChallengeDao {
  val stored = mutableMapOf<String, ChallengeEntity>()

  override suspend fun upsert(challenge: ChallengeEntity) {
    stored[challenge.challengeId] = challenge
  }

  override suspend fun update(challenge: ChallengeEntity) {
    stored[challenge.challengeId] = challenge
  }

  override fun observe(challengeId: String): Flow<ChallengeEntity?> = MutableStateFlow(stored[challengeId])

  override fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?> =
    MutableStateFlow(
      stored.values.filter { it.challengerUserId == userId && it.status == "active" }.maxByOrNull { it.startDate }
    )

  override fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>> =
    MutableStateFlow(stored.values.filter { it.witnessUserId == userId && it.status == "active" })

  override suspend fun getActiveForChallenger(userId: String): List<ChallengeEntity> =
    stored.values.filter { it.challengerUserId == userId && it.status == "active" }
}

private class FakeHabitDao : HabitDao {
  val stored = mutableListOf<HabitEntity>()

  override suspend fun upsertAll(habits: List<HabitEntity>) {
    stored += habits
  }

  override suspend fun update(habit: HabitEntity) {
    val index = stored.indexOfFirst { it.habitId == habit.habitId }
    if (index >= 0) stored[index] = habit
  }

  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> =
    MutableStateFlow(stored.filter { it.challengeId == challengeId }.sortedBy { it.sortOrder })
}

private class FakeChallengeRemoteDataSource(private val unreachable: Boolean = false) : ChallengeRemoteDataSource {
  val stored = mutableMapOf<String, ChallengeEntity>()

  override suspend fun upsert(challenge: ChallengeEntity) {
    if (unreachable) throw ChallengeFirestoreUnavailableException()
    stored[challenge.challengeId] = challenge
  }
}

private class FakeHabitRemoteDataSource(private val unreachable: Boolean = false) : HabitRemoteDataSource {
  val stored = mutableListOf<HabitEntity>()

  override suspend fun upsertAll(habits: List<HabitEntity>) {
    if (unreachable) throw ChallengeFirestoreUnavailableException()
    stored += habits
  }
}

/** Stands in for `FirebaseFirestoreException: Failed to get document because the client is offline.` */
private class ChallengeFirestoreUnavailableException : Exception()

class ChallengeRepositoryTest {

  private val fixedClock = BelayClock { 86_400_000L } // epoch day 1, midnight UTC
  private var nextId = 0
  private val sequentialIds = IdGenerator { "id-${nextId++}" }

  private fun repository(
    challengeDao: ChallengeDao = FakeChallengeDao(),
    habitDao: HabitDao = FakeHabitDao(),
    challengeRemote: ChallengeRemoteDataSource = FakeChallengeRemoteDataSource(),
    habitRemote: HabitRemoteDataSource = FakeHabitRemoteDataSource(),
  ) =
    ChallengeRepositoryImpl(
      challengeDao = challengeDao,
      habitDao = habitDao,
      challengeRemoteDataSource = challengeRemote,
      habitRemoteDataSource = habitRemote,
      clock = fixedClock,
      idGenerator = sequentialIds,
    )

  private val validHabits = listOf(HabitSpec(name = "Run 3km", detail = "before 8am"), HabitSpec(name = "Read", detail = null))

  @Test
  fun `creating a challenge with valid input persists the challenge and its habits, remotely and locally`() = runTest {
    val challengeDao = FakeChallengeDao()
    val habitDao = FakeHabitDao()
    val challengeRemote = FakeChallengeRemoteDataSource()
    val habitRemote = FakeHabitRemoteDataSource()

    val result =
      repository(challengeDao, habitDao, challengeRemote, habitRemote)
        .createChallenge(
          challengerUserId = "user-1",
          witnessUserId = "user-2",
          title = "Morning reset",
          habits = validHabits,
          durationDays = 21,
          graceDaysTotal = 2,
        )

    assertTrue(result is ChallengeCreationResult.Success)
    val success = result as ChallengeCreationResult.Success
    assertEquals("active", success.challenge.status)
    assertEquals(0, success.challenge.graceDaysUsed)
    assertEquals(2, success.habits.size)
    assertEquals(listOf(0, 1), success.habits.map { it.sortOrder })
    assertTrue(success.habits.all { it.challengeId == success.challenge.challengeId })
    assertEquals(success.challenge, challengeDao.stored[success.challenge.challengeId])
    assertEquals(success.habits, habitDao.stored)
    assertEquals(success.challenge, challengeRemote.stored[success.challenge.challengeId])
    assertEquals(success.habits, habitRemote.stored)
  }

  @Test
  fun `creating a challenge still succeeds locally even if the remote writes fail`() = runTest {
    val challengeDao = FakeChallengeDao()
    val habitDao = FakeHabitDao()

    val result =
      repository(
          challengeDao,
          habitDao,
          challengeRemote = FakeChallengeRemoteDataSource(unreachable = true),
          habitRemote = FakeHabitRemoteDataSource(unreachable = true),
        )
        .createChallenge(
          challengerUserId = "user-1",
          witnessUserId = "user-2",
          title = "Morning reset",
          habits = validHabits,
          durationDays = 21,
          graceDaysTotal = 2,
        )

    assertTrue(result is ChallengeCreationResult.Success)
    val success = result as ChallengeCreationResult.Success
    assertEquals(success.challenge, challengeDao.stored[success.challenge.challengeId])
    assertEquals(success.habits, habitDao.stored)
  }

  @Test
  fun `rejects a challenge with zero habits`() = runTest {
    val result =
      repository().createChallenge(
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Empty",
        habits = emptyList(),
        durationDays = 21,
        graceDaysTotal = 1,
      )

    assertEquals(ChallengeCreationResult.TooFewHabits, result)
  }

  @Test
  fun `rejects a challenge with more than 5 habits`() = runTest {
    val sixHabits = (1..6).map { HabitSpec(name = "Habit $it", detail = null) }

    val result =
      repository().createChallenge(
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Too many",
        habits = sixHabits,
        durationDays = 21,
        graceDaysTotal = 1,
      )

    assertEquals(ChallengeCreationResult.TooManyHabits, result)
  }

  @Test
  fun `rejects a duration that isn't one of the supported lengths`() = runTest {
    val result =
      repository().createChallenge(
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Bad duration",
        habits = validHabits,
        durationDays = 10,
        graceDaysTotal = 1,
      )

    assertEquals(ChallengeCreationResult.InvalidDuration, result)
  }

  @Test
  fun `rejects grace days outside 0 to 3`() = runTest {
    val tooMany =
      repository().createChallenge(
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Bad grace",
        habits = validHabits,
        durationDays = 7,
        graceDaysTotal = 4,
      )
    val negative =
      repository().createChallenge(
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Bad grace",
        habits = validHabits,
        durationDays = 7,
        graceDaysTotal = -1,
      )

    assertEquals(ChallengeCreationResult.InvalidGraceDays, tooMany)
    assertEquals(ChallengeCreationResult.InvalidGraceDays, negative)
  }

  @Test
  fun `observeActiveForChallenger returns the most recently started challenge when more than one is active`() = runTest {
    val challengeDao = FakeChallengeDao()
    val older = ChallengeEntity("challenge-old", "user-1", "user-2", "Old", 21, 1, 0, 0, startDate = 1L, status = "active")
    val newer = ChallengeEntity("challenge-new", "user-1", "user-2", "New", 21, 1, 0, 0, startDate = 5L, status = "active")
    challengeDao.upsert(older)
    challengeDao.upsert(newer)

    val active = repository(challengeDao).observeActiveForChallenger("user-1")

    assertEquals(newer, (active as MutableStateFlow).value)
  }

  @Test
  fun `creating a challenge abandons the challenger's existing active challenge, remotely and locally`() = runTest {
    val challengeDao = FakeChallengeDao()
    val habitDao = FakeHabitDao()
    val challengeRemote = FakeChallengeRemoteDataSource()
    val habitRemote = FakeHabitRemoteDataSource()
    val existing = ChallengeEntity("challenge-old", "user-1", "user-2", "Old", 21, 1, 0, 0, startDate = 1L, status = "active")
    challengeDao.upsert(existing)
    challengeRemote.stored[existing.challengeId] = existing

    val result =
      repository(challengeDao, habitDao, challengeRemote, habitRemote)
        .createChallenge(
          challengerUserId = "user-1",
          witnessUserId = "user-2",
          title = "New challenge",
          habits = validHabits,
          durationDays = 21,
          graceDaysTotal = 1,
        )

    assertTrue(result is ChallengeCreationResult.Success)
    assertEquals("abandoned", challengeDao.stored["challenge-old"]?.status)
    assertEquals("abandoned", challengeRemote.stored["challenge-old"]?.status)
    assertEquals("active", challengeDao.stored[(result as ChallengeCreationResult.Success).challenge.challengeId]?.status)
  }

  @Test
  fun `abandoning the old challenge still succeeds locally even if the remote write fails`() = runTest {
    val challengeDao = FakeChallengeDao()
    val existing = ChallengeEntity("challenge-old", "user-1", "user-2", "Old", 21, 1, 0, 0, startDate = 1L, status = "active")
    challengeDao.upsert(existing)

    val result =
      repository(
          challengeDao,
          challengeRemote = FakeChallengeRemoteDataSource(unreachable = true),
          habitRemote = FakeHabitRemoteDataSource(unreachable = true),
        )
        .createChallenge(
          challengerUserId = "user-1",
          witnessUserId = "user-2",
          title = "New challenge",
          habits = validHabits,
          durationDays = 21,
          graceDaysTotal = 1,
        )

    assertTrue(result is ChallengeCreationResult.Success)
    assertEquals("abandoned", challengeDao.stored["challenge-old"]?.status)
  }
}
