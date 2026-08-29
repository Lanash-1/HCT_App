package com.codigitech.belay.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.HabitRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** AlarmManager alarms don't survive a reboot — re-arm every habit reminder for the active challenge once the device comes back up. */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

  @Inject lateinit var authRepository: AuthRepository
  @Inject lateinit var challengeRepository: ChallengeRepository
  @Inject lateinit var habitRepository: HabitRepository
  @Inject lateinit var reminderScheduler: ReminderScheduler

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val userId = authRepository.currentUserId() ?: return

    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val challenge = challengeRepository.observeActiveForChallenger(userId).first()
        if (challenge != null) {
          habitRepository
            .observeForChallenge(challenge.challengeId)
            .first()
            .forEach { habit -> habit.reminderTime?.let { reminderScheduler.scheduleHabitReminder(habit.habitId, habit.name, it) } }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
