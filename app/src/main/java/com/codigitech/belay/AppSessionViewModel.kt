package com.codigitech.belay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.core.ErrorReporter
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.PushTokenRepository
import com.codigitech.belay.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AppSessionUiState(val isLoading: Boolean = true, val themePref: String = "system", val mode: String = "challenger")

/**
 * Cross-cutting session state (theme preference, challenger/witness mode) read at the top of the
 * nav graph — both drive things above any single screen: [BelayTheme][com.codigitech.belay.theme.BelayTheme]
 * needs the theme pref, and the main tab bar needs the mode.
 */
@HiltViewModel
class AppSessionViewModel
@Inject
constructor(
  errorReporter: ErrorReporter,
  pushTokenRepository: PushTokenRepository,
  private val authRepository: AuthRepository,
  userRepository: UserRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AppSessionUiState())
  val uiState: StateFlow<AppSessionUiState> = _uiState.asStateFlow()

  init {
    val userId = authRepository.currentUserId()
    // Tag crash reports with the account they came from (PRD §6.8) — with one witness per
    // challenger, a report that can't be traced to an account is close to untriageable.
    errorReporter.identify(userId)
    if (userId == null) {
      _uiState.value = _uiState.value.copy(isLoading = false)
    } else {
      // FCM rotates tokens on its own schedule, including while the app is closed — re-register
      // on every start so a returning user doesn't silently stop receiving pushes.
      viewModelScope.launch { pushTokenRepository.register(userId) }
      userRepository
        .observeLocalUser(userId)
        .onEach { user ->
          _uiState.value =
            if (user == null) {
              AppSessionUiState(isLoading = false)
            } else {
              AppSessionUiState(isLoading = false, themePref = user.themePref, mode = user.defaultMode)
            }
        }
        .launchIn(viewModelScope)
    }
  }
}
