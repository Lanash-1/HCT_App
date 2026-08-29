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

private class FakeUserRemoteDataSource(private val unreachable: Boolean = false) : UserRemoteDataSource {
  val stored = mutableMapOf<String, UserEntity>()

  override suspend fun get(userId: String): UserEntity? {
    if (unreachable) throw FirestoreUnavailableException()
    return stored[userId]
  }

  override suspend fun upsert(user: UserEntity) {
    if (unreachable) throw FirestoreUnavailableException()
    stored[user.userId] = user
  }
}

/** Stands in for `FirebaseFirestoreException: Failed to get document because the client is offline.` */
private class FirestoreUnavailableException : Exception()

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

  @Test
  fun `ensureProfile still creates a usable local profile when Firestore is unreachable (first launch, offline)`() = runTest {
    val dao = FakeUserDao()
    val remote = FakeUserRemoteDataSource(unreachable = true)

    val user = repository(dao, remote).ensureProfile(userId = "user-1", displayName = "ana")

    assertEquals("user-1", user.userId)
    assertEquals("ana", user.displayName)
    assertEquals(user, dao.get("user-1"))
  }

  @Test
  fun `ensureProfile falls back to the local cache when Firestore is unreachable but a profile already exists`() = runTest {
    val dao = FakeUserDao()
    val existing = UserEntity("user-1", "ana", "AAAA", "witness", "dark", null, false, 1L)
    dao.upsert(existing)
    val remote = FakeUserRemoteDataSource(unreachable = true)

    val user = repository(dao, remote).ensureProfile(userId = "user-1", displayName = "ignored-new-name")

    assertEquals(existing, user)
  }

  @Test
  fun `setDefaultMode still updates the local cache when Firestore is unreachable`() = runTest {
    val dao = FakeUserDao()
    dao.upsert(UserEntity("user-1", "ana", "AAAA", "challenger", "system", null, true, 1L))
    val remote = FakeUserRemoteDataSource(unreachable = true)

    repository(dao, remote).setDefaultMode(userId = "user-1", mode = "witness")

    assertEquals("witness", dao.get("user-1")?.defaultMode)
  }
}
