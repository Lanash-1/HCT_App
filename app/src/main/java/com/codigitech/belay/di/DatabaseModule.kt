package com.codigitech.belay.di

import android.content.Context
import androidx.room.Room
import com.codigitech.belay.data.local.BelayDatabase
import com.codigitech.belay.data.local.dao.ChallengeDao
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.local.dao.HabitDao
import com.codigitech.belay.data.local.dao.InteractionDao
import com.codigitech.belay.data.local.dao.PairingDao
import com.codigitech.belay.data.local.dao.RecapDao
import com.codigitech.belay.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): BelayDatabase =
    // Pre-release, no shipped users yet — destructive migration is fine until the schema needs
    // to survive a real upgrade; switch to real Migrations before the first Play Store release.
    Room.databaseBuilder(context, BelayDatabase::class.java, "belay.db").fallbackToDestructiveMigration(true).build()

  @Provides fun provideUserDao(db: BelayDatabase): UserDao = db.userDao()

  @Provides fun provideChallengeDao(db: BelayDatabase): ChallengeDao = db.challengeDao()

  @Provides fun provideHabitDao(db: BelayDatabase): HabitDao = db.habitDao()

  @Provides fun provideCheckInDao(db: BelayDatabase): CheckInDao = db.checkInDao()

  @Provides fun providePairingDao(db: BelayDatabase): PairingDao = db.pairingDao()

  @Provides fun provideInteractionDao(db: BelayDatabase): InteractionDao = db.interactionDao()

  @Provides fun provideRecapDao(db: BelayDatabase): RecapDao = db.recapDao()
}
