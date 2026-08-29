package com.codigitech.belay.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.RecapEntity
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.RecapRepository
import com.codigitech.belay.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class RecapHabitSummaryDto(val habitId: String, val name: String, val score: Int, val dailyCells: List<Boolean>)

data class RecapHabitRowUiState(val name: String, val score: String, val cells: List<Boolean>)

data class RecapUiState(
  val isLoading: Boolean = true,
  val hasRecap: Boolean = false,
  val challengeTitle: String = "",
  val weekRangeLabel: String = "",
  val checkInsTotal: Int = 0,
  val checkInsPossible: Int = 0,
  val perfectDays: Int = 0,
  val habitRows: List<RecapHabitRowUiState> = emptyList(),
  val witnessName: String = "",
  val shareText: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val challengeRepository: ChallengeRepository,
  private val recapRepository: RecapRepository,
  private val userRepository: UserRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RecapUiState())
  val uiState: StateFlow<RecapUiState> = _uiState.asStateFlow()

  init {
    val userId = authRepository.currentUserId()
    if (userId == null) {
      _uiState.value = _uiState.value.copy(isLoading = false)
    } else {
      challengeRepository
        .observeActiveForChallenger(userId)
        .flatMapLatest(::recapUiStateFlow)
        .onEach { state -> _uiState.value = state }
        .launchIn(viewModelScope)
    }
  }

  private fun recapUiStateFlow(challenge: ChallengeEntity?) =
    if (challenge == null) {
      flowOf(RecapUiState(isLoading = false, hasRecap = false))
    } else {
      combine(recapRepository.observeForChallenge(challenge.challengeId), witnessNameFlow(challenge.witnessUserId)) { recaps, witnessName ->
        buildState(challenge, recaps.maxByOrNull { it.weekStart }, witnessName)
      }
    }

  private fun witnessNameFlow(witnessUserId: String) = flow { emit(userRepository.getProfile(witnessUserId)?.displayName ?: "") }

  private fun buildState(challenge: ChallengeEntity, latest: RecapEntity?, witnessName: String): RecapUiState {
    if (latest == null) {
      return RecapUiState(isLoading = false, hasRecap = false, challengeTitle = challenge.title, witnessName = witnessName)
    }
    val habitRows =
      Json.decodeFromString<List<RecapHabitSummaryDto>>(latest.perHabitSummaryJson).map { dto ->
        RecapHabitRowUiState(name = dto.name, score = "${dto.score}/${dto.dailyCells.size}", cells = dto.dailyCells)
      }
    val weekRangeLabel =
      "${WEEK_DATE_FORMATTER.format(LocalDate.ofEpochDay(latest.weekStart))} – ${WEEK_DATE_FORMATTER.format(LocalDate.ofEpochDay(latest.weekEnd))}"
    return RecapUiState(
      isLoading = false,
      hasRecap = true,
      challengeTitle = challenge.title,
      weekRangeLabel = weekRangeLabel,
      checkInsTotal = latest.checkInsTotal,
      checkInsPossible = latest.checkInsPossible,
      perfectDays = latest.perfectDays,
      habitRows = habitRows,
      witnessName = witnessName,
      shareText =
        RecapCopy.shareText(challenge.title, weekRangeLabel, latest.checkInsTotal, latest.checkInsPossible, latest.perfectDays, witnessName),
    )
  }

  private companion object {
    val WEEK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
  }
}
