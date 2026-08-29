package com.codigitech.belay.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.PairingRepository
import com.codigitech.belay.data.repository.PairingResult
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.domain.user.displayNameFromEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingRole {
  Challenger,
  Witness,
}

data class OnboardingUiState(
  val role: OnboardingRole? = null,
  val shareCode: String? = null,
  val pairCodeInput: String = "",
  val isLoading: Boolean = false,
  val pairingSuccess: Boolean = false,
  val pairingError: String? = null,
  val didContinue: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val userRepository: UserRepository,
  private val pairingRepository: PairingRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OnboardingUiState())
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  init {
    authRepository.currentUserId()?.let { userId ->
      viewModelScope.launch {
        userRepository.ensureProfile(userId, displayNameFromEmail(authRepository.currentUserEmail().orEmpty()))
      }
    }
  }

  fun pickRole(role: OnboardingRole) {
    _uiState.update { it.copy(role = role, shareCode = null) }
    val userId = authRepository.currentUserId() ?: return
    viewModelScope.launch {
      userRepository.setDefaultMode(userId, role.toMode())
      if (role == OnboardingRole.Challenger) {
        val pairing = pairingRepository.createPendingPairing(userId)
        _uiState.update { it.copy(shareCode = pairing.pairCode) }
      }
    }
  }

  fun onPairCodeInputChange(value: String) {
    _uiState.update { it.copy(pairCodeInput = value.uppercase(), pairingError = null) }
  }

  fun submitPairCode() {
    val userId = authRepository.currentUserId() ?: return
    val code = _uiState.value.pairCodeInput.trim()
    if (code.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      when (pairingRepository.completePairing(code, userId)) {
        is PairingResult.Success -> _uiState.update { it.copy(isLoading = false, pairingSuccess = true, pairingError = null) }
        PairingResult.NotFound ->
          _uiState.update {
            it.copy(isLoading = false, pairingSuccess = false, pairingError = "That code isn't valid, or it's already been used.")
          }
      }
    }
  }

  fun continueOnboarding() {
    if (_uiState.value.role != null) _uiState.update { it.copy(didContinue = true) }
  }
}

private fun OnboardingRole.toMode(): String =
  when (this) {
    OnboardingRole.Challenger -> "challenger"
    OnboardingRole.Witness -> "witness"
  }
