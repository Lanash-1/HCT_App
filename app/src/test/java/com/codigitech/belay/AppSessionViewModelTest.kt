package com.codigitech.belay

import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.PushTokenRepository
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.testutil.MainDispatcherRule
import com.codigitech.belay.testutil.RecordingErrorReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepositoryForSession(private val userId: String? = "user-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "arun@example.com"

  override suspend fun logOut() = Unit

  override suspend fun deleteAccount(): com.codigitech.belay.data.repository.AccountDeletionResult = error("not used")
}

private class FakeUserRepositoryForSession(private val user: MutableStateFlow<UserEntity?>) : UserRepository {
  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) = error("not used")

  override suspend fun setThemePref(userId: String, pref: String) = error("not used")

  override suspend fun setDailyReminderTime(userId: String, time: String?) = error("not used")

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) = error("not used")

  override suspend fun getProfile(userId: String): UserEntity? = user.value

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = user
}

private class FakeSessionPushTokenRepository : PushTokenRepository {
  val registered = mutableListOf<String>()

  override suspend fun register(userId: String) {
    registered.add(userId)
  }

  override suspend fun unregister(userId: String) = Unit
}

class AppSessionViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val errorReporter = RecordingErrorReporter()
  private val pushTokenRepository = FakeSessionPushTokenRepository()

  @Test
  fun `no signed-in user defaults to system theme and challenger mode`() = runTest {
    val vm = AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(userId = null), FakeUserRepositoryForSession(MutableStateFlow(null)))

    assertEquals("system", vm.uiState.value.themePref)
    assertEquals("challenger", vm.uiState.value.mode)
  }

  @Test
  fun `reflects the signed-in user's stored theme and mode`() = runTest {
    val user = UserEntity("user-1", "Arun", "AB12", "witness", "dark", null, true, createdAt = 0L)
    val vm = AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(), FakeUserRepositoryForSession(MutableStateFlow(user)))

    assertEquals("dark", vm.uiState.value.themePref)
    assertEquals("witness", vm.uiState.value.mode)
  }

  @Test
  fun `updates when the underlying user profile changes`() = runTest {
    val userFlow = MutableStateFlow<UserEntity?>(UserEntity("user-1", "Arun", "AB12", "challenger", "light", null, true, createdAt = 0L))
    val vm = AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(), FakeUserRepositoryForSession(userFlow))
    assertEquals("challenger", vm.uiState.value.mode)

    userFlow.value = userFlow.value?.copy(defaultMode = "witness")

    assertEquals("witness", vm.uiState.value.mode)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun `tags crash reports with the signed-in user, so a report can be traced to one account`() = runTest {
    AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(userId = "user-1"), FakeUserRepositoryForSession(MutableStateFlow(null)))

    assertEquals(listOf("user-1"), errorReporter.identified)
  }

  @Test
  fun `clears the crash-report user tag when nobody is signed in`() = runTest {
    AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(userId = null), FakeUserRepositoryForSession(MutableStateFlow(null)))

    assertEquals(listOf<String?>(null), errorReporter.identified)
  }

  @Test
  fun `an already-signed-in user re-registers for push on app start`() = runTest {
    // FCM rotates tokens while the app is closed; without a re-register at start, a returning
    // user quietly stops getting cheers and nudges.
    AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(userId = "user-1"), FakeUserRepositoryForSession(MutableStateFlow(null)))

    assertEquals(listOf("user-1"), pushTokenRepository.registered)
  }

  @Test
  fun `a signed-out start registers nothing`() = runTest {
    AppSessionViewModel(errorReporter, pushTokenRepository, FakeAuthRepositoryForSession(userId = null), FakeUserRepositoryForSession(MutableStateFlow(null)))

    assertEquals(emptyList<String>(), pushTokenRepository.registered)
  }
}
