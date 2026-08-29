package com.codigitech.belay.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.CheckInRepository
import com.codigitech.belay.data.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
  val challengeId: String? = null, // not rendered; needed by toggleHabit
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
  private val clock: BelayClock,
) : ViewModel() {

  private val _uiState = MutableStateFlow(TodayUiState())
  val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

  private val today: Long =
    Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  init {
    val userId = authRepository.currentUserId()
    if (userId == null) {
      _uiState.update { it.copy(isLoading = false) }
    } else {
      challengeRepository
        .observeActiveForChallenger(userId)
        .flatMapLatest(::challengeUiStateFlow)
        .onEach { state -> _uiState.value = state }
        .launchIn(viewModelScope)
    }
  }

  private fun challengeUiStateFlow(challenge: ChallengeEntity?) =
    if (challenge == null) {
      flowOf(TodayUiState(isLoading = false, hasActiveChallenge = false))
    } else {
      combine(
        habitRepository.observeForChallenge(challenge.challengeId),
        checkInRepository.observeForChallengeAndDate(challenge.challengeId, today),
      ) { habits, checkIns ->
        buildState(challenge, habits, checkIns)
      }
    }

  private fun buildState(challenge: ChallengeEntity, habits: List<HabitEntity>, checkIns: List<CheckInEntity>): TodayUiState {
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
    return TodayUiState(
      isLoading = false,
      hasActiveChallenge = true,
      challengeTitle = challenge.title,
      habits = habitStates,
      progressFraction = if (habitStates.isEmpty()) 0f else checkedHabitIds.size.toFloat() / habitStates.size,
      perfectDays = challenge.perfectDays,
      graceDaysLeft = challenge.graceDaysTotal - challenge.graceDaysUsed,
      daysToGo = (challenge.durationDays - (today - challenge.startDate)).coerceAtLeast(0).toInt(),
      challengeId = challenge.challengeId,
    )
  }

  fun toggleHabit(habitId: String) {
    val state = _uiState.value
    val challengeId = state.challengeId ?: return
    val currentlyChecked = state.habits.firstOrNull { it.habitId == habitId }?.checkedToday ?: false
    viewModelScope.launch {
      checkInRepository.setCheckIn(habitId = habitId, challengeId = challengeId, date = today, done = !currentlyChecked)
    }
  }
}
