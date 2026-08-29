package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.ChallengeDao
import com.codigitech.belay.data.local.dao.HabitDao
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.remote.ChallengeRemoteDataSource
import com.codigitech.belay.data.remote.HabitRemoteDataSource
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** A habit as specified during challenge creation, before it has an id (PRD §5.2). */
data class HabitSpec(val name: String, val detail: String?, val icon: String? = null, val reminderTime: String? = null)

sealed interface ChallengeCreationResult {
  data class Success(val challenge: ChallengeEntity, val habits: List<HabitEntity>) : ChallengeCreationResult

  data object TooFewHabits : ChallengeCreationResult

  data object TooManyHabits : ChallengeCreationResult

  data object InvalidDuration : ChallengeCreationResult

  data object InvalidGraceDays : ChallengeCreationResult
}

interface ChallengeRepository {
  suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ): ChallengeCreationResult

  fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?>

  fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>>
}

class ChallengeRepositoryImpl
@Inject
constructor(
  private val challengeDao: ChallengeDao,
  private val habitDao: HabitDao,
  private val challengeRemoteDataSource: ChallengeRemoteDataSource,
  private val habitRemoteDataSource: HabitRemoteDataSource,
  private val clock: BelayClock,
  private val idGenerator: IdGenerator,
) : ChallengeRepository {

  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ): ChallengeCreationResult {
    if (habits.isEmpty()) return ChallengeCreationResult.TooFewHabits
    if (habits.size > MAX_HABITS) return ChallengeCreationResult.TooManyHabits
    if (durationDays !in VALID_DURATIONS_DAYS) return ChallengeCreationResult.InvalidDuration
    if (graceDaysTotal !in MIN_GRACE_DAYS..MAX_GRACE_DAYS) return ChallengeCreationResult.InvalidGraceDays

    // A challenger has at most one active challenge at a time — starting a new one supersedes
    // any existing one rather than leaving two rows with status='active' (which would make
    // observeActiveForChallenger's "the active challenge" ambiguous).
    challengeDao.getActiveForChallenger(challengerUserId).forEach { previouslyActive ->
      val abandoned = previouslyActive.copy(status = "abandoned")
      challengeDao.update(abandoned)
      runCatching { challengeRemoteDataSource.upsert(abandoned) }
    }

    val challengeId = idGenerator.newId()
    val startDate = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    val challenge =
      ChallengeEntity(
        challengeId = challengeId,
        challengerUserId = challengerUserId,
        witnessUserId = witnessUserId,
        title = title,
        durationDays = durationDays,
        graceDaysTotal = graceDaysTotal,
        graceDaysUsed = 0,
        perfectDays = 0,
        startDate = startDate,
        status = "active",
      )
    val habitEntities =
      habits.mapIndexed { index, spec ->
        HabitEntity(
          habitId = idGenerator.newId(),
          challengeId = challengeId,
          name = spec.name,
          detail = spec.detail,
          icon = spec.icon,
          reminderTime = spec.reminderTime,
          sortOrder = index,
          currentStreak = 0,
        )
      }

    // Best-effort remote write, same pattern as PairingRepository/UserRepository: a Firestore
    // hiccup shouldn't block challenge creation, since Room is the source of truth for "my own"
    // data and the witness's device will pick this up once connectivity returns.
    runCatching { challengeRemoteDataSource.upsert(challenge) }
    runCatching { habitRemoteDataSource.upsertAll(habitEntities) }
    challengeDao.upsert(challenge)
    habitDao.upsertAll(habitEntities)

    return ChallengeCreationResult.Success(challenge, habitEntities)
  }

  override fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?> = challengeDao.observeActiveForChallenger(userId)

  override fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>> = challengeDao.observeWitnessed(userId)

  companion object {
    const val MAX_HABITS = 5
    val VALID_DURATIONS_DAYS = setOf(7, 21, 30, 66)
    const val MIN_GRACE_DAYS = 0
    const val MAX_GRACE_DAYS = 3
  }
}
