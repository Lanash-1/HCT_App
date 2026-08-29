package com.codigitech.belay.ui.onboarding

import com.codigitech.belay.data.local.entity.PairingEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.PairingRepository
import com.codigitech.belay.data.repository.PairingResult
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepository(private val userId: String? = "user-1", private val email: String = "arun@example.com") :
  AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = email

  override suspend fun logOut() = Unit

  override suspend fun deleteAccount(): com.codigitech.belay.data.repository.AccountDeletionResult = error("not used")
}

private class FakeUserRepository : UserRepository {
  val ensuredFor = mutableListOf<Pair<String, String>>()
  val modeUpdates = mutableListOf<Pair<String, String>>()

  override suspend fun ensureProfile(userId: String, displayName: String): UserEntity {
    ensuredFor += userId to displayName
    return UserEntity(userId, displayName, "AAAA", "challenger", "system", null, true, 0L)
  }

  override suspend fun setDefaultMode(userId: String, mode: String) {
    modeUpdates += userId to mode
  }

  override suspend fun setThemePref(userId: String, pref: String) = error("not used")

  override suspend fun setDailyReminderTime(userId: String, time: String?) = error("not used")

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) = error("not used")

  override suspend fun getProfile(userId: String): UserEntity? = null

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = MutableStateFlow(null)
}

private class FakePairingRepository(
  private val createResult: PairingEntity = PairingEntity("pairing-1", "WXYZ", "user-1", null, "pending", 0L),
  private val completeResult: PairingResult = PairingResult.NotFound,
) : PairingRepository {
  var completedWith: Pair<String, String>? = null

  override suspend fun createPendingPairing(fromUserId: String): PairingEntity = createResult

  override suspend fun completePairing(pairCode: String, toUserId: String): PairingResult {
    completedWith = pairCode to toUserId
    return completeResult
  }

  override suspend fun getPairedContactIds(userId: String): List<String> = emptyList()
}

class OnboardingViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun viewModel(
    userRepository: UserRepository = FakeUserRepository(),
    pairingRepository: PairingRepository = FakePairingRepository(),
    authRepository: AuthRepository = FakeAuthRepository(),
  ) = OnboardingViewModel(authRepository, userRepository, pairingRepository)

  @Test
  fun `on start, ensures a user profile exists using a display name derived from the email`() = runTest {
    val userRepository = FakeUserRepository()

    viewModel(userRepository = userRepository)

    assertEquals(listOf("user-1" to "Arun"), userRepository.ensuredFor)
  }

  @Test
  fun `picking challenger persists the mode and generates a share code`() = runTest {
    val userRepository = FakeUserRepository()
    val pairingRepository = FakePairingRepository(createResult = PairingEntity("p1", "7K42", "user-1", null, "pending", 0L))
    val viewModel = viewModel(userRepository = userRepository, pairingRepository = pairingRepository)

    viewModel.pickRole(OnboardingRole.Challenger)

    assertEquals(OnboardingRole.Challenger, viewModel.uiState.value.role)
    assertEquals("7K42", viewModel.uiState.value.shareCode)
    assertEquals(listOf("user-1" to "challenger"), userRepository.modeUpdates)
  }

  @Test
  fun `picking witness persists the mode and does not generate a share code`() = runTest {
    val userRepository = FakeUserRepository()
    val viewModel = viewModel(userRepository = userRepository)

    viewModel.pickRole(OnboardingRole.Witness)

    assertEquals(OnboardingRole.Witness, viewModel.uiState.value.role)
    assertNull(viewModel.uiState.value.shareCode)
    assertEquals(listOf("user-1" to "witness"), userRepository.modeUpdates)
  }

  @Test
  fun `submitting a valid pair code marks pairing successful`() = runTest {
    val pairingRepository =
      FakePairingRepository(completeResult = PairingResult.Success(PairingEntity("p1", "WXYZ", "user-2", "user-1", "paired", 0L)))
    val viewModel = viewModel(pairingRepository = pairingRepository)
    viewModel.pickRole(OnboardingRole.Witness)
    viewModel.onPairCodeInputChange("wxyz")

    viewModel.submitPairCode()

    assertEquals("WXYZ" to "user-1", pairingRepository.completedWith)
    assertTrue(viewModel.uiState.value.pairingSuccess)
    assertNull(viewModel.uiState.value.pairingError)
  }

  @Test
  fun `submitting an unknown pair code surfaces an error`() = runTest {
    val viewModel = viewModel(pairingRepository = FakePairingRepository(completeResult = PairingResult.NotFound))
    viewModel.pickRole(OnboardingRole.Witness)
    viewModel.onPairCodeInputChange("ZZZZ")

    viewModel.submitPairCode()

    assertFalse(viewModel.uiState.value.pairingSuccess)
    assertEquals("That code isn't valid, or it's already been used.", viewModel.uiState.value.pairingError)
  }

  @Test
  fun `continuing requires a role to have been picked`() = runTest {
    val viewModel = viewModel()

    viewModel.continueOnboarding()
    assertFalse(viewModel.uiState.value.didContinue)

    viewModel.pickRole(OnboardingRole.Challenger)
    viewModel.continueOnboarding()
    assertTrue(viewModel.uiState.value.didContinue)
  }
}
