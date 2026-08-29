package com.codigitech.belay.ui.watching

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ONE_DAY_MILLIS = 86_400_000L

private class FakeAuthRepositoryForWatching(private val userId: String? = "witness-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "priya@example.com"

  override suspend fun logOut() = Unit

  override suspend fun deleteAccount(): com.codigitech.belay.data.repository.AccountDeletionResult = error("not used")
}

private class FakeChallengeRepositoryForWatching(private val witnessed: List<ChallengeEntity>) : ChallengeRepository {
  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<com.codigitech.belay.data.repository.HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ) = error("not used")

  override fun observeActiveForChallenger(userId: String) = error("not used")

  override fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>> = MutableStateFlow(witnessed)

  override fun observeChallenge(challengeId: String): Flow<ChallengeEntity?> = error("not used")

  override suspend fun syncRemoteUpdates(challengeId: String) = error("not used")
}

private class FakeHabitRepositoryForWatching(private val byChallenge: Map<String, List<HabitEntity>>) : HabitRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = MutableStateFlow(byChallenge[challengeId].orEmpty())

  override suspend fun updateStreak(habit: HabitEntity, newStreak: Int) = error("not used")
}

private class FakeCheckInRepositoryForWatching(private val byChallenge: Map<String, List<CheckInEntity>>) : CheckInRepository {
  override fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>> =
    MutableStateFlow(byChallenge[challengeId].orEmpty().filter { it.date == date })

  override fun observeForChallenge(challengeId: String): Flow<List<CheckInEntity>> = MutableStateFlow(byChallenge[challengeId].orEmpty())

  override suspend fun setCheckIn(habitId: String, challengeId: String, date: Long, done: Boolean) = error("not used")
}

private class FakeUserRepositoryForWatching(private val profiles: Map<String, UserEntity>) : UserRepository {
  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) = error("not used")

  override suspend fun setThemePref(userId: String, pref: String) = error("not used")

  override suspend fun setDailyReminderTime(userId: String, time: String?) = error("not used")

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) = error("not used")

  override suspend fun getProfile(userId: String): UserEntity? = profiles[userId]

  override fun observeLocalUser(userId: String) = error("not used")
}

private class FakeInteractionRepositoryForWatching : InteractionRepository {
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

class WatchingViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val fixedClock = BelayClock { 5 * ONE_DAY_MILLIS } // epoch day 5

  private val meeraChallenge =
    ChallengeEntity(
      challengeId = "challenge-meera",
      challengerUserId = "meera",
      witnessUserId = "witness-1",
      title = "Evening wind-down",
      durationDays = 21,
      graceDaysTotal = 1,
      graceDaysUsed = 0,
      perfectDays = 3,
      startDate = 0L, // day 5 is day 6 of the challenge
      status = "active",
    )

  private val meeraHabits =
    listOf(
      HabitEntity("habit-1", "challenge-meera", "Lights out by 11", null, null, null, 0, currentStreak = 5),
      HabitEntity("habit-2", "challenge-meera", "Journal one page", null, null, null, 1, currentStreak = 2),
    )

  private fun viewModel(
    authRepository: AuthRepository = FakeAuthRepositoryForWatching(),
    challengeRepository: ChallengeRepository = FakeChallengeRepositoryForWatching(listOf(meeraChallenge)),
    habitRepository: HabitRepository = FakeHabitRepositoryForWatching(mapOf("challenge-meera" to meeraHabits)),
    checkInRepository: CheckInRepository = FakeCheckInRepositoryForWatching(emptyMap()),
    userRepository: UserRepository = FakeUserRepositoryForWatching(mapOf("meera" to user("meera", "Meera"))),
    interactionRepository: InteractionRepository = FakeInteractionRepositoryForWatching(),
  ) = WatchingViewModel(
    authRepository,
    challengeRepository,
    habitRepository,
    checkInRepository,
    userRepository,
    interactionRepository,
    fixedClock,
  )

  private fun user(id: String, name: String) = UserEntity(id, name, "AB12", "challenger", "system", null, true, createdAt = 0L)

  @Test
  fun `no one witnessed yields an empty list`() = runTest {
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForWatching(emptyList()))

    assertFalse(vm.uiState.value.isLoading)
    assertTrue(vm.uiState.value.people.isEmpty())
  }

  @Test
  fun `a witnessed challenge with no check-ins surfaces the person unchecked and not on track`() = runTest {
    val vm = viewModel()

    val person = vm.uiState.value.people.single()
    assertEquals("Meera", person.challengerName)
    assertEquals("Evening wind-down · day 6 of 21", person.subtitle)
    assertEquals(0, person.doneCount)
    assertEquals(2, person.habitCount)
    assertFalse(person.onTrack)
    assertEquals(listOf("Lights out by 11" to false, "Journal one page" to false), person.habits.map { it.name to it.checkedToday })
  }

  @Test
  fun `a checked-in habit surfaces done with a formatted time, and the person is on track`() = runTest {
    val checkedAt = 5 * ONE_DAY_MILLIS + 3_600_000L // 1am same day, system default zone
    val checkIns =
      mapOf(
        "challenge-meera" to
          listOf(
            CheckInEntity("c1", "habit-1", "challenge-meera", date = 5L, done = true, checkedAt = checkedAt, clientIdempotencyKey = "c1")
          )
      )

    val vm = viewModel(checkInRepository = FakeCheckInRepositoryForWatching(checkIns))

    val person = vm.uiState.value.people.single()
    assertEquals(1, person.doneCount)
    assertTrue(person.onTrack)
    val expectedTime = DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochMilli(checkedAt).atZone(ZoneId.systemDefault()))
    val habit1 = person.habits.first { it.name == "Lights out by 11" }
    assertTrue(habit1.checkedToday)
    assertEquals(expectedTime, habit1.time)
    val habit2 = person.habits.first { it.name == "Journal one page" }
    assertFalse(habit2.checkedToday)
    assertEquals(WatchingCopy.NOT_YET_TIME, habit2.time)
  }

  @Test
  fun `sending a cheer delegates to the interaction repository as the signed-in witness`() = runTest {
    val interactions = FakeInteractionRepositoryForWatching()
    val vm = viewModel(interactionRepository = interactions)

    val result = vm.sendCheer("challenge-meera", "Nice work!")

    assertTrue(result is CheerOrNudgeResult.Success)
    assertEquals(listOf("challenge-meera", "witness-1", "Nice work!"), interactions.cheerCalls.single())
  }

  @Test
  fun `sending a nudge delegates to the interaction repository as the signed-in witness`() = runTest {
    val interactions = FakeInteractionRepositoryForWatching()
    val vm = viewModel(interactionRepository = interactions)

    val result = vm.sendNudge("challenge-meera", "Don't forget!")

    assertTrue(result is CheerOrNudgeResult.Success)
    assertEquals(listOf("challenge-meera", "witness-1", "Don't forget!"), interactions.nudgeCalls.single())
  }
}
