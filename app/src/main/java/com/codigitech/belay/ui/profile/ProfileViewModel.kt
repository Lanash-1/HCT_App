package com.codigitech.belay.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.UserEntity
import com.codigitech.belay.data.repository.AccountDeletionResult
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.CheckInRepository
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.PushTokenRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
  val isDeletingAccount: Boolean = false,
  val deleteErrorMessage: String? = null,
  val didDeleteAccount: Boolean = false,
)

@Serializable data class ProfileDataExport(val displayName: String, val pairCode: String, val mode: String, val ownChallenge: ChallengeExport?, val watching: List<String>)

@Serializable
data class ChallengeExport(
  val title: String,
  val durationDays: Int,
  val graceDaysTotal: Int,
  val graceDaysUsed: Int,
  val perfectDays: Int,
  val habits: List<HabitExport>,
)

@Serializable data class HabitExport(val name: String, val detail: String?, val currentStreak: Int, val checkIns: List<CheckInExport>)

@Serializable data class CheckInExport(val date: Long, val done: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel
@Inject
constructor(
  private val authRepository: AuthRepository,
  private val userRepository: UserRepository,
  private val challengeRepository: ChallengeRepository,
  private val habitRepository: HabitRepository,
  private val checkInRepository: CheckInRepository,
  private val clock: BelayClock,
  private val pushTokenRepository: PushTokenRepository,
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
        .onEach { state ->
          // Preserve the account-action fields — they're set outside this Room-driven pipeline
          // (deleteAccount/exportData) and a background Firestore sync elsewhere in the app can
          // cause this pipeline to re-emit at any time; a raw replace would wipe them out.
          _uiState.update { current ->
            state.copy(
              isDeletingAccount = current.isDeletingAccount,
              deleteErrorMessage = current.deleteErrorMessage,
              didDeleteAccount = current.didDeleteAccount,
            )
          }
        }
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

  fun deleteAccount() {
    viewModelScope.launch {
      _uiState.update { it.copy(isDeletingAccount = true, deleteErrorMessage = null) }
      // Before the account exists no more: the server prunes tokens off a user document, and
      // deleteAccount removes that document outright (backend/functions deleteAccount).
      userId?.let { pushTokenRepository.unregister(it) }
      when (val result = authRepository.deleteAccount()) {
        AccountDeletionResult.Success -> _uiState.update { it.copy(isDeletingAccount = false, didDeleteAccount = true) }
        is AccountDeletionResult.Failure -> _uiState.update { it.copy(isDeletingAccount = false, deleteErrorMessage = result.message) }
      }
    }
  }

  /** A lightweight local export of the signed-in user's own data (PRD §6.3) — not their witnessed challenges' data. */
  suspend fun exportData(): String {
    val id = userId ?: return "{}"
    val user = userRepository.getProfile(id) ?: return "{}"
    val active = challengeRepository.observeActiveForChallenger(id).first()
    val witnessed = challengeRepository.observeWitnessed(id).first()
    val ownChallenge =
      active?.let { challenge ->
        val habits = habitRepository.observeForChallenge(challenge.challengeId).first()
        val checkIns = checkInRepository.observeForChallenge(challenge.challengeId).first()
        ChallengeExport(
          title = challenge.title,
          durationDays = challenge.durationDays,
          graceDaysTotal = challenge.graceDaysTotal,
          graceDaysUsed = challenge.graceDaysUsed,
          perfectDays = challenge.perfectDays,
          habits =
            habits.map { habit ->
              HabitExport(
                name = habit.name,
                detail = habit.detail,
                currentStreak = habit.currentStreak,
                checkIns = checkIns.filter { it.habitId == habit.habitId }.map { CheckInExport(it.date, it.done) },
              )
            },
        )
      }
    val export = ProfileDataExport(user.displayName, user.pairCode, user.defaultMode, ownChallenge, witnessed.map { it.title })
    return Json.encodeToString(export)
  }

  private fun ownHabitsFlow(challenge: ChallengeEntity?): Flow<List<HabitEntity>> =
    if (challenge == null) flowOf(emptyList()) else habitRepository.observeForChallenge(challenge.challengeId)

  private fun witnessNameFlow(challenge: ChallengeEntity?): Flow<String?> =
    if (challenge?.witnessUserId == null) flowOf(null) else flow { emit(userRepository.getProfile(challenge.witnessUserId)?.displayName) }

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
