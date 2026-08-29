package com.codigitech.belay.ui.profile

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.testutil.MainDispatcherRule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepositoryForProfile(private val userId: String? = "user-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "arun@example.com"

  override fun logOut() = Unit
}

private class FakeUserRepositoryForProfile(private val profiles: Map<String, UserEntity>) : UserRepository {
  val modeUpdates = mutableListOf<Pair<String, String>>()
  val themeUpdates = mutableListOf<Pair<String, String>>()
  val reminderUpdates = mutableListOf<Pair<String, String?>>()
  val nudgeUpdates = mutableListOf<Pair<String, Boolean>>()

  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) {
    modeUpdates += userId to mode
  }

  override suspend fun setThemePref(userId: String, pref: String) {
    themeUpdates += userId to pref
  }

  override suspend fun setDailyReminderTime(userId: String, time: String?) {
    reminderUpdates += userId to time
  }

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) {
    nudgeUpdates += userId to allowed
  }

  override suspend fun getProfile(userId: String): UserEntity? = profiles[userId]

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = MutableStateFlow(profiles[userId])
}

private class FakeChallengeRepositoryForProfile(
  private val active: ChallengeEntity?,
  private val witnessed: List<ChallengeEntity> = emptyList(),
) : ChallengeRepository {
  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<com.codigitech.belay.data.repository.HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ) = error("not used")

  override fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?> = MutableStateFlow(active)

  override fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>> = MutableStateFlow(witnessed)

  override fun observeChallenge(challengeId: String) = error("not used")

  override suspend fun syncRemoteUpdates(challengeId: String) = error("not used")
}

private class FakeHabitRepositoryForProfile(private val byChallenge: Map<String, List<HabitEntity>>) : HabitRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = MutableStateFlow(byChallenge[challengeId].orEmpty())

  override suspend fun updateStreak(habit: HabitEntity, newStreak: Int) = error("not used")
}

class ProfileViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val fixedClock = BelayClock { 1_000L }

  private val createdAt = Instant.parse("2026-05-14T00:00:00Z").toEpochMilli()

  private val user =
    UserEntity(
      userId = "user-1",
      displayName = "Arun",
      pairCode = "7K42",
      defaultMode = "challenger",
      themePref = "system",
      notifDailyReminderTime = "07:00",
      notifAllowNudge = true,
      createdAt = createdAt,
    )

  private val activeChallenge =
    ChallengeEntity(
      challengeId = "challenge-1",
      challengerUserId = "user-1",
      witnessUserId = "user-2",
      title = "Morning reset",
      durationDays = 21,
      graceDaysTotal = 2,
      graceDaysUsed = 1,
      perfectDays = 3,
      startDate = 0L,
      status = "active",
    )

  private val ownHabits =
    listOf(
      HabitEntity("habit-1", "challenge-1", "Run 3km", "before 8am", null, null, 0, currentStreak = 4),
      HabitEntity("habit-2", "challenge-1", "Read", null, null, null, 1, currentStreak = 11),
    )

  private val witness = UserEntity("user-2", "Priya", "AB12", "witness", "system", null, true, createdAt = 0L)

  private fun viewModel(
    authRepository: AuthRepository = FakeAuthRepositoryForProfile(),
    userRepository: FakeUserRepositoryForProfile = FakeUserRepositoryForProfile(mapOf("user-1" to user, "user-2" to witness)),
    challengeRepository: ChallengeRepository = FakeChallengeRepositoryForProfile(activeChallenge),
    habitRepository: HabitRepository = FakeHabitRepositoryForProfile(mapOf("challenge-1" to ownHabits)),
  ) = ProfileViewModel(authRepository, userRepository, challengeRepository, habitRepository, fixedClock)

  @Test
  fun `no active challenge and nobody watched surfaces zeroed stats and no rows`() = runTest {
    val vm =
      viewModel(
        challengeRepository = FakeChallengeRepositoryForProfile(active = null),
        habitRepository = FakeHabitRepositoryForProfile(emptyMap()),
      )

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Arun", state.displayName)
    assertEquals("7K42", state.pairCode)
    assertEquals(0, state.habitCount)
    assertEquals(0, state.bestStreak)
    assertEquals(0, state.peopleWatchedCount)
    assertNull(state.witnessRow)
    assertTrue(state.watchingRows.isEmpty())
    assertNull(state.graceDaysLeft)
  }

  @Test
  fun `an active challenge surfaces habit stats, best streak, the witness row, and grace left`() = runTest {
    val vm = viewModel()

    val state = vm.uiState.value
    assertEquals(2, state.habitCount)
    assertEquals(11, state.bestStreak)
    assertEquals(1, state.graceDaysLeft) // 2 total - 1 used
    assertEquals("Priya", state.witnessRow?.title)
    assertEquals("Your witness · sees all 2 habits", state.witnessRow?.subtitle)
  }

  @Test
  fun `people witnessed surface as watching rows`() = runTest {
    val watched =
      ChallengeEntity(
        challengeId = "challenge-2",
        challengerUserId = "user-3",
        witnessUserId = "user-1",
        title = "Evening wind-down",
        durationDays = 21,
        graceDaysTotal = 1,
        graceDaysUsed = 0,
        perfectDays = 0,
        startDate = 0L,
        status = "active",
      )
    val meera = UserEntity("user-3", "Meera", "CD34", "challenger", "system", null, true, createdAt = 0L)
    val vm =
      viewModel(
        userRepository = FakeUserRepositoryForProfile(mapOf("user-1" to user, "user-2" to witness, "user-3" to meera)),
        challengeRepository = FakeChallengeRepositoryForProfile(activeChallenge, witnessed = listOf(watched)),
      )

    val state = vm.uiState.value
    assertEquals(1, state.peopleWatchedCount)
    val row = state.watchingRows.single()
    assertEquals("Meera", row.title)
    assertEquals("You're watching · Evening wind-down", row.subtitle)
  }

  @Test
  fun `joined label is derived from the account creation date`() = runTest {
    val vm = viewModel()

    val expected = "joined " + DateTimeFormatter.ofPattern("MMMM").format(Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()))
    assertEquals(expected, vm.uiState.value.joinedLabel)
  }

  @Test
  fun `setMode delegates to the user repository for the signed-in user`() = runTest {
    val userRepository = FakeUserRepositoryForProfile(mapOf("user-1" to user))
    val vm = viewModel(userRepository = userRepository)

    vm.setMode("witness")

    assertEquals(listOf("user-1" to "witness"), userRepository.modeUpdates)
  }

  @Test
  fun `setThemePref delegates to the user repository for the signed-in user`() = runTest {
    val userRepository = FakeUserRepositoryForProfile(mapOf("user-1" to user))
    val vm = viewModel(userRepository = userRepository)

    vm.setThemePref("dark")

    assertEquals(listOf("user-1" to "dark"), userRepository.themeUpdates)
  }

  @Test
  fun `setNudgeAllowed delegates to the user repository for the signed-in user`() = runTest {
    val userRepository = FakeUserRepositoryForProfile(mapOf("user-1" to user))
    val vm = viewModel(userRepository = userRepository)

    vm.setNudgeAllowed(false)

    assertEquals(listOf("user-1" to false), userRepository.nudgeUpdates)
  }

  @Test
  fun `setDailyReminderTime delegates to the user repository for the signed-in user`() = runTest {
    val userRepository = FakeUserRepositoryForProfile(mapOf("user-1" to user))
    val vm = viewModel(userRepository = userRepository)

    vm.setDailyReminderTime("08:30")

    assertEquals(listOf("user-1" to "08:30"), userRepository.reminderUpdates)
  }
}
