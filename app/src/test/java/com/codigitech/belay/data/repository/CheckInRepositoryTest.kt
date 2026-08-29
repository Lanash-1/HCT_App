package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.remote.CheckInRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

  override fun observeForHabit(habitId: String): Flow<List<CheckInEntity>> =
    MutableStateFlow(stored.values.filter { it.habitId == habitId })

  override suspend fun get(habitId: String, date: Long): CheckInEntity? =
    stored.values.firstOrNull { it.habitId == habitId && it.date == date }
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

class CheckInRepositoryTest {

  private val fixedClock = BelayClock { 100_000L }
  private var nextId = 0
  private val sequentialIds = IdGenerator { "id-${nextId++}" }

  private fun repository(
    checkInDao: CheckInDao = FakeCheckInDao(),
    checkInRemote: CheckInRemoteDataSource = FakeCheckInRemoteDataSource(),
  ) =
    CheckInRepositoryImpl(
      checkInDao = checkInDao,
      checkInRemoteDataSource = checkInRemote,
      clock = fixedClock,
      idGenerator = sequentialIds,
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
    assertEquals(result, checkInDao.stored[result.checkInId])
    assertEquals(result, checkInRemote.stored[result.checkInId])
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
  fun `check-in still succeeds locally even if the remote write fails`() = runTest {
    val checkInDao = FakeCheckInDao()

    val result =
      repository(checkInDao, FakeCheckInRemoteDataSource(unreachable = true))
        .setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    assertEquals(result, checkInDao.stored[result.checkInId])
  }

  @Test
  fun `observeForChallengeAndDate reflects the dao`() = runTest {
    val checkInDao = FakeCheckInDao()
    val repo = repository(checkInDao)
    repo.setCheckIn(habitId = "habit-1", challengeId = "challenge-1", date = 5L, done = true)

    val results = repo.observeForChallengeAndDate("challenge-1", 5L)

    assertEquals(1, (results as MutableStateFlow).value.size)
  }

  private fun assertFalseDone(done: Boolean) = assertEquals(false, done)
}
