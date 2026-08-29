package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.dao.UserDao
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.remote.UserRemoteDataSource
import com.codigitech.belay.domain.pairing.PairCodeGenerator
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeUserDao : UserDao {
  val stored = mutableMapOf<String, MutableStateFlow<UserEntity?>>()

  override suspend fun upsert(user: UserEntity) {
    stored.getOrPut(user.userId) { MutableStateFlow(null) }.value = user
  }

  override fun observe(userId: String): Flow<UserEntity?> = stored.getOrPut(userId) { MutableStateFlow(null) }

  override suspend fun get(userId: String): UserEntity? = stored[userId]?.value
}

private class FakeUserRemoteDataSource : UserRemoteDataSource {
  val stored = mutableMapOf<String, UserEntity>()

  override suspend fun get(userId: String): UserEntity? = stored[userId]

  override suspend fun upsert(user: UserEntity) {
    stored[user.userId] = user
  }
}

class UserRepositoryTest {

  private val fixedClock = BelayClock { 5_000L }

  private fun repository(dao: UserDao, remote: UserRemoteDataSource) =
    UserRepositoryImpl(userDao = dao, remoteDataSource = remote, codeGenerator = PairCodeGenerator(Random(1)), clock = fixedClock)

  @Test
  fun `ensureProfile creates a new profile with defaults when none exists remotely`() = runTest {
    val dao = FakeUserDao()
    val remote = FakeUserRemoteDataSource()

    val user = repository(dao, remote).ensureProfile(userId = "user-1", displayName = "ana")

    assertEquals("user-1", user.userId)
    assertEquals("ana", user.displayName)
    assertEquals("challenger", user.defaultMode)
    assertEquals("system", user.themePref)
    assertTrue(user.notifAllowNudge)
    assertNotNull(user.pairCode)
    assertEquals(5_000L, user.createdAt)
    assertEquals(user, remote.stored["user-1"])
    assertEquals(user, dao.get("user-1"))
  }

  @Test
  fun `ensureProfile returns the existing remote profile unchanged and mirrors it locally`() = runTest {
    val dao = FakeUserDao()
    val remote = FakeUserRemoteDataSource()
    val existing =
      UserEntity(
        userId = "user-1",
        displayName = "ana",
        pairCode = "ZZZZ",
        defaultMode = "witness",
        themePref = "dark",
        notifDailyReminderTime = "09:00",
        notifAllowNudge = false,
        createdAt = 1L,
      )
    remote.stored["user-1"] = existing

    val user = repository(dao, remote).ensureProfile(userId = "user-1", displayName = "ignored-new-name")

    assertEquals(existing, user)
    assertEquals(existing, dao.get("user-1"))
  }

  @Test
  fun `setDefaultMode updates the mode remotely and locally`() = runTest {
    val dao = FakeUserDao()
    val remote = FakeUserRemoteDataSource()
    val repo = repository(dao, remote)
    repo.ensureProfile(userId = "user-1", displayName = "ana")

    repo.setDefaultMode(userId = "user-1", mode = "witness")

    assertEquals("witness", remote.stored["user-1"]?.defaultMode)
    assertEquals("witness", dao.get("user-1")?.defaultMode)
  }
}
