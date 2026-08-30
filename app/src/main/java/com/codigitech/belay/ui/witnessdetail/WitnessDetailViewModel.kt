package com.codigitech.belay.ui.witnessdetail

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
import com.codigitech.belay.domain.challenge.hasChallengeEnded
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class WitnessDetailHabitUiState(val name: String, val time: String, val checkedToday: Boolean)

data class WitnessDetailLogRowUiState(val date: Long, val dayLabel: String, val score: String, val detail: String)

data class WitnessDetailUiState(
  val isLoading: Boolean = true,
  val challengerName: String = "",
  val challengeTitle: String = "",
  val dayNo: Int = 0,
  val totalDays: Int = 0,
  val habits: List<WitnessDetailHabitUiState> = emptyList(),
  val allDone: Boolean = false,
  val headline: String = "",
  val progressPercent: Int = 0,
  val perfectDays: Int = 0,
  val checkInsTotal: Int = 0,
  val graceDaysLeft: Int = 0,
  val log: List<WitnessDetailLogRowUiState> = emptyList(),
  val challengeId: String? = null, // not rendered; needed by sendCheer/sendNudge
  val hasEnded: Boolean = false, // PRD §6.7
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WitnessDetailViewModel
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

  private val _uiState = MutableStateFlow(WitnessDetailUiState())
  val uiState: StateFlow<WitnessDetailUiState> = _uiState.asStateFlow()

  private val challengeIdFlow = MutableStateFlow<String?>(null)

  private val today: Long =
    Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

  init {
    challengeIdFlow
      .filterNotNull()
      .flatMapLatest(::challengeUiStateFlow)
      .onEach { state -> _uiState.value = state }
      .launchIn(viewModelScope)
  }

  /** Set once, right after this ViewModel is obtained for a given challenge (there's no SavedStateHandle nav-arg wiring yet). */
  fun load(challengeId: String) {
    challengeIdFlow.value = challengeId
  }

  suspend fun sendCheer(message: String): CheerOrNudgeResult = send(message, interactionRepository::sendCheer)

  suspend fun sendNudge(message: String): CheerOrNudgeResult = send(message, interactionRepository::sendNudge)

  private suspend fun send(message: String, action: suspend (String, String, String) -> CheerOrNudgeResult): CheerOrNudgeResult {
    val challengeId = _uiState.value.challengeId ?: return CheerOrNudgeResult.MessageBlank
    val userId = authRepository.currentUserId() ?: return CheerOrNudgeResult.MessageBlank
    return action(challengeId, userId, message)
  }

  private fun challengeUiStateFlow(challengeId: String) =
    challengeRepository.observeChallenge(challengeId).flatMapLatest { challenge ->
      if (challenge == null) {
        flowOf(WitnessDetailUiState(isLoading = false))
      } else {
        combine(
          habitRepository.observeForChallenge(challenge.challengeId),
          checkInRepository.observeForChallengeAndDate(challenge.challengeId, today),
          checkInRepository.observeForChallenge(challenge.challengeId),
          challengerNameFlow(challenge.challengerUserId),
        ) { habits, todayCheckIns, allCheckIns, challengerName ->
          buildState(challenge, habits, todayCheckIns, allCheckIns, challengerName)
        }
      }
    }

  private fun challengerNameFlow(userId: String) = flow { emit(userRepository.getProfile(userId)?.displayName ?: "") }

  private fun buildState(
    challenge: ChallengeEntity,
    habits: List<HabitEntity>,
    todayCheckIns: List<CheckInEntity>,
    allCheckIns: List<CheckInEntity>,
    challengerName: String,
  ): WitnessDetailUiState {
    val checkedIds = todayCheckIns.filter { it.done }.map { it.habitId }.toSet()
    val habitStates =
      habits.map { habit ->
        val checkedAt = todayCheckIns.firstOrNull { it.habitId == habit.habitId }?.checkedAt
        WitnessDetailHabitUiState(
          name = habit.name,
          time = if (habit.habitId in checkedIds) formatTime(checkedAt) else WitnessDetailCopy.NOT_YET_TIME,
          checkedToday = habit.habitId in checkedIds,
        )
      }
    val doneCount = checkedIds.size
    val habitCount = habitStates.size
    val dayNo = (today - challenge.startDate + 1).coerceAtLeast(1).toInt()

    return WitnessDetailUiState(
      isLoading = false,
      challengerName = challengerName,
      challengeTitle = challenge.title,
      dayNo = dayNo,
      totalDays = challenge.durationDays,
      hasEnded = hasChallengeEnded(challenge.startDate, challenge.durationDays, today),
      habits = habitStates,
      allDone = habitCount > 0 && doneCount == habitCount,
      headline = WitnessDetailCopy.headline(challengerName, doneCount, habitCount),
      progressPercent =
        if (challenge.durationDays == 0) 0 else ((dayNo.toFloat() / challenge.durationDays) * 100).roundToInt().coerceIn(0, 100),
      perfectDays = challenge.perfectDays,
      checkInsTotal = allCheckIns.count { it.done },
      graceDaysLeft = challenge.graceDaysTotal - challenge.graceDaysUsed,
      log = buildLog(habitCount, allCheckIns, challenge.startDate),
      challengeId = challenge.challengeId,
    )
  }

  private fun buildLog(habitCount: Int, allCheckIns: List<CheckInEntity>, startDate: Long): List<WitnessDetailLogRowUiState> =
    allCheckIns
      .groupBy { it.date }
      .entries
      .sortedByDescending { it.key }
      .map { (date, checkIns) ->
        val doneCount = checkIns.count { it.done }
        WitnessDetailLogRowUiState(
          date = date,
          dayLabel = dayLabel(date, startDate),
          score = "$doneCount/$habitCount",
          detail = WitnessDetailCopy.logDetail(doneCount, habitCount),
        )
      }

  private fun dayLabel(date: Long, startDate: Long): String =
    when (date) {
      today -> "Today"
      today - 1 -> "Yesterday"
      else -> "Day ${date - startDate + 1}"
    }

  private fun formatTime(epochMillis: Long?): String =
    epochMillis?.let { TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: WitnessDetailCopy.NOT_YET_TIME

  private companion object {
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
  }
}
