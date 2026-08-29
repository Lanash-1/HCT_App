package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.remote.CheckInRemoteDataSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCheckInDao : CheckInDao {
  val stored = mutableMapOf<String, CheckInEntity>()

  override suspend fun upsert(checkIn: CheckInEntity) {
    stored[checkIn.checkInId] = checkIn
  }

  override fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>> =
    MutableStateFlow(stored.values.filter { it.challengeId == challengeId && it.date == date })

  override fun observeForChallenge(challengeId: String): Flow<List<CheckInEntity>> =
    MutableStateFlow(stored.values.filter { it.challengeId == challengeId }.sortedByDescending { it.date })

  override fun observeForHabit(habitId: String): Flow<List<CheckInEntity>> =
    MutableStateFlow(stored.values.filter { it.habitId == habitId })

  override suspend fun get(habitId: String, date: Long): CheckInEntity? =
    stored.values.firstOrNull { it.habitId == habitId && it.date == date }

  override suspend fun getUnsynced(): List<CheckInEntity> = stored.values.filter { !it.synced }
}

private class FakeCheckInRemoteDataSource(private val unreachable: Boolean = false) : CheckInRemoteDataSource {
  val stored = mutableMapOf<String, CheckInEntity>()

  override suspend fun upsert(checkIn: CheckInEntity) {
    if (unreachable) throw CheckInFirestoreUnavailableException()
    stored[checkIn.checkInId] = checkIn
  }
}

/** Stands in for `FirebaseFirestoreException: Failed to get document because the client is offline.` */
private class CheckInFirestoreUnavailableException : Exception()

/**
 * Stands in for what Firestore's SDK actually does when the device has no connectivity at all
 * (as opposed to a fast server-side error): the write's Task never completes — it stays queued
 * client-side until connectivity returns — so `.await()` on it suspends forever.
 */
private class HangingCheckInRemoteDataSource : CheckInRemoteDataSource {
  override suspend fun upsert(checkIn: CheckInEntity): Unit = awaitCancellation()
}

private class FakeCheckInSyncScheduler : CheckInSyncScheduler {
  var scheduleCount = 0

  override fun scheduleSync() {
    scheduleCount++
  }
}

class CheckInRepositoryTest {

  private val fixedClock = BelayClock { 100_000L }
  private var nextId = 0
  private val sequentialIds = IdGenerator { "id-${nextId++}" }

  private fun repository(
    checkInDao: CheckInDao = FakeCheckInDao(),
    checkInRemote: CheckInRemoteDataSource = FakeCheckInRemoteDataSource(),
    syncScheduler: CheckInSyncScheduler = FakeCheckInSyncScheduler(),
  ) =
    CheckInRepositoryImpl(
      checkInDao = checkInDao,
      checkInRemoteDataSource = checkInRemote,
      clock = fixedClock,
      idGenerator = sequentialIds,
      syncScheduler = syncScheduler,
    )

  @Test
  fun `checking off a habit with no prior row creates a new check-in, done and timestamped`() = runTest {
    val checkInDao = FakeCheckInDao()
    val checkInRemote = FakeCheckInRemoteDataSource()

    val result =
      repository(checkInDao, checkInRemote).setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    assertEquals("habit-1", result.habitId)
    assertEquals("challenge-1", result.challengeId)
    assertEquals(5L, result.date)
    assertTrue(result.done)
    assertEquals(100_000L, result.checkedAt)
    assertTrue(result.synced)
    assertEquals(result, checkInDao.stored[result.checkInId])
    // The remote write is sent before the outcome is known, so it never carries the local-only synced flag.
    assertEquals(result.copy(synced = false), checkInRemote.stored[result.checkInId])
  }

  @Test
  fun `unchecking an existing row reuses its id and clears the checked-at timestamp`() = runTest {
    val checkInDao = FakeCheckInDao()
    val repo = repository(checkInDao)
    val checked = repo.setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    val unchecked = repo.setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = false)

    assertEquals(checked.checkInId, unchecked.checkInId)
    assertFalseDone(unchecked.done)
    assertNull(unchecked.checkedAt)
    assertEquals(1, checkInDao.stored.size)
  }

  @Test
  fun `check-in still succeeds locally even if the remote write fails, marked unsynced`() = runTest {
    val checkInDao = FakeCheckInDao()
    val syncScheduler = FakeCheckInSyncScheduler()

    val result =
      repository(checkInDao, FakeCheckInRemoteDataSource(unreachable = true), syncScheduler)
        .setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    assertEquals(result, checkInDao.stored[result.checkInId])
    assertFalse(result.synced)
  }

  @Test
  fun `a failed remote write schedules an offline-sync retry`() = runTest {
    val syncScheduler = FakeCheckInSyncScheduler()

    repository(checkInRemote = FakeCheckInRemoteDataSource(unreachable = true), syncScheduler = syncScheduler)
      .setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    assertEquals(1, syncScheduler.scheduleCount)
  }

  @Test
  fun `a successful remote write does not schedule a retry`() = runTest {
    val syncScheduler = FakeCheckInSyncScheduler()

    repository(syncScheduler = syncScheduler).setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    assertEquals(0, syncScheduler.scheduleCount)
  }

  @Test
  fun `a remote write that never completes (no connectivity at all) still returns promptly, unsynced`() = runTest {
    val checkInDao = FakeCheckInDao()
    val syncScheduler = FakeCheckInSyncScheduler()

    // Real device-offline behavior: Firestore's write Task simply never resolves — it doesn't
    // throw. A naive runCatching{ }.await() around that would hang the "instant" check-in toggle
    // forever, which is worse than the silent-failure PRD §6.6 explicitly rules out.
    val result =
      repository(checkInDao, HangingCheckInRemoteDataSource(), syncScheduler)
        .setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    assertFalse(result.synced)
    assertEquals(result, checkInDao.stored[result.checkInId])
    assertEquals(1, syncScheduler.scheduleCount)
  }

  @Test
  fun `observeForChallengeAndDate reflects the dao`() = runTest {
    val checkInDao = FakeCheckInDao()
    val repo = repository(checkInDao)
    repo.setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    val results = repo.observeForChallengeAndDate("challenge-1", 5L)

    assertEquals(1, (results as MutableStateFlow).value.size)
  }

  @Test
  fun `observeForChallenge returns every date for the challenge, most recent first`() = runTest {
    val checkInDao = FakeCheckInDao()
    val repo = repository(checkInDao)
    repo.setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)
    repo.setCheckIn(habitId = "habit-2", challengeId = "challenge-1", date = 6L, done = true)
    repo.setCheckIn(habitId = "habit-3", challengeId = "challenge-2", date = 6L, done = true)

    val results = (repo.observeForChallenge("challenge-1") as MutableStateFlow).value

    assertEquals(listOf(6L, 5L), results.map { it.date })
  }

  private fun assertFalseDone(done: Boolean) = assertEquals(false, done)
}
