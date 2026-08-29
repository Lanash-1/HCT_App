package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.InteractionDao
import com.codigitech.belay.data.local.entity.InteractionEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

sealed interface CheerOrNudgeResult {
  data class Success(val interaction: InteractionEntity) : CheerOrNudgeResult

  data object MessageBlank : CheerOrNudgeResult

  data object MessageTooLong : CheerOrNudgeResult

  /** Client-side check for immediate UX feedback; the Catalyst Function re-enforces this server-side. */
  data object AlreadyNudgedToday : CheerOrNudgeResult
}

interface InteractionRepository {
  suspend fun sendCheer(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult

  suspend fun sendNudge(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult

  fun observeForChallenge(challengeId: String): Flow<List<InteractionEntity>>
}

class InteractionRepositoryImpl
@Inject
constructor(
  private val interactionDao: InteractionDao,
  private val clock: BelayClock,
  private val idGenerator: IdGenerator,
) : InteractionRepository {

  override suspend fun sendCheer(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult =
    send(challengeId, fromUserId, message, type = "cheer")

  override suspend fun sendNudge(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult {
    val today = todayEpochDay()
    if (interactionDao.nudgeCountForDate(challengeId, today) > 0) return CheerOrNudgeResult.AlreadyNudgedToday
    return send(challengeId, fromUserId, message, type = "nudge", today = today)
  }

  override fun observeForChallenge(challengeId: String): Flow<List<InteractionEntity>> =
    interactionDao.observeForChallenge(challengeId)

  private suspend fun send(
    challengeId: String,
    fromUserId: String,
    message: String,
    type: String,
    today: Long = todayEpochDay(),
  ): CheerOrNudgeResult {
    if (message.isBlank()) return CheerOrNudgeResult.MessageBlank
    if (message.length > MAX_MESSAGE_LENGTH) return CheerOrNudgeResult.MessageTooLong

    val interaction =
      InteractionEntity(
        interactionId = idGenerator.newId(),
        challengeId = challengeId,
        fromUserId = fromUserId,
        type = type,
        date = today,
        message = message,
        createdAt = clock.nowEpochMillis(),
      )
    interactionDao.upsert(interaction)
    return CheerOrNudgeResult.Success(interaction)
  }

  private fun todayEpochDay(): Long =
    Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  companion object {
    const val MAX_MESSAGE_LENGTH = 140
  }
}
