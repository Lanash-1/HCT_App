package com.codigitech.belay.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.InteractionEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.CheckInRepository
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.InteractionRepository
import com.codigitech.belay.data.repository.PairingRepository
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.domain.challenge.hasChallengeEnded
import com.codigitech.belay.domain.challenge.isGraceExhausted
import com.codigitech.belay.domain.challenge.isWitnessAway
import com.codigitech.belay.domain.challenge.witnessDaysAway
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class TodayHabitUiState(
  val habitId: String,
  val name: String,
  val detail: String?,
  val streak: Int,
  val checkedToday: Boolean,
)

data class TodayUiState(
  val isLoading: Boolean = true,
  val hasActiveChallenge: Boolean = false,
  val challengeTitle: String = "",
  val habits: List<TodayHabitUiState> = emptyList(),
  val progressFraction: Float = 0f,
  val perfectDays: Int = 0,
  val graceDaysLeft: Int = 0,
  val daysToGo: Int = 0,
  val witnessStatusText: String = "",
  val cheerMessage: String? = null,
  val nudgeMessage: String? = null,
  val challengeId: String? = null, // not rendered; needed by toggleHabit
  val latestNudgeId: String? = null, // not rendered; needed by dismissNudge
  val brokenHabitNames: List<String> = emptyList(), // PRD §6.2 recovery moment
  // PRD §6.7 edge states
  val hasWitness: Boolean = false,
  val isWitnessAway: Boolean = false,
  val isGraceExhausted: Boolean = false,
  val hasEnded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val challengeRepository: ChallengeRepository,
  private val habitRepository: HabitRepository,
  private val checkInRepository: CheckInRepository,
  private val userRepository: UserRepository,
  private val interactionRepository: InteractionRepository,
  private val clock: BelayClock,
  private val pairingRepository: PairingRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(TodayUiState())
  val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

  private val dismissedNudgeId = MutableStateFlow<String?>(null)
  private val recoveryDismissed = MutableStateFlow(false)

  private val today: Long =
    Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  init {
    val userId = authRepository.currentUserId()
    if (userId == null) {
      _uiState.value = _uiState.value.copy(isLoading = false)
    } else {
      challengeRepository
        .observeActiveForChallenger(userId)
        .flatMapLatest(::challengeUiStateFlow)
        .onEach { state -> _uiState.value = state }
        .launchIn(viewModelScope)

      // A challenge can be left witnessless when its witness deletes their account (PRD §6.7).
      // Pairing again is the fix, but the witness picker only exists at creation — so a challenge
      // in that state adopts whoever the challenger has since paired with, rather than stranding
      // them with a challenge nobody can watch.
      viewModelScope.launch {
        challengeRepository
          .observeActiveForChallenger(userId)
          .distinctUntilChanged()
          .collectLatest { challenge ->
            if (challenge != null && challenge.witnessUserId == null) {
              pairingRepository.getPairedContactIds(userId).firstOrNull()?.let { contactId ->
                challengeRepository.attachWitnessIfMissing(userId, contactId)
              }
            }
          }
      }

      viewModelScope.launch {
        challengeRepository
          .observeActiveForChallenger(userId)
          .map { it?.challengeId }
          .distinctUntilChanged()
          .collectLatest { challengeId -> if (challengeId != null) challengeRepository.syncRemoteUpdates(challengeId) }
      }
    }
  }

  private fun challengeUiStateFlow(challenge: ChallengeEntity?) =
    if (challenge == null) {
      flowOf(TodayUiState(isLoading = false, hasActiveChallenge = false))
    } else {
      combine(
        combine(
          habitRepository.observeForChallenge(challenge.challengeId),
          checkInRepository.observeForChallengeAndDate(challenge.challengeId, today),
          interactionRepository.observeForChallenge(challenge.challengeId),
          witnessProfileFlow(challenge.witnessUserId),
          dismissedNudgeId,
        ) { habits, checkIns, interactions, witness, dismissedNudgeId ->
          HabitDayContext(habits, checkIns, interactions, witness, dismissedNudgeId)
        },
        recoveryDismissed,
      ) { context, recoveryDismissed -> buildState(challenge, context, recoveryDismissed) }
    }

  private fun witnessProfileFlow(witnessUserId: String?) = flow { emit(witnessUserId?.let { userRepository.getProfile(it) }) }

  private data class HabitDayContext(
    val habits: List<HabitEntity>,
    val checkIns: List<CheckInEntity>,
    val interactions: List<InteractionEntity>,
    val witness: UserEntity?,
    val dismissedNudgeId: String?,
  )

  private fun buildState(challenge: ChallengeEntity, context: HabitDayContext, recoveryDismissed: Boolean): TodayUiState {
    val (habits, checkIns, interactions, witness, dismissedNudgeId) = context
    val checkedHabitIds = checkIns.filter { it.done }.map { it.habitId }.toSet()
    val habitStates =
      habits.map { habit ->
        TodayHabitUiState(
          habitId = habit.habitId,
          name = habit.name,
          detail = habit.detail,
          streak = habit.currentStreak,
          checkedToday = habit.habitId in checkedHabitIds,
        )
      }
    val todaysInteractions = interactions.filter { it.date == today }
    val latestCheer = todaysInteractions.filter { it.type == "cheer" }.maxByOrNull { it.createdAt }
    val latestNudge = todaysInteractions.filter { it.type == "nudge" }.maxByOrNull { it.createdAt }
    val daysAway = witnessDaysAway(witness?.lastSeenAt, today)
    return TodayUiState(
      isLoading = false,
      hasActiveChallenge = true,
      challengeTitle = challenge.title,
      habits = habitStates,
      progressFraction = if (habitStates.isEmpty()) 0f else checkedHabitIds.size.toFloat() / habitStates.size,
      perfectDays = challenge.perfectDays,
      graceDaysLeft = (challenge.graceDaysTotal - challenge.graceDaysUsed).coerceAtLeast(0),
      daysToGo = (challenge.durationDays - (today - challenge.startDate)).coerceAtLeast(0).toInt(),
      witnessStatusText = witnessStatusText(witness, daysAway, checked = checkedHabitIds.size, total = habitStates.size),
      hasWitness = witness != null,
      isWitnessAway = isWitnessAway(daysAway),
      isGraceExhausted = isGraceExhausted(challenge.graceDaysTotal, challenge.graceDaysUsed),
      hasEnded = hasChallengeEnded(challenge.startDate, challenge.durationDays, today),
      cheerMessage = latestCheer?.message,
      nudgeMessage = latestNudge?.takeIf { it.interactionId != dismissedNudgeId }?.message,
      challengeId = challenge.challengeId,
      latestNudgeId = latestNudge?.interactionId,
      brokenHabitNames = if (recoveryDismissed) emptyList() else habits.filter { it.streakBrokenAt != null }.map { it.name },
    )
  }

  /**
   * The witness pill's line. It carries the §6.7 states as well as the live one: a challenger
   * whose witness never opened the invite is in a different situation from one being watched, and
   * a pill that says "is watching" in both cases is quietly lying.
   */
  private fun witnessStatusText(witness: UserEntity?, daysAway: Int?, checked: Int, total: Int): String =
    when {
      witness == null -> TodayCopy.NO_WITNESS_STATUS
      daysAway == null -> TodayCopy.witnessNotOpenedYet(witness.displayName)
      isWitnessAway(daysAway) -> TodayCopy.witnessAway(witness.displayName, daysAway)
      else -> TodayCopy.witnessStatusText(witness.displayName, checked = checked, total = total)
    }

  fun toggleHabit(habitId: String) {
    val state = _uiState.value
    val challengeId = state.challengeId ?: return
    val currentlyChecked = state.habits.firstOrNull { it.habitId == habitId }?.checkedToday ?: false
    viewModelScope.launch {
      checkInRepository.setCheckIn(habitId = habitId, challengeId = challengeId, date = today, done = !currentlyChecked)
    }
  }

  fun dismissNudge() {
    _uiState.value.latestNudgeId?.let { dismissedNudgeId.value = it }
  }

  fun dismissRecovery() {
    recoveryDismissed.value = true
  }
}
