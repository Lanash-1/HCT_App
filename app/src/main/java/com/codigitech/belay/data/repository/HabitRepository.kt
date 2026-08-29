package com.codigitech.belay.data.repository

import com.codigitech.belay.data.local.dao.HabitDao
import com.codigitech.belay.data.local.entity.HabitEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
  fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>>

  /** Applies a streak value pushed down from a Catalyst Function result — never computed and pushed up locally. */
  suspend fun updateStreak(habit: HabitEntity, newStreak: Int)
}

class HabitRepositoryImpl
@Inject
constructor(private val habitDao: HabitDao) : HabitRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = habitDao.observeForChallenge(challengeId)

  override suspend fun updateStreak(habit: HabitEntity, newStreak: Int) {
    habitDao.update(habit.copy(currentStreak = newStreak))
  }
}
