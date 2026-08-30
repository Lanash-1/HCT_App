package com.codigitech.belay.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.RecapEntity
import com.codigitech.belay.data.media.RecapCardImage
import com.codigitech.belay.data.media.RecapCardStore
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.RecapRepository
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.domain.recap.recapCardFileName
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
import kotlinx.coroutines.flow.update
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
  /** One-shot feedback for a save/share attempt, cleared once shown. */
  val cardMessage: String? = null,
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
  private val cardStore: RecapCardStore,
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

  /** Saves the rendered card to the gallery (PRD §5.4 "share/save actions"). */
  suspend fun saveCard(image: RecapCardImage) {
    val saved = cardStore.saveToGallery(image, cardFileName())
    _uiState.update { it.copy(cardMessage = if (saved != null) RecapCopy.SAVED_CONFIRMATION else RecapCopy.SAVE_FAILED) }
  }

  /**
   * Writes the card somewhere a share target can read it, returning its URI.
   *
   * Null means share the text on its own rather than nothing at all — a recap the user can't send
   * is worse than one that goes out without its picture.
   */
  suspend fun shareCard(image: RecapCardImage): String? {
    val uri = cardStore.cacheForSharing(image, cardFileName())
    if (uri == null) _uiState.update { it.copy(cardMessage = RecapCopy.SHARE_FAILED) }
    return uri
  }

  fun onCardMessageShown() {
    _uiState.update { it.copy(cardMessage = null) }
  }

  private fun cardFileName(): String = _uiState.value.let { recapCardFileName(it.challengeTitle, it.weekRangeLabel) }
}
