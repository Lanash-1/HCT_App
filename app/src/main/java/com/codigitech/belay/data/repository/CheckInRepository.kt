package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.remote.CheckInRemoteDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface CheckInRepository {
  fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>>

  /** Toggles a habit's check-in for a given day (PRD §5.3 Today screen). */
  suspend fun setCheckIn(habitId: String, challengeId: String, date: Long, done: Boolean): CheckInEntity
}

class CheckInRepositoryImpl
@Inject
constructor(
  private val checkInDao: CheckInDao,
  private val checkInRemoteDataSource: CheckInRemoteDataSource,
  private val clock: BelayClock,
  private val idGenerator: IdGenerator,
) : CheckInRepository {

  override fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>> =
    checkInDao.observeForChallengeAndDate(challengeId, date)

  override suspend fun setCheckIn(habitId: String, challengeId: String, date: Long, done: Boolean): CheckInEntity {
    val existing = checkInDao.get(habitId, date)
    val checkInId = existing?.checkInId ?: idGenerator.newId()
    val checkIn =
      CheckInEntity(
        checkInId = checkInId,
        habitId = habitId,
        challengeId = challengeId,
        date = date,
        done = done,
        checkedAt = if (done) clock.nowEpochMillis() else null,
        clientIdempotencyKey = existing?.clientIdempotencyKey ?: checkInId,
      )

    // Local-first, same best-effort remote pattern as ChallengeRepositoryImpl/PairingRepository:
    // a Firestore hiccup must never block a check-in (PRD §6.6 offline tolerance).
    checkInDao.upsert(checkIn)
    runCatching { checkInRemoteDataSource.upsert(checkIn) }

    return checkIn
  }
}
