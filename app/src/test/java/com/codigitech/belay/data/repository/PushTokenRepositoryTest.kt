package com.codigitech.belay.data.repository

import com.codigitech.belay.data.notification.PushNotificationService
import com.codigitech.belay.testutil.RecordingErrorReporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePushNotificationService(var token: String? = "token-1", private val unreachable: Boolean = false) : PushNotificationService {
  var deleted = false

  override suspend fun currentToken(): String? {
    if (unreachable) throw PushUnavailableException()
    return token
  }

  override suspend fun deleteToken() {
    deleted = true
  }
}

private class FakePushTokenRemoteDataSource(private val unreachable: Boolean = false) :
  com.codigitech.belay.data.remote.PushTokenRemoteDataSource {
  val tokensByUser = mutableMapOf<String, MutableList<String>>()

  override suspend fun addPushToken(userId: String, token: String) {
    if (unreachable) throw PushUnavailableException()
    tokensByUser.getOrPut(userId) { mutableListOf() }.let { if (token !in it) it.add(token) }
  }

  override suspend fun removePushToken(userId: String, token: String) {
    if (unreachable) throw PushUnavailableException()
    tokensByUser[userId]?.remove(token)
  }
}

/** Stands in for an FCM/Firestore failure (no Play Services, offline, revoked token). */
private class PushUnavailableException : Exception()

class PushTokenRepositoryTest {

  private val errorReporter = RecordingErrorReporter()

  private fun repository(push: PushNotificationService, remote: FakePushTokenRemoteDataSource) =
    PushTokenRepositoryImpl(pushNotificationService = push, remoteDataSource = remote, errorReporter = errorReporter)

  @Test
  fun `registering stores this device's token against the signed-in user`() = runTest {
    val remote = FakePushTokenRemoteDataSource()

    repository(FakePushNotificationService(token = "token-1"), remote).register(userId = "user-1")

    assertEquals(listOf("token-1"), remote.tokensByUser["user-1"])
  }

  @Test
  fun `registering the same device twice does not duplicate its token`() = runTest {
    val remote = FakePushTokenRemoteDataSource()
    val repository = repository(FakePushNotificationService(token = "token-1"), remote)

    repository.register(userId = "user-1")
    repository.register(userId = "user-1")

    assertEquals(listOf("token-1"), remote.tokensByUser["user-1"])
  }

  @Test
  fun `unregistering removes the token, so the next user of this device is not pushed someone else's day`() = runTest {
    val remote = FakePushTokenRemoteDataSource()
    val push = FakePushNotificationService(token = "token-1")
    val repository = repository(push, remote)
    repository.register(userId = "user-1")

    repository.unregister(userId = "user-1")

    assertEquals(emptyList<String>(), remote.tokensByUser["user-1"])
    assertTrue("the device's own token is dropped too, not just the server copy", push.deleted)
  }

  @Test
  fun `a device with no push token available registers nothing rather than an empty token`() = runTest {
    val remote = FakePushTokenRemoteDataSource()

    repository(FakePushNotificationService(token = null), remote).register(userId = "user-1")

    assertEquals(null, remote.tokensByUser["user-1"])
  }

  @Test
  fun `a push registration failure is reported and swallowed, never surfaced as a crash`() = runTest {
    // No Play Services on the device is a real, non-crashing case — the app still works, it just
    // won't get tray notifications.
    repository(FakePushNotificationService(unreachable = true), FakePushTokenRemoteDataSource()).register(userId = "user-1")

    assertTrue(errorReporter.recorded.any { it is PushUnavailableException })
  }
}
