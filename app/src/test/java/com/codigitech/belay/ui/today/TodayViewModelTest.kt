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

  override suspend fun logOut() = Unit

  override suspend fun deleteAccount(): com.codigitech.belay.data.repository.AccountDeletionResult = error("not used")
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

  override suspend fun syncRemoteUpdates(challengeId: String) = Unit
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

  override suspend fun setThemePref(userId: String, pref: String) = error("not used")

  override suspend fun setDailyReminderTime(userId: String, time: String?) = error("not used")

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) = error("not used")

  override suspend fun touchLastSeen(userId: String) = Unit


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

  // lastSeenAt = today: the default fixture is a witness who is actually around, so the
  // §6.7 "away"/"never opened" states are opt-in per test rather than the baseline.
  private val witness = UserEntity("user-2", "Priya", "AB12", "witness", "system", null, true, createdAt = 0L, lastSeenAt = 5L)

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

  @Test
  fun `a habit with a fresh streak-broken flag surfaces in the recovery moment until dismissed`() = runTest {
    val brokenHabits =
      listOf(
        HabitEntity("habit-1", "challenge-1", "Run 3km", "before 8am", null, null, 0, currentStreak = 0, streakBrokenAt = "2026-08-29"),
        HabitEntity("habit-2", "challenge-1", "Read", null, null, null, 1, currentStreak = 2),
      )
    val vm = viewModel(habitRepository = FakeHabitRepositoryForToday(brokenHabits))

    assertEquals(listOf("Run 3km"), vm.uiState.value.brokenHabitNames)

    vm.dismissRecovery()

    assertTrue(vm.uiState.value.brokenHabitNames.isEmpty())
  }

  @Test
  fun `no habit has a streak-broken flag means no recovery moment`() = runTest {
    val vm = viewModel()

    assertTrue(vm.uiState.value.brokenHabitNames.isEmpty())
  }

  // ---- PRD §6.7 edge states ----

  @Test
  fun `a challenge with nobody watching says so, instead of showing a blank witness pill`() = runTest {
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForToday(activeChallenge.copy(witnessUserId = null)))

    val state = vm.uiState.value
    assertFalse(state.hasWitness)
    assertEquals(TodayCopy.NO_WITNESS_STATUS, state.witnessStatusText)
  }

  @Test
  fun `a witness who has never opened the app is named as invited, not as watching`() = runTest {
    val vm = viewModel(userRepository = FakeUserRepositoryForToday(mapOf("user-2" to witness.copy(lastSeenAt = null))))

    val state = vm.uiState.value
    assertTrue(state.hasWitness)
    assertEquals(TodayCopy.witnessNotOpenedYet("Priya"), state.witnessStatusText)
  }

  @Test
  fun `a witness who has been away for days is flagged, with the count`() = runTest {
    // Clock is epoch day 5; last seen on day 1 is four days ago.
    val vm = viewModel(userRepository = FakeUserRepositoryForToday(mapOf("user-2" to witness.copy(lastSeenAt = 1L))))

    val state = vm.uiState.value
    assertTrue(state.isWitnessAway)
    assertEquals(TodayCopy.witnessAway("Priya", 4), state.witnessStatusText)
  }

  @Test
  fun `a witness who looked in yesterday is not flagged as away`() = runTest {
    val vm = viewModel(userRepository = FakeUserRepositoryForToday(mapOf("user-2" to witness.copy(lastSeenAt = 4L))))

    assertFalse(vm.uiState.value.isWitnessAway)
  }

  @Test
  fun `spending the last grace day is surfaced, since the next miss now costs a streak`() = runTest {
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForToday(activeChallenge.copy(graceDaysUsed = 2)))

    val state = vm.uiState.value
    assertEquals(0, state.graceDaysLeft)
    assertTrue(state.isGraceExhausted)
  }

  @Test
  fun `grace still in hand is not flagged`() = runTest {
    assertFalse(viewModel().uiState.value.isGraceExhausted)
  }

  @Test
  fun `a challenge whose days have all elapsed reads as finished`() = runTest {
    // Started day 2, ran 3 days (2, 3, 4) — day 5 is past the end.
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForToday(activeChallenge.copy(durationDays = 3)))

    val state = vm.uiState.value
    assertTrue(state.hasEnded)
    assertEquals(0, state.daysToGo)
  }

  @Test
  fun `a challenge still running does not read as finished`() = runTest {
    val state = viewModel().uiState.value

    assertFalse(state.hasEnded)
    assertEquals(18, state.daysToGo)
  }
}
