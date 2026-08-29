package com.codigitech.belay.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.domain.auth.SignupValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
  SignUp,
  LogIn,
}

data class AuthUiState(
  val email: String = "",
  val password: String = "",
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val signedInUserId: String? = null,
)

@HiltViewModel
class AuthViewModel
@Inject
constructor(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState(signedInUserId = authRepository.currentUserId()))
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun onEmailChange(email: String) {
    _uiState.update { it.copy(email = email, errorMessage = null) }
  }

  fun onPasswordChange(password: String) {
    _uiState.update { it.copy(password = password, errorMessage = null) }
  }

  fun submit(mode: AuthMode) {
    val (email, password) = _uiState.value.let { it.email to it.password }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val outcome =
        when (mode) {
          AuthMode.SignUp -> authRepository.signUp(email, password)
          AuthMode.LogIn -> authRepository.logIn(email, password)
        }
      _uiState.update { current ->
        when (outcome) {
          is AuthOutcome.Success -> current.copy(isLoading = false, signedInUserId = outcome.userId)
          is AuthOutcome.ValidationFailed -> current.copy(isLoading = false, errorMessage = outcome.reason.toMessage())
          is AuthOutcome.Failure -> current.copy(isLoading = false, errorMessage = outcome.message)
        }
      }
    }
  }

  fun logOut() {
    viewModelScope.launch {
      authRepository.logOut()
      _uiState.update { AuthUiState() }
    }
  }
}

private fun SignupValidationError.toMessage(): String =
  when (this) {
    SignupValidationError.BlankEmail -> "Enter your email"
    SignupValidationError.InvalidEmail -> "Enter a valid email"
    SignupValidationError.PasswordTooShort -> "Password must be at least 6 characters"
  }
