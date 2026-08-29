package com.codigitech.belay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.codigitech.belay.data.local.dao.ChallengeDao
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.local.dao.HabitDao
import com.codigitech.belay.data.local.dao.InteractionDao
import com.codigitech.belay.data.local.dao.PairingDao
import com.codigitech.belay.data.local.dao.RecapDao
import com.codigitech.belay.data.local.dao.UserDao
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import com.codigitech.belay.data.local.entity.InteractionEntity
import com.codigitech.belay.data.local.entity.PairingEntity
import com.codigitech.belay.data.local.entity.RecapEntity
import com.codigitech.belay.data.local.entity.UserEntity

@Database(
  entities = [
    UserEntity::class,
    ChallengeEntity::class,
    HabitEntity::class,
    CheckInEntity::class,
    PairingEntity::class,
    InteractionEntity::class,
    RecapEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class BelayDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao

  abstract fun challengeDao(): ChallengeDao

  abstract fun habitDao(): HabitDao

  abstract fun checkInDao(): CheckInDao

  abstract fun pairingDao(): PairingDao

  abstract fun interactionDao(): InteractionDao

  abstract fun recapDao(): RecapDao
}
