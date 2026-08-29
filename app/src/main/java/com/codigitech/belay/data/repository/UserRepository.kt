package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.dao.UserDao
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.remote.UserRemoteDataSource
import com.codigitech.belay.domain.pairing.PairCodeGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface UserRepository {
  /** Fetches the signed-in user's profile, creating one with sensible defaults if this is their first time. */
  suspend fun ensureProfile(userId: String, displayName: String): UserEntity

  suspend fun setDefaultMode(userId: String, mode: String)

  /** Looks up any user's profile (e.g. a paired contact's display name) — does not create one if missing. */
  suspend fun getProfile(userId: String): UserEntity?

  fun observeLocalUser(userId: String): Flow<UserEntity?>
}

class UserRepositoryImpl
@Inject
constructor(
  private val userDao: UserDao,
  private val remoteDataSource: UserRemoteDataSource,
  private val codeGenerator: PairCodeGenerator,
  private val clock: BelayClock,
) : UserRepository {

  override suspend fun ensureProfile(userId: String, displayName: String): UserEntity {
    // Firestore reads/writes can fail (offline, a flaky connection right at app start) — Room is
    // the offline-tolerant mirror, so a remote hiccup should never crash onboarding.
    runCatching { remoteDataSource.get(userId) }.getOrNull()?.let {
      userDao.upsert(it)
      return it
    }
    userDao.get(userId)?.let { return it }
    val created =
      UserEntity(
        userId = userId,
        displayName = displayName,
        pairCode = codeGenerator.generate(),
        defaultMode = "challenger",
        themePref = "system",
        notifDailyReminderTime = null,
        notifAllowNudge = true,
        createdAt = clock.nowEpochMillis(),
      )
    runCatching { remoteDataSource.upsert(created) }
    userDao.upsert(created)
    return created
  }

  override suspend fun setDefaultMode(userId: String, mode: String) {
    val current = runCatching { remoteDataSource.get(userId) }.getOrNull() ?: userDao.get(userId) ?: return
    val updated = current.copy(defaultMode = mode)
    runCatching { remoteDataSource.upsert(updated) }
    userDao.upsert(updated)
  }

  override suspend fun getProfile(userId: String): UserEntity? =
    runCatching { remoteDataSource.get(userId) }.getOrNull()?.also { userDao.upsert(it) } ?: userDao.get(userId)

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = userDao.observe(userId)
}
