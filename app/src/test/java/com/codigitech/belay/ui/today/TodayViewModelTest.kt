package com.codigitech.belay.ui.today

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ONE_DAY_MILLIS = 86_400_000L

private class FakeAuthRepositoryForToday(private val userId: String? = "user-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "arun@example.com"

  override fun logOut() = Unit
}

private class FakeChallengeRepositoryForToday(private val challenge: ChallengeEntity?) : ChallengeRepository {
  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<com.codigitech.belay.data.repository.HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ) = error("not used")

  override fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?> = MutableStateFlow(challenge)

  override fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>> = MutableStateFlow(emptyList())

  override fun observeChallenge(challengeId: String): Flow<ChallengeEntity?> = error("not used")
}

private class FakeHabitRepositoryForToday(private val habits: List<HabitEntity>) : HabitRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = MutableStateFlow(habits)

  override suspend fun updateStreak(habit: HabitEntity, newStreak: Int) = error("not used")
}

private class FakeCheckInRepositoryForToday(initial: List<CheckInEntity> = emptyList()) : CheckInRepository {
  private val state = MutableStateFlow(initial)
  val setCheckInCalls = mutableListOf<List<Any?>>()

  override fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>> = state

  override fun observeForChallenge(challengeId: String): Flow<List<CheckInEntity>> = state

  override suspend fun setCheckIn(habitId: String, challengeId: String, date: Long, done: Boolean): CheckInEntity {
    setCheckInCalls += listOf(habitId, challengeId, date, done)
    val entity =
      CheckInEntity(
        checkInId = "check-$habitId",
        habitId = habitId,
        challengeId = challengeId,
        date = date,
        done = done,
        checkedAt = if (done) 0L else null,
        clientIdempotencyKey = "check-$habitId",
      )
    state.value = state.value.filterNot { it.habitId == habitId } + entity
    return entity
  }
}

private class FakeUserRepositoryForToday(private val profiles: Map<String, UserEntity> = emptyMap()) : UserRepository {
  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) = error("not used")

  override suspend fun getProfile(userId: String): UserEntity? = profiles[userId]

  override fun observeLocalUser(userId: String): Flow<UserEntity?> = error("not used")
}

private class FakeInteractionRepositoryForToday(initial: List<InteractionEntity> = emptyList()) : InteractionRepository {
  private val state = MutableStateFlow(initial)

  override suspend fun sendCheer(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult = error("not used")

  override suspend fun sendNudge(challengeId: String, fromUserId: String, message: String): CheerOrNudgeResult = error("not used")

  override fun observeForChallenge(challengeId: String): Flow<List<InteractionEntity>> = state
}

class TodayViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val fixedClock = BelayClock { 5 * ONE_DAY_MILLIS } // epoch day 5

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
      startDate = 2L, // epoch day 2, so day 5 is the 4th day in
      status = "active",
    )

  private val habits =
    listOf(
      HabitEntity("habit-1", "challenge-1", "Run 3km", "before 8am", null, null, 0, currentStreak = 4),
      HabitEntity("habit-2", "challenge-1", "Read", null, null, null, 1, currentStreak = 2),
    )

  private val witness = UserEntity("user-2", "Priya", "AB12", "witness", "system", null, true, createdAt = 0L)

  private fun viewModel(
    authRepository: AuthRepository = FakeAuthRepositoryForToday(),
    challengeRepository: ChallengeRepository = FakeChallengeRepositoryForToday(activeChallenge),
    habitRepository: HabitRepository = FakeHabitRepositoryForToday(habits),
    checkInRepository: CheckInRepository = FakeCheckInRepositoryForToday(),
    userRepository: UserRepository = FakeUserRepositoryForToday(mapOf("user-2" to witness)),
    interactionRepository: InteractionRepository = FakeInteractionRepositoryForToday(),
  ) = TodayViewModel(
    authRepository,
    challengeRepository,
    habitRepository,
    checkInRepository,
    userRepository,
    interactionRepository,
    fixedClock,
  )

  @Test
  fun `no active challenge yields the empty state`() = runTest {
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForToday(null))

    assertFalse(vm.uiState.value.hasActiveChallenge)
    assertFalse(vm.uiState.value.isLoading)
    assertTrue(vm.uiState.value.habits.isEmpty())
  }

  @Test
  fun `active challenge with no check-ins yet surfaces habits unchecked and correct summary numbers`() = runTest {
    val vm = viewModel()

    val state = vm.uiState.value
    assertTrue(state.hasActiveChallenge)
    assertEquals("Morning reset", state.challengeTitle)
    assertEquals(
      listOf(
        TodayHabitUiState("habit-1", "Run 3km", "before 8am", streak = 4, checkedToday = false),
        TodayHabitUiState("habit-2", "Read", null, streak = 2, checkedToday = false),
      ),
      state.habits,
    )
    assertEquals(0f, state.progressFraction, 0.001f)
    assertEquals(3, state.perfectDays)
    assertEquals(1, state.graceDaysLeft) // 2 total - 1 used
    assertEquals(18, state.daysToGo) // 21 duration - (day 5 - day 2)
  }

  @Test
  fun `toggling an unchecked habit checks it and updates progress`() = runTest {
    val checkInRepository = FakeCheckInRepositoryForToday()
    val vm = viewModel(checkInRepository = checkInRepository)

    vm.toggleHabit("habit-1")

    assertEquals(listOf("habit-1", "challenge-1", 5L, true), checkInRepository.setCheckInCalls.last())
    val habitState = vm.uiState.value.habits.first { it.habitId == "habit-1" }
    assertTrue(habitState.checkedToday)
    assertEquals(0.5f, vm.uiState.value.progressFraction, 0.001f)
  }

  @Test
  fun `toggling an already-checked habit unchecks it`() = runTest {
    val checkInRepository =
      FakeCheckInRepositoryForToday(
        initial = listOf(CheckInEntity("check-habit-1", "habit-1", "challenge-1", 5L, done = true, checkedAt = 0L, clientIdempotencyKey = "check-habit-1"))
      )
    val vm = viewModel(checkInRepository = checkInRepository)

    vm.toggleHabit("habit-1")

    assertEquals(listOf("habit-1", "challenge-1", 5L, false), checkInRepository.setCheckInCalls.last())
    assertFalse(vm.uiState.value.habits.first { it.habitId == "habit-1" }.checkedToday)
  }

  @Test
  fun `witness status reads waiting when nothing is checked yet`() = runTest {
    val vm = viewModel()

    assertEquals("Priya is watching · waiting", vm.uiState.value.witnessStatusText)
  }

  @Test
  fun `witness status reads watching you finish once some but not all habits are checked`() = runTest {
    val checkInRepository = FakeCheckInRepositoryForToday()
    val vm = viewModel(checkInRepository = checkInRepository)

    vm.toggleHabit("habit-1")

    assertEquals("Priya is watching · watching you finish", vm.uiState.value.witnessStatusText)
  }

  @Test
  fun `witness status reads saw all once every habit is checked`() = runTest {
    val checkInRepository = FakeCheckInRepositoryForToday()
    val vm = viewModel(checkInRepository = checkInRepository)

    vm.toggleHabit("habit-1")
    vm.toggleHabit("habit-2")

    assertEquals("Priya is watching · saw all 2", vm.uiState.value.witnessStatusText)
  }

  @Test
  fun `a cheer sent today surfaces as the cheer message`() = runTest {
    val interactions =
      FakeInteractionRepositoryForToday(
        listOf(
          InteractionEntity("i1", "challenge-1", "user-2", "cheer", date = 5L, message = "Nice work!", createdAt = 0L)
        )
      )
    val vm = viewModel(interactionRepository = interactions)

    assertEquals("Nice work!", vm.uiState.value.cheerMessage)
  }

  @Test
  fun `a cheer from a previous day does not surface`() = runTest {
    val interactions =
      FakeInteractionRepositoryForToday(
        listOf(
          InteractionEntity("i1", "challenge-1", "user-2", "cheer", date = 4L, message = "Yesterday's cheer", createdAt = 0L)
        )
      )
    val vm = viewModel(interactionRepository = interactions)

    assertNull(vm.uiState.value.cheerMessage)
  }

  @Test
  fun `a nudge sent today surfaces as the nudge message until dismissed`() = runTest {
    val interactions =
      FakeInteractionRepositoryForToday(
        listOf(InteractionEntity("i1", "challenge-1", "user-2", "nudge", date = 5L, message = "Don't forget!", createdAt = 0L))
      )
    val vm = viewModel(interactionRepository = interactions)

    assertEquals("Don't forget!", vm.uiState.value.nudgeMessage)

    vm.dismissNudge()

    assertNull(vm.uiState.value.nudgeMessage)
  }
}
