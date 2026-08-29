package com.codigitech.belay.ui.createchallenge

import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeCreationResult
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.HabitSpec
import com.codigitech.belay.data.repository.PairingRepository
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

private class FakeAuthRepository(private val userId: String? = "user-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "arun@example.com"

  override fun logOut() = Unit
}

private class FakePairingRepositoryForCreate(private val contactIds: List<String> = emptyList()) : PairingRepository {
  override suspend fun createPendingPairing(fromUserId: String) = error("not used")

  override suspend fun completePairing(pairCode: String, toUserId: String) = error("not used")

  override suspend fun getPairedContactIds(userId: String): List<String> = contactIds
}

private class FakeUserRepositoryForCreate(private val profiles: Map<String, String> = emptyMap()) : UserRepository {
  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) = error("not used")

  override suspend fun getProfile(userId: String): UserEntity? =
    profiles[userId]?.let { UserEntity(userId, it, "AAAA", "witness", "system", null, true, 0L) }

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = MutableStateFlow(null)
}

private class FakeChallengeRepository(
  private val result: ChallengeCreationResult =
    ChallengeCreationResult.Success(
      ChallengeEntity("challenge-1", "user-1", "user-2", "Morning reset", 21, 1, 0, 0, 0L, "active"),
      emptyList(),
    )
) : ChallengeRepository {
  var lastCall: List<Any?>? = null

  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ): ChallengeCreationResult {
    lastCall = listOf(challengerUserId, witnessUserId, title, habits, durationDays, graceDaysTotal)
    return result
  }

  override fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?> = MutableStateFlow(null)

  override fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>> = MutableStateFlow(emptyList())
}

class CreateChallengeViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun viewModel(
    authRepository: AuthRepository = FakeAuthRepository(),
    pairingRepository: PairingRepository = FakePairingRepositoryForCreate(),
    userRepository: UserRepository = FakeUserRepositoryForCreate(),
    challengeRepository: ChallengeRepository = FakeChallengeRepository(),
  ) = CreateChallengeViewModel(authRepository, pairingRepository, userRepository, challengeRepository)

  @Test
  fun `on start, loads paired contacts as witness options with their display names`() = runTest {
    val viewModel =
      viewModel(
        pairingRepository = FakePairingRepositoryForCreate(listOf("user-2", "user-3")),
        userRepository = FakeUserRepositoryForCreate(mapOf("user-2" to "Bala", "user-3" to "Chitra")),
      )

    assertEquals(listOf(WitnessOption("user-2", "Bala"), WitnessOption("user-3", "Chitra")), viewModel.uiState.value.witnessOptions)
  }

  @Test
  fun `starts with a single empty habit row`() {
    val viewModel = viewModel()

    assertEquals(listOf(HabitInput()), viewModel.uiState.value.habits)
  }

  @Test
  fun `addHabit appends a row up to the 5-habit cap`() {
    val viewModel = viewModel()

    repeat(6) { viewModel.addHabit() }

    assertEquals(5, viewModel.uiState.value.habits.size)
  }

  @Test
  fun `removeHabit removes the row but never leaves the list empty`() {
    val viewModel = viewModel()
    viewModel.addHabit()
    viewModel.onHabitNameChange(0, "Run")
    viewModel.onHabitNameChange(1, "Read")

    viewModel.removeHabit(0)

    assertEquals(listOf(HabitInput(name = "Read")), viewModel.uiState.value.habits)

    viewModel.removeHabit(0)

    assertEquals(listOf(HabitInput()), viewModel.uiState.value.habits)
  }

  @Test
  fun `submit rejects when no habit has a name`() = runTest {
    val viewModel = viewModel()
    viewModel.onTitleChange("Morning reset")
    viewModel.selectDuration(21)
    viewModel.selectWitness("user-2")

    viewModel.submit()

    assertFalse(viewModel.uiState.value.didCreate)
    assertEquals("Add at least one habit", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `submit rejects when no duration is picked`() = runTest {
    val viewModel = viewModel()
    viewModel.onTitleChange("Morning reset")
    viewModel.onHabitNameChange(0, "Run")
    viewModel.selectWitness("user-2")

    viewModel.submit()

    assertFalse(viewModel.uiState.value.didCreate)
    assertEquals("Pick how long", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `submit rejects when no witness is picked`() = runTest {
    val viewModel = viewModel()
    viewModel.onTitleChange("Morning reset")
    viewModel.onHabitNameChange(0, "Run")
    viewModel.selectDuration(21)

    viewModel.submit()

    assertFalse(viewModel.uiState.value.didCreate)
    assertEquals("Pick who's watching", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `submit rejects a blank title`() = runTest {
    val viewModel = viewModel()
    viewModel.onHabitNameChange(0, "Run")
    viewModel.selectDuration(21)
    viewModel.selectWitness("user-2")

    viewModel.submit()

    assertFalse(viewModel.uiState.value.didCreate)
    assertEquals("Give your challenge a name", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `submit with valid input calls createChallenge, filtering out blank habit rows, and marks done`() = runTest {
    val challengeRepository = FakeChallengeRepository()
    val viewModel = viewModel(challengeRepository = challengeRepository)
    viewModel.onTitleChange("  Morning reset  ")
    viewModel.onHabitNameChange(0, "Run 3km")
    viewModel.onHabitDetailChange(0, "before 8am")
    viewModel.addHabit()
    viewModel.onHabitNameChange(1, "  ") // left blank, should be filtered out
    viewModel.selectDuration(21)
    viewModel.selectWitness("user-2")
    viewModel.incrementGraceDays()

    viewModel.submit()

    assertTrue(viewModel.uiState.value.didCreate)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(
      listOf("user-1", "user-2", "Morning reset", listOf(HabitSpec("Run 3km", "before 8am")), 21, 2),
      challengeRepository.lastCall,
    )
  }

  @Test
  fun `submit surfaces a repository-level rejection instead of marking done`() = runTest {
    val viewModel = viewModel(challengeRepository = FakeChallengeRepository(result = ChallengeCreationResult.TooManyHabits))
    viewModel.onTitleChange("Morning reset")
    viewModel.onHabitNameChange(0, "Run")
    viewModel.selectDuration(21)
    viewModel.selectWitness("user-2")

    viewModel.submit()

    assertFalse(viewModel.uiState.value.didCreate)
    assertEquals("Up to 5 habits only", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `graceDays defaults to 1 and is clamped between 0 and 3`() {
    val viewModel = viewModel()
    assertEquals(1, viewModel.uiState.value.graceDays)

    repeat(5) { viewModel.incrementGraceDays() }
    assertEquals(3, viewModel.uiState.value.graceDays)

    repeat(10) { viewModel.decrementGraceDays() }
    assertEquals(0, viewModel.uiState.value.graceDays)
  }
}
