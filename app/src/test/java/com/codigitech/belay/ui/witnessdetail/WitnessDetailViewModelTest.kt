package com.codigitech.belay.ui.witnessdetail

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.InteractionEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.CheckInRepository
import com.codigitech.belay.data.repository.CheerOrNudgeResult
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.InteractionRepository
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ONE_DAY_MILLIS = 86_400_000L

private class FakeAuthRepositoryForDetail(private val userId: String? = "witness-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "priya@example.com"

  override suspend fun logOut() = Unit

  override suspend fun deleteAccount(): com.codigitech.belay.data.repository.AccountDeletionResult = error("not used")
}

private class FakeChallengeRepositoryForDetail(private val challenge: ChallengeEntity?) : ChallengeRepository {
  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<com.codigitech.belay.data.repository.HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ) = error("not used")

  override fun observeActiveForChallenger(userId: String) = error("not used")

  override fun observeWitnessed(userId: String) = error("not used")

  override fun observeChallenge(challengeId: String): Flow<ChallengeEntity?> = MutableStateFlow(challenge)

  override suspend fun attachWitnessIfMissing(challengerUserId: String, witnessUserId: String) = Unit


  override suspend fun syncRemoteUpdates(challengeId: String) = error("not used")
}

private class FakeHabitRepositoryForDetail(private val habits: List<HabitEntity>) : HabitRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = MutableStateFlow(habits)

  override suspend fun updateStreak(habit: HabitEntity, newStreak: Int) = error("not used")
}

private class FakeCheckInRepositoryForDetail(
  private val today: List<CheckInEntity> = emptyList(),
  private val all: List<CheckInEntity> = today,
) : CheckInRepository {
  override fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>> =
    MutableStateFlow(today.filter { it.date == date })

  override fun observeForChallenge(challengeId: String): Flow<List<CheckInEntity>> = MutableStateFlow(all.sortedByDescending { it.date })

  override suspend fun setCheckIn(habitId: String, challengeId: String, date: Long, done: Boolean) = error("not used")
}

private class FakeUserRepositoryForDetail(private val profiles: Map<String, UserEntity>) : UserRepository {
  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) = error("not used")

  override suspend fun setThemePref(userId: String, pref: String) = error("not used")

  override suspend fun setDailyReminderTime(userId: String, time: String?) = error("not used")

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) = error("not used")

  override suspend fun touchLastSeen(userId: String) = Unit


  override suspend fun getProfile(userId: String): UserEntity? = profiles[userId]

  override fun observeLocalUser(userId: String) = error("not used")
}

private class FakeInteractionRepositoryForDetail : InteractionRepository {
  val cheerCalls = mutableListOf<List<String>>()
  val nudgeCalls = mutableListOf<List<String>>()

  override suspend fun sendCheer(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult {
    cheerCalls += listOf(challengeId, fromUserId, message)
    return CheerOrNudgeResult.Success(
      InteractionEntity("i1", challengeId, fromUserId, "cheer", date = 0L, message = message, createdAt = 0L)
    )
  }

  override suspend fun sendNudge(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult {
    nudgeCalls += listOf(challengeId, fromUserId, message)
    return CheerOrNudgeResult.Success(
      InteractionEntity("i2", challengeId, fromUserId, "nudge", date = 0L, message = message, createdAt = 0L)
    )
  }

  override fun observeForChallenge(challengeId: String) = error("not used")
}

class WitnessDetailViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val fixedClock = BelayClock { 5 * ONE_DAY_MILLIS } // epoch day 5

  private val challenge =
    ChallengeEntity(
      challengeId = "challenge-1",
      challengerUserId = "arun",
      witnessUserId = "witness-1",
      title = "Morning reset",
      durationDays = 20,
      graceDaysTotal = 2,
      graceDaysUsed = 1,
      perfectDays = 8,
      startDate = 0L, // day 5 -> day 6 of the challenge, 15 days to go
      status = "active",
    )

  private val habits =
    listOf(
      HabitEntity("habit-1", "challenge-1", "Run 3km", "before 8am", null, null, 0, currentStreak = 4),
      HabitEntity("habit-2", "challenge-1", "Read", null, null, null, 1, currentStreak = 2),
    )

  private fun viewModel(
    authRepository: AuthRepository = FakeAuthRepositoryForDetail(),
    challengeRepository: ChallengeRepository = FakeChallengeRepositoryForDetail(challenge),
    habitRepository: HabitRepository = FakeHabitRepositoryForDetail(habits),
    checkInRepository: CheckInRepository = FakeCheckInRepositoryForDetail(),
    userRepository: UserRepository = FakeUserRepositoryForDetail(mapOf("arun" to user("arun", "Arun"))),
    interactionRepository: InteractionRepository = FakeInteractionRepositoryForDetail(),
  ) =
    WitnessDetailViewModel(
      authRepository,
      challengeRepository,
      habitRepository,
      checkInRepository,
      userRepository,
      interactionRepository,
      fixedClock,
    ).also { it.load("challenge-1") }

  private fun user(id: String, name: String) = UserEntity(id, name, "AB12", "witness", "system", null, true, createdAt = 0L)

  @Test
  fun `loading with nothing checked yet surfaces the incomplete headline and no progress`() = runTest {
    val vm = viewModel()

    val state = vm.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Arun", state.challengerName)
    assertEquals("Morning reset", state.challengeTitle)
    assertEquals(6, state.dayNo) // day 5 - startDate 0 + 1
    assertEquals(20, state.totalDays)
    assertFalse(state.allDone)
    assertEquals("Nothing yet.", state.headline)
    assertEquals(8, state.perfectDays)
    assertEquals(1, state.graceDaysLeft) // 2 total - 1 used
    assertEquals(
      listOf("Run 3km" to false, "Read" to false),
      state.habits.map { it.name to it.checkedToday },
    )
  }

  @Test
  fun `some habits checked reads the partial headline and is not all done`() = runTest {
    val checkIns = listOf(CheckInEntity("c1", "habit-1", "challenge-1", date = 5L, done = true, checkedAt = 0L, clientIdempotencyKey = "c1"))
    val vm = viewModel(checkInRepository = FakeCheckInRepositoryForDetail(checkIns))

    val state = vm.uiState.value
    assertFalse(state.allDone)
    assertEquals("Arun is 1 of 2.", state.headline)
  }

  @Test
  fun `every habit checked reads the complete headline and is all done`() = runTest {
    val checkIns =
      listOf(
        CheckInEntity("c1", "habit-1", "challenge-1", date = 5L, done = true, checkedAt = 0L, clientIdempotencyKey = "c1"),
        CheckInEntity("c2", "habit-2", "challenge-1", date = 5L, done = true, checkedAt = 0L, clientIdempotencyKey = "c2"),
      )
    val vm = viewModel(checkInRepository = FakeCheckInRepositoryForDetail(checkIns))

    val state = vm.uiState.value
    assertTrue(state.allDone)
    assertEquals("Arun finished the day.", state.headline)
  }

  @Test
  fun `progress reflects days elapsed in the challenge`() = runTest {
    val vm = viewModel()

    // day 5 is the 6th day of a 20-day challenge starting at day 0 -> 6/20 = 30%
    assertEquals(30, vm.uiState.value.progressPercent)
  }

  @Test
  fun `the activity log tallies check-ins per day, most recent first`() = runTest {
    val allCheckIns =
      listOf(
        CheckInEntity("c1", "habit-1", "challenge-1", date = 4L, done = true, checkedAt = 0L, clientIdempotencyKey = "c1"),
        CheckInEntity("c2", "habit-2", "challenge-1", date = 4L, done = true, checkedAt = 0L, clientIdempotencyKey = "c2"),
        CheckInEntity("c3", "habit-1", "challenge-1", date = 3L, done = true, checkedAt = 0L, clientIdempotencyKey = "c3"),
      )
    val vm = viewModel(checkInRepository = FakeCheckInRepositoryForDetail(today = emptyList(), all = allCheckIns))

    val log = vm.uiState.value.log
    assertEquals(listOf(4L, 3L), log.map { it.date })
    assertEquals("2/2", log[0].score)
    assertEquals(WitnessDetailCopy.PERFECT_DAY, log[0].detail)
    assertEquals("1/2", log[1].score)
    assertEquals(WitnessDetailCopy.PARTIAL_DAY, log[1].detail)
  }

  @Test
  fun `sending a cheer delegates to the interaction repository as the signed-in witness`() = runTest {
    val interactions = FakeInteractionRepositoryForDetail()
    val vm = viewModel(interactionRepository = interactions)

    val result = vm.sendCheer("Nice work!")

    assertTrue(result is CheerOrNudgeResult.Success)
    assertEquals(listOf("challenge-1", "witness-1", "Nice work!"), interactions.cheerCalls.single())
  }

  @Test
  fun `sending a nudge delegates to the interaction repository as the signed-in witness`() = runTest {
    val interactions = FakeInteractionRepositoryForDetail()
    val vm = viewModel(interactionRepository = interactions)

    val result = vm.sendNudge("Don't forget!")

    assertTrue(result is CheerOrNudgeResult.Success)
    assertEquals(listOf("challenge-1", "witness-1", "Don't forget!"), interactions.nudgeCalls.single())
  }

  @Test
  fun `a finished challenge reads as finished from the witness side too`() = runTest {
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForDetail(challenge.copy(durationDays = 1)))

    assertTrue(vm.uiState.value.hasEnded)
  }

  @Test
  fun `a running challenge does not read as finished`() = runTest {
    assertFalse(viewModel().uiState.value.hasEnded)
  }
}
