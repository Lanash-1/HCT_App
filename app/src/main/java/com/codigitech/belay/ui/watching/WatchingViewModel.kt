package com.codigitech.belay.ui.watching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.CheckInRepository
import com.codigitech.belay.data.repository.CheerOrNudgeResult
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.InteractionRepository
import com.codigitech.belay.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class WatchingHabitUiState(val name: String, val time: String, val checkedToday: Boolean)

data class WatchingPersonUiState(
  val challengeId: String,
  val challengerName: String,
  val subtitle: String,
  val doneCount: Int,
  val habitCount: Int,
  val onTrack: Boolean,
  val habits: List<WatchingHabitUiState>,
)

data class WatchingUiState(val isLoading: Boolean = true, val people: List<WatchingPersonUiState> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchingViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val challengeRepository: ChallengeRepository,
  private val habitRepository: HabitRepository,
  private val checkInRepository: CheckInRepository,
  private val userRepository: UserRepository,
  private val interactionRepository: InteractionRepository,
  private val clock: BelayClock,
) : ViewModel() {

  private val _uiState = MutableStateFlow(WatchingUiState())
  val uiState: StateFlow<WatchingUiState> = _uiState.asStateFlow()

  private val today: Long =
    Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  init {
    val userId = authRepository.currentUserId()
    if (userId == null) {
      _uiState.value = _uiState.value.copy(isLoading = false)
    } else {
      challengeRepository
        .observeWitnessed(userId)
        .flatMapLatest(::peopleFlow)
        .onEach { people -> _uiState.value = WatchingUiState(isLoading = false, people = people) }
        .launchIn(viewModelScope)
    }
  }

  suspend fun sendCheer(challengeId: String, message: String): CheerOrNudgeResult {
    val userId = authRepository.currentUserId() ?: return CheerOrNudgeResult.MessageBlank
    return interactionRepository.sendCheer(challengeId, userId, message)
  }

  suspend fun sendNudge(challengeId: String, message: String): CheerOrNudgeResult {
    val userId = authRepository.currentUserId() ?: return CheerOrNudgeResult.MessageBlank
    return interactionRepository.sendNudge(challengeId, userId, message)
  }

  private fun peopleFlow(challenges: List<ChallengeEntity>) =
    if (challenges.isEmpty()) flowOf(emptyList()) else combine(challenges.map(::personFlow)) { it.toList() }

  private fun personFlow(challenge: ChallengeEntity) =
    combine(
      habitRepository.observeForChallenge(challenge.challengeId),
      checkInRepository.observeForChallengeAndDate(challenge.challengeId, today),
      challengerNameFlow(challenge.challengerUserId),
    ) { habits, checkIns, challengerName ->
      buildPerson(challenge, habits, checkIns, challengerName)
    }

  private fun challengerNameFlow(userId: String) = flow { emit(userRepository.getProfile(userId)?.displayName ?: "") }

  private fun buildPerson(
    challenge: ChallengeEntity,
    habits: List<HabitEntity>,
    checkIns: List<CheckInEntity>,
    challengerName: String,
  ): WatchingPersonUiState {
    val checkedIds = checkIns.filter { it.done }.map { it.habitId }.toSet()
    val habitStates =
      habits.map { habit ->
        val checkedAt = checkIns.firstOrNull { it.habitId == habit.habitId }?.checkedAt
        WatchingHabitUiState(
          name = habit.name,
          time = if (habit.habitId in checkedIds) formatTime(checkedAt) else WatchingCopy.NOT_YET_TIME,
          checkedToday = habit.habitId in checkedIds,
        )
      }
    val dayNo = (today - challenge.startDate + 1).coerceAtLeast(1)
    return WatchingPersonUiState(
      challengeId = challenge.challengeId,
      challengerName = challengerName,
      subtitle = WatchingCopy.subtitle(challenge.title, dayNo.toInt(), challenge.durationDays),
      doneCount = checkedIds.size,
      habitCount = habitStates.size,
      onTrack = checkedIds.isNotEmpty(),
      habits = habitStates,
    )
  }

  private fun formatTime(epochMillis: Long?): String =
    epochMillis?.let { TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: WatchingCopy.NOT_YET_TIME

  private companion object {
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
  }
}
