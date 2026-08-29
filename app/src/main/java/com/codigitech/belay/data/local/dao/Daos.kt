package com.codigitech.belay.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.InteractionEntity
import com.codigitech.belay.data.local.entity.PairingEntity
import com.codigitech.belay.data.local.entity.RecapEntity
import com.codigitech.belay.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(user: UserEntity)

  @Query("SELECT * FROM users WHERE userId = :userId") fun observe(userId: String): Flow<UserEntity?>

  @Query("SELECT * FROM users WHERE userId = :userId") suspend fun get(userId: String): UserEntity?
}

@Dao
interface ChallengeDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(challenge: ChallengeEntity)

  @Update suspend fun update(challenge: ChallengeEntity)

  @Query("SELECT * FROM challenges WHERE challengeId = :challengeId") fun observe(challengeId: String): Flow<ChallengeEntity?>

  @Query("SELECT * FROM challenges WHERE challengerUserId = :userId AND status = 'active' LIMIT 1")
  fun observeActiveForChallenger(userId: String): Flow<ChallengeEntity?>

  @Query("SELECT * FROM challenges WHERE witnessUserId = :userId AND status = 'active'")
  fun observeWitnessed(userId: String): Flow<List<ChallengeEntity>>
}

@Dao
interface HabitDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(habits: List<HabitEntity>)

  @Update suspend fun update(habit: HabitEntity)

  @Query("SELECT * FROM habits WHERE challengeId = :challengeId ORDER BY sortOrder ASC")
  fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>>
}

@Dao
interface CheckInDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(checkIn: CheckInEntity)

  @Query("SELECT * FROM check_ins WHERE challengeId = :challengeId AND date = :date")
  fun observeForChallengeAndDate(challengeId: String, date: Long): Flow<List<CheckInEntity>>

  @Query("SELECT * FROM check_ins WHERE habitId = :habitId ORDER BY date DESC")
  fun observeForHabit(habitId: String): Flow<List<CheckInEntity>>
}

@Dao
interface PairingDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(pairing: PairingEntity)

  @Query("SELECT * FROM pairings WHERE pairCode = :pairCode AND status = 'pending' LIMIT 1")
  suspend fun findPendingByCode(pairCode: String): PairingEntity?
}

@Dao
interface InteractionDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(interaction: InteractionEntity)

  @Query("SELECT * FROM interactions WHERE challengeId = :challengeId ORDER BY createdAt DESC")
  fun observeForChallenge(challengeId: String): Flow<List<InteractionEntity>>

  @Query(
    "SELECT COUNT(*) FROM interactions WHERE challengeId = :challengeId AND type = 'nudge' AND date = :date"
  )
  suspend fun nudgeCountForDate(challengeId: String, date: Long): Int
}

@Dao
interface RecapDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(recap: RecapEntity)

  @Query("SELECT * FROM recaps WHERE challengeId = :challengeId ORDER BY weekStart DESC")
  fun observeForChallenge(challengeId: String): Flow<List<RecapEntity>>
}
