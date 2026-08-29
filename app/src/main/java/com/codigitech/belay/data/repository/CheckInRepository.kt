package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.remote.CheckInRemoteDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

interface CheckInRepository {
  fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>>

  /** All check-ins for a challenge across its whole run — the raw data behind the witness-detail activity log. */
  fun observeForChallenge(challengeId: String): Flow<List<CheckInEntity>>

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
  private val syncScheduler: CheckInSyncScheduler,
) : CheckInRepository {

  override fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>> =
    checkInDao.observeForChallengeAndDate(challengeId, date)

  override fun observeForChallenge(challengeId: String): Flow<List<CheckInEntity>> = checkInDao.observeForChallenge(challengeId)

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
        synced = false,
      )

    // Local-first: a Firestore hiccup must never block a check-in (PRD §6.6 offline tolerance).
    // Unlike ChallengeRepositoryImpl/PairingRepository's best-effort-and-forget pattern, a failure
    // here queues a real retry (WorkManager, gated on connectivity) instead of just dropping it.
    //
    // The timeout matters as much as the try/catch: with no connectivity at all (not just a fast
    // server error), Firestore's write Task doesn't throw — it just never completes, since the
    // SDK queues it client-side until the connection returns. Without a bound here, "instant"
    // optimistic UI would instead hang indefinitely offline.
    val remoteSucceeded = withTimeoutOrNull(REMOTE_WRITE_TIMEOUT_MILLIS) { runCatching { checkInRemoteDataSource.upsert(checkIn) } }
      ?.isSuccess == true
    val localCheckIn = if (remoteSucceeded) checkIn.copy(synced = true) else checkIn
    checkInDao.upsert(localCheckIn)
    if (!remoteSucceeded) syncScheduler.scheduleSync()

    return localCheckIn
  }

  private companion object {
    const val REMOTE_WRITE_TIMEOUT_MILLIS = 3_000L
  }
}
