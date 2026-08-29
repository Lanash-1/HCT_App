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
    remoteDataSource.get(userId)?.let {
      userDao.upsert(it)
      return it
    }
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
    remoteDataSource.upsert(created)
    userDao.upsert(created)
    return created
  }

  override suspend fun setDefaultMode(userId: String, mode: String) {
    val current = remoteDataSource.get(userId) ?: userDao.get(userId) ?: return
    val updated = current.copy(defaultMode = mode)
    remoteDataSource.upsert(updated)
    userDao.upsert(updated)
  }

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = userDao.observe(userId)
}
