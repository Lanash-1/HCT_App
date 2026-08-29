package com.codigitech.belay.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ProfilePersonRowUiState(val title: String, val subtitle: String, val challengeId: String? = null)

data class ProfileUiState(
  val isLoading: Boolean = true,
  val displayName: String = "",
  val pairCode: String = "",
  val joinedLabel: String = "",
  val mode: String = "challenger",
  val themePref: String = "system",
  val habitCount: Int = 0,
  val bestStreak: Int = 0,
  val peopleWatchedCount: Int = 0,
  val witnessRow: ProfilePersonRowUiState? = null,
  val watchingRows: List<ProfilePersonRowUiState> = emptyList(),
  val dailyReminderTime: String? = null,
  val nudgeAllowed: Boolean = true,
  val nudgeToggleLabel: String = "",
  val graceDaysLeft: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val userRepository: UserRepository,
  private val challengeRepository: ChallengeRepository,
  private val habitRepository: HabitRepository,
  private val clock: BelayClock,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  private val userId: String? = authRepository.currentUserId()

  init {
    if (userId == null) {
      _uiState.value = _uiState.value.copy(isLoading = false)
    } else {
      combine(
          userRepository.observeLocalUser(userId),
          challengeRepository.observeActiveForChallenger(userId),
          challengeRepository.observeWitnessed(userId),
        ) { user, activeChallenge, witnessed ->
          Triple(user, activeChallenge, witnessed)
        }
        .flatMapLatest { (user, activeChallenge, witnessed) ->
          if (user == null) {
            flowOf(ProfileUiState(isLoading = false))
          } else {
            combine(ownHabitsFlow(activeChallenge), witnessNameFlow(activeChallenge), watchingRowsFlow(witnessed)) {
              ownHabits,
              witnessName,
              watchingRows ->
              buildState(user, activeChallenge, ownHabits, witnessName, watchingRows)
            }
          }
        }
        .onEach { state -> _uiState.value = state }
        .launchIn(viewModelScope)
    }
  }

  fun setMode(mode: String) = withUserId { userRepository.setDefaultMode(it, mode) }

  fun setThemePref(pref: String) = withUserId { userRepository.setThemePref(it, pref) }

  fun setNudgeAllowed(allowed: Boolean) = withUserId { userRepository.setNudgeAllowed(it, allowed) }

  fun setDailyReminderTime(time: String) = withUserId { userRepository.setDailyReminderTime(it, time) }

  private fun withUserId(action: suspend (String) -> Unit) {
    val id = userId ?: return
    viewModelScope.launch { action(id) }
  }

  private fun ownHabitsFlow(challenge: ChallengeEntity?): Flow<List<HabitEntity>> =
    if (challenge == null) flowOf(emptyList()) else habitRepository.observeForChallenge(challenge.challengeId)

  private fun witnessNameFlow(challenge: ChallengeEntity?): Flow<String?> =
    if (challenge == null) flowOf(null) else flow { emit(userRepository.getProfile(challenge.witnessUserId)?.displayName) }

  private fun watchingRowsFlow(witnessed: List<ChallengeEntity>): Flow<List<Pair<ChallengeEntity, String>>> =
    if (witnessed.isEmpty()) {
      flowOf(emptyList())
    } else {
      combine(
        witnessed.map { challenge ->
          flow { emit(challenge to (userRepository.getProfile(challenge.challengerUserId)?.displayName ?: "")) }
        }
      ) {
        it.toList()
      }
    }

  private fun buildState(
    user: UserEntity,
    activeChallenge: ChallengeEntity?,
    ownHabits: List<HabitEntity>,
    witnessName: String?,
    watchingRows: List<Pair<ChallengeEntity, String>>,
  ): ProfileUiState {
    val witnessRow = witnessName?.let { ProfilePersonRowUiState(title = it, subtitle = ProfileCopy.witnessSubtitle(ownHabits.size)) }
    return ProfileUiState(
      isLoading = false,
      displayName = user.displayName,
      pairCode = user.pairCode,
      joinedLabel = ProfileCopy.joinedLabel(MONTH_FORMATTER.format(Instant.ofEpochMilli(user.createdAt).atZone(ZoneId.systemDefault()))),
      mode = user.defaultMode,
      themePref = user.themePref,
      habitCount = ownHabits.size,
      bestStreak = ownHabits.maxOfOrNull { it.currentStreak } ?: 0,
      peopleWatchedCount = watchingRows.size,
      witnessRow = witnessRow,
      watchingRows =
        watchingRows.map { (challenge, name) ->
          ProfilePersonRowUiState(title = name, subtitle = ProfileCopy.watchingSubtitle(challenge.title), challengeId = challenge.challengeId)
        },
      dailyReminderTime = user.notifDailyReminderTime,
      nudgeAllowed = user.notifAllowNudge,
      nudgeToggleLabel = ProfileCopy.nudgeToggleLabel(witnessName ?: ""),
      graceDaysLeft = activeChallenge?.let { it.graceDaysTotal - it.graceDaysUsed },
    )
  }

  private companion object {
    val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
  }
}
