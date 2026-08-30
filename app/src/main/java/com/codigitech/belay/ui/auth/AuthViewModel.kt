package com.codigitech.belay.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.data.repository.AuthOutcome
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.PushTokenRepository
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
constructor(private val pushTokenRepository: PushTokenRepository, private val authRepository: AuthRepository) : ViewModel() {

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
      // Bind this device for push before touching UI state: FCM is how a cheer or a finished day
      // reaches someone whose app isn't open (docs/TECH_STACK.md §4).
      if (outcome is AuthOutcome.Success) pushTokenRepository.register(outcome.userId)
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
      // Unbind first — after logOut() there is no user id left to unregister against, and a
      // token left behind would keep delivering this account's cheers to whoever uses the device next.
      authRepository.currentUserId()?.let { pushTokenRepository.unregister(it) }
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
