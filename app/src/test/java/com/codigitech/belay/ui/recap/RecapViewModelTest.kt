package com.codigitech.belay.ui.recap

import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.RecapEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.RecapRepository
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepositoryForRecap(private val userId: String? = "user-1") : AuthRepository {
  override suspend fun signUp(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override suspend fun logIn(email: String, password: String): AuthOutcome = AuthOutcome.Success("unused")

  override fun currentUserId(): String? = userId

  override fun currentUserEmail(): String? = "arun@example.com"

  override suspend fun logOut() = Unit

  override suspend fun deleteAccount(): com.codigitech.belay.data.repository.AccountDeletionResult = error("not used")
}

private class FakeChallengeRepositoryForRecap(private val active: ChallengeEntity?) : ChallengeRepository {
  override suspend fun createChallenge(
    challengerUserId: String,
    witnessUserId: String,
    title: String,
    habits: List<com.codigitech.belay.data.repository.HabitSpec>,
    durationDays: Int,
    graceDaysTotal: Int,
  ) = error("not used")

  override fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?> = MutableStateFlow(active)

  override fun observeWitnessed(userId: String) = error("not used")

  override fun observeChallenge(challengeId: String) = error("not used")

  override suspend fun syncRemoteUpdates(challengeId: String) = error("not used")
}

private class FakeRecapRepositoryForRecap(private val recaps: List<RecapEntity>) : RecapRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<RecapEntity>> = MutableStateFlow(recaps)
}

private class FakeUserRepositoryForRecap(private val profiles: Map<String, UserEntity>) : UserRepository {
  override suspend fun ensureProfile(userId: String, displayName: String) = error("not used")

  override suspend fun setDefaultMode(userId: String, mode: String) = error("not used")

  override suspend fun setThemePref(userId: String, pref: String) = error("not used")

  override suspend fun setDailyReminderTime(userId: String, time: String?) = error("not used")

  override suspend fun setNudgeAllowed(userId: String, allowed: Boolean) = error("not used")

  override suspend fun touchLastSeen(userId: String) = Unit


  override suspend fun getProfile(userId: String): UserEntity? = profiles[userId]

  override fun observeLocalUser(userId: String) = error("not used")
}

private object FakeCardImage : com.codigitech.belay.data.media.RecapCardImage {
  // Never invoked: a fake store has no reason to rasterise, and android.graphics.Bitmap can't be
  // constructed in a plain JVM test.
  override fun asBitmap(): android.graphics.Bitmap = error("not rasterised in tests")
}

private class FakeRecapCardStore : com.codigitech.belay.data.media.RecapCardStore {
  val saved = mutableListOf<String>()
  val cached = mutableListOf<String>()
  var failing = false

  override suspend fun saveToGallery(image: com.codigitech.belay.data.media.RecapCardImage, fileName: String): String? {
    if (failing) return null
    saved.add(fileName)
    return "content://media/external/images/media/1"
  }

  override suspend fun cacheForSharing(image: com.codigitech.belay.data.media.RecapCardImage, fileName: String): String? {
    if (failing) return null
    cached.add(fileName)
    return "content://com.codigitech.belay.fileprovider/recap-cards/card.png"
  }
}

class RecapViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val activeChallenge =
    ChallengeEntity(
      challengeId = "challenge-1",
      challengerUserId = "user-1",
      witnessUserId = "user-2",
      title = "Morning reset",
      durationDays = 21,
      graceDaysTotal = 2,
      graceDaysUsed = 1,
      perfectDays = 8,
      startDate = 0L,
      status = "active",
    )

  private val witness = UserEntity("user-2", "Priya", "AB12", "witness", "system", null, true, createdAt = 0L)

  private val perHabitSummaryJson =
    """[{"habitId":"habit-1","name":"Run 3 km","score":7,"dailyCells":[true,true,true,true,true,true,true]},""" +
      """{"habitId":"habit-2","name":"Read 20 pages","score":6,"dailyCells":[true,true,false,true,true,true,true]}]"""

  private val olderRecap =
    RecapEntity(
      recapId = "recap-old",
      challengeId = "challenge-1",
      weekStart = 0L,
      weekEnd = 6L,
      checkInsTotal = 10,
      checkInsPossible = 14,
      perfectDays = 2,
      perHabitSummaryJson = perHabitSummaryJson,
      generatedAt = 0L,
    )

  private val newerRecap =
    RecapEntity(
      recapId = "recap-new",
      challengeId = "challenge-1",
      weekStart = 7L,
      weekEnd = 13L,
      checkInsTotal = 13,
      checkInsPossible = 14,
      perfectDays = 4,
      perHabitSummaryJson = perHabitSummaryJson,
      generatedAt = 1L,
    )

  private val cardStore = FakeRecapCardStore()

  private fun viewModel(
    authRepository: AuthRepository = FakeAuthRepositoryForRecap(),
    challengeRepository: ChallengeRepository = FakeChallengeRepositoryForRecap(activeChallenge),
    recapRepository: RecapRepository = FakeRecapRepositoryForRecap(listOf(newerRecap, olderRecap)),
    userRepository: UserRepository = FakeUserRepositoryForRecap(mapOf("user-2" to witness)),
  ) = RecapViewModel(authRepository, challengeRepository, recapRepository, userRepository, cardStore)

  @Test
  fun `no active challenge yields no recap`() = runTest {
    val vm = viewModel(challengeRepository = FakeChallengeRepositoryForRecap(active = null))

    assertFalse(vm.uiState.value.isLoading)
    assertFalse(vm.uiState.value.hasRecap)
  }

  @Test
  fun `an active challenge with no recaps yet surfaces the empty state`() = runTest {
    val vm = viewModel(recapRepository = FakeRecapRepositoryForRecap(emptyList()))

    val state = vm.uiState.value
    assertFalse(state.hasRecap)
    assertEquals("Morning reset", state.challengeTitle)
  }

  @Test
  fun `the most recent recap is parsed into rows, most recent by week start`() = runTest {
    val vm = viewModel()

    val state = vm.uiState.value
    assertTrue(state.hasRecap)
    assertEquals(13, state.checkInsTotal)
    assertEquals(14, state.checkInsPossible)
    assertEquals(4, state.perfectDays)
    assertEquals("Jan 8 – Jan 14", state.weekRangeLabel)
    assertEquals(
      listOf("Run 3 km" to "7/7", "Read 20 pages" to "6/7"),
      state.habitRows.map { it.name to it.score },
    )
    assertEquals(listOf(true, true, true, true, true, true, true), state.habitRows[0].cells)
    assertEquals(listOf(true, true, false, true, true, true, true), state.habitRows[1].cells)
  }

  @Test
  fun `witness name is resolved for the witnessed-by line and share text`() = runTest {
    val vm = viewModel()

    val state = vm.uiState.value
    assertEquals("Priya", state.witnessName)
    assertTrue(state.shareText.contains("Priya"))
    assertTrue(state.shareText.contains("Morning reset"))
    assertTrue(state.shareText.contains("13"))
  }

  @Test
  fun `saving the card names the file after the challenge and week, and confirms it landed`() = runTest {
    val vm = viewModel()

    vm.saveCard(FakeCardImage)

    assertEquals(listOf("Belay-Morning-reset-Jan-8-Jan-14.png"), cardStore.saved)
    assertEquals(RecapCopy.SAVED_CONFIRMATION, vm.uiState.value.cardMessage)
  }

  @Test
  fun `a failed save says so instead of silently doing nothing`() = runTest {
    val vm = viewModel()
    cardStore.failing = true

    vm.saveCard(FakeCardImage)

    assertEquals(RecapCopy.SAVE_FAILED, vm.uiState.value.cardMessage)
  }

  @Test
  fun `dismissing the message clears it, so it isn't re-shown on the next recomposition`() = runTest {
    val vm = viewModel()
    vm.saveCard(FakeCardImage)

    vm.onCardMessageShown()

    assertNull(vm.uiState.value.cardMessage)
  }

  @Test
  fun `sharing hands back a uri for the rendered card`() = runTest {
    val vm = viewModel()

    val uri = vm.shareCard(FakeCardImage)

    assertNotNull(uri)
    assertEquals(listOf("Belay-Morning-reset-Jan-8-Jan-14.png"), cardStore.cached)
  }

  @Test
  fun `a card that can't be written falls back to sharing without an image, and says why`() = runTest {
    val vm = viewModel()
    cardStore.failing = true

    val uri = vm.shareCard(FakeCardImage)

    assertNull(uri)
    assertEquals(RecapCopy.SHARE_FAILED, vm.uiState.value.cardMessage)
  }
}
