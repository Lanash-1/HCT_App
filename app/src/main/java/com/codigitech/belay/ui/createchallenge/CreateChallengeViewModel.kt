package com.codigitech.belay.ui.createchallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeCreationResult
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.HabitSpec
import com.codigitech.belay.data.repository.PairingRepository
import com.codigitech.belay.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitInput(val name: String = "", val detail: String = "")

data class WitnessOption(val userId: String, val displayName: String)

data class CreateChallengeUiState(
  val title: String = "",
  val habits: List<HabitInput> = listOf(HabitInput()),
  val selectedDurationDays: Int? = null,
  val witnessOptions: List<WitnessOption> = emptyList(),
  val selectedWitnessId: String? = null,
  val graceDays: Int = 1,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val didCreate: Boolean = false,
)

@HiltViewModel
class CreateChallengeViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val pairingRepository: PairingRepository,
  private val userRepository: UserRepository,
  private val challengeRepository: ChallengeRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateChallengeUiState())
  val uiState: StateFlow<CreateChallengeUiState> = _uiState.asStateFlow()

  init {
    authRepository.currentUserId()?.let { userId ->
      viewModelScope.launch {
        val options =
          pairingRepository.getPairedContactIds(userId).mapNotNull { contactId ->
            userRepository.getProfile(contactId)?.let { WitnessOption(contactId, it.displayName) }
          }
        _uiState.update { it.copy(witnessOptions = options) }
      }
    }
  }

  fun onTitleChange(value: String) {
    _uiState.update { it.copy(title = value, errorMessage = null) }
  }

  fun onHabitNameChange(index: Int, value: String) {
    updateHabit(index) { it.copy(name = value) }
  }

  fun onHabitDetailChange(index: Int, value: String) {
    updateHabit(index) { it.copy(detail = value) }
  }

  private fun updateHabit(index: Int, transform: (HabitInput) -> HabitInput) {
    _uiState.update { state ->
      state.copy(habits = state.habits.mapIndexed { i, habit -> if (i == index) transform(habit) else habit }, errorMessage = null)
    }
  }

  fun addHabit() {
    _uiState.update { state -> if (state.habits.size >= MAX_HABITS) state else state.copy(habits = state.habits + HabitInput()) }
  }

  fun removeHabit(index: Int) {
    _uiState.update { state ->
      val updated = state.habits.filterIndexed { i, _ -> i != index }
      state.copy(habits = updated.ifEmpty { listOf(HabitInput()) })
    }
  }

  fun selectDuration(days: Int) {
    _uiState.update { it.copy(selectedDurationDays = days, errorMessage = null) }
  }

  fun selectWitness(userId: String) {
    _uiState.update { it.copy(selectedWitnessId = userId, errorMessage = null) }
  }

  fun incrementGraceDays() {
    _uiState.update { it.copy(graceDays = (it.graceDays + 1).coerceAtMost(3)) }
  }

  fun decrementGraceDays() {
    _uiState.update { it.copy(graceDays = (it.graceDays - 1).coerceAtLeast(0)) }
  }

  fun submit() {
    val state = _uiState.value
    val userId = authRepository.currentUserId() ?: return
    val title = state.title.trim()
    val habits =
      state.habits.mapNotNull { habit ->
        habit.name.trim().takeIf { it.isNotBlank() }?.let { name -> HabitSpec(name, habit.detail.trim().ifBlank { null }) }
      }
    val duration = state.selectedDurationDays
    val witnessId = state.selectedWitnessId

    when {
      title.isBlank() -> _uiState.update { it.copy(errorMessage = "Give your challenge a name") }
      habits.isEmpty() -> _uiState.update { it.copy(errorMessage = "Add at least one habit") }
      duration == null -> _uiState.update { it.copy(errorMessage = "Pick how long") }
      witnessId == null -> _uiState.update { it.copy(errorMessage = "Pick who's watching") }
      else ->
        viewModelScope.launch {
          _uiState.update { it.copy(isLoading = true) }
          when (challengeRepository.createChallenge(userId, witnessId, title, habits, duration, state.graceDays)) {
            is ChallengeCreationResult.Success -> _uiState.update { it.copy(isLoading = false, didCreate = true) }
            ChallengeCreationResult.TooFewHabits -> fail("Add at least one habit")
            ChallengeCreationResult.TooManyHabits -> fail("Up to 5 habits only")
            ChallengeCreationResult.InvalidDuration -> fail("Pick how long")
            ChallengeCreationResult.InvalidGraceDays -> fail("Grace days must be 0-3")
          }
        }
    }
  }

  private fun fail(message: String) {
    _uiState.update { it.copy(isLoading = false, errorMessage = message) }
  }

  companion object {
    const val MAX_HABITS = 5
    val DURATIONS_DAYS = listOf(7, 21, 30, 66)
  }
}
