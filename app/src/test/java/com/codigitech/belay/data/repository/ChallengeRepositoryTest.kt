package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.testutil.RecordingErrorReporter
import com.codigitech.belay.data.local.dao.ChallengeDao
import com.codigitech.belay.data.local.dao.HabitDao
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.notification.ReminderScheduler
import com.codigitech.belay.data.remote.ChallengeRemoteDataSource
import com.codigitech.belay.data.remote.HabitRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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

private class FakeChallengeRemoteDataSource(
  private val unreachable: Boolean = false,
  private val remoteUpdates: Flow<ChallengeEntity?> = MutableStateFlow(null),
) : ChallengeRemoteDataSource {
  val stored = mutableMapOf<String, ChallengeEntity>()

  override suspend fun upsert(challenge: ChallengeEntity) {
    if (unreachable) throw ChallengeFirestoreUnavailableException()
    stored[challenge.challengeId] = challenge
  }

  override fun observe(challengeId: String): Flow<ChallengeEntity?> = remoteUpdates
}

private class FakeHabitRemoteDataSource(
  private val unreachable: Boolean = false,
  private val remoteUpdates: Flow<List<HabitEntity>> = MutableStateFlow(emptyList()),
) : HabitRemoteDataSource {
  val stored = mutableListOf<HabitEntity>()

  override suspend fun upsertAll(habits: List<HabitEntity>) {
    if (unreachable) throw ChallengeFirestoreUnavailableException()
    stored += habits
  }

  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = remoteUpdates
}

/** Stands in for `FirebaseFirestoreException: Failed to get document because the client is offline.` */
private class ChallengeFirestoreUnavailableException : Exception()

private class FakeReminderScheduler : ReminderScheduler {
  val scheduled = mutableListOf<Triple<String, String, String>>()

  override fun scheduleHabitReminder(habitId: String, habitName: String, time: String) {
    scheduled += Triple(habitId, habitName, time)
  }

  override fun cancelHabitReminder(habitId: String) {
    scheduled.removeAll { it.first == habitId }
  }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChallengeRepositoryTest {

  private val fixedClock = BelayClock { 86_400_000L } // epoch day 1, midnight UTC
  private var nextId = 0
  private val sequentialIds = IdGenerator { "id-${nextId++}" }
  private val errorReporter = RecordingErrorReporter()

  private fun repository(
    challengeDao: ChallengeDao = FakeChallengeDao(),
    habitDao: HabitDao = FakeHabitDao(),
    challengeRemote: ChallengeRemoteDataSource = FakeChallengeRemoteDataSource(),
    habitRemote: HabitRemoteDataSource = FakeHabitRemoteDataSource(),
    reminderScheduler: ReminderScheduler = FakeReminderScheduler(),
  ) =
    ChallengeRepositoryImpl(
      challengeDao = challengeDao,
      habitDao = habitDao,
      challengeRemoteDataSource = challengeRemote,
      habitRemoteDataSource = habitRemote,
      clock = fixedClock,
      idGenerator = sequentialIds,
      reminderScheduler = reminderScheduler,
      errorReporter = errorReporter,
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
  fun `creating a challenge schedules a reminder for each habit that has a reminder time, and skips those that don't`() = runTest {
    val reminderScheduler = FakeReminderScheduler()
    val habitsWithReminders =
      listOf(
        HabitSpec(name = "Run 3km", detail = null, reminderTime = "06:42"),
        HabitSpec(name = "Read", detail = null, reminderTime = null),
      )

    val result =
      repository(reminderScheduler = reminderScheduler)
        .createChallenge(
          challengerUserId = "user-1",
          witnessUserId = "user-2",
          title = "Morning reset",
          habits = habitsWithReminders,
          durationDays = 21,
          graceDaysTotal = 1,
        )

    val success = result as ChallengeCreationResult.Success
    val runHabitId = success.habits.first { it.name == "Run 3km" }.habitId
    assertEquals(listOf(Triple(runHabitId, "Run 3km", "06:42")), reminderScheduler.scheduled)
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

  @Test
  fun `syncRemoteUpdates mirrors remote challenge and habit writes into Room`() = runTest {
    val challengeDao = FakeChallengeDao()
    val habitDao = FakeHabitDao()
    val remoteChallenge =
      MutableStateFlow(
        ChallengeEntity("challenge-1", "user-1", "user-2", "Morning reset", 21, 2, 1, 3, startDate = 1L, status = "active")
      )
    val remoteHabits =
      MutableStateFlow(
        listOf(HabitEntity("h1", "challenge-1", "Run", null, null, null, 0, 5, streakBrokenAt = "2026-08-29"))
      )
    val challengeRemote = FakeChallengeRemoteDataSource(remoteUpdates = remoteChallenge)
    val habitRemote = FakeHabitRemoteDataSource(remoteUpdates = remoteHabits)

    val job = launch { repository(challengeDao, habitDao, challengeRemote, habitRemote).syncRemoteUpdates("challenge-1") }
    runCurrent()

    assertEquals(remoteChallenge.value, challengeDao.stored["challenge-1"])
    assertEquals(remoteHabits.value, habitDao.stored)
    job.cancel()
  }

  @Test
  fun `observeChallenge returns the challenge by id regardless of who's asking`() = runTest {
    val challengeDao = FakeChallengeDao()
    val challenge = ChallengeEntity("challenge-1", "user-1", "user-2", "Morning reset", 21, 1, 0, 0, startDate = 1L, status = "active")
    challengeDao.upsert(challenge)

    val result = repository(challengeDao).observeChallenge("challenge-1")

    assertEquals(challenge, (result as MutableStateFlow).value)
  }

  @Test
  fun `a swallowed remote failure is reported rather than lost`() = runTest {
    repository(
        challengeRemote = FakeChallengeRemoteDataSource(unreachable = true),
        habitRemote = FakeHabitRemoteDataSource(unreachable = true),
      )
      .createChallenge(
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "September",
        habits = validHabits,
        durationDays = 30,
        graceDaysTotal = 2,
      )

    assertTrue(errorReporter.recorded.any { it is ChallengeFirestoreUnavailableException })
  }
}
