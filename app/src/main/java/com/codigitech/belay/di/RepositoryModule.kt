package com.codigitech.belay.di

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.CrashlyticsErrorReporter
import com.codigitech.belay.core.ErrorReporter
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.core.LocalDataReset
import com.codigitech.belay.core.RoomLocalDataReset
import com.codigitech.belay.core.SystemBelayClock
import com.codigitech.belay.core.UuidIdGenerator
import com.codigitech.belay.data.repository.ChallengeRepository
import com.codigitech.belay.data.repository.ChallengeRepositoryImpl
import com.codigitech.belay.data.repository.CheckInRepository
import com.codigitech.belay.data.repository.CheckInRepositoryImpl
import com.codigitech.belay.data.repository.CheckInSyncScheduler
import com.codigitech.belay.data.sync.WorkManagerCheckInSyncScheduler
import com.codigitech.belay.data.repository.HabitRepository
import com.codigitech.belay.data.repository.HabitRepositoryImpl
import com.codigitech.belay.data.repository.InteractionRepository
import com.codigitech.belay.data.repository.InteractionRepositoryImpl
import com.codigitech.belay.data.remote.ChallengeRemoteDataSource
import com.codigitech.belay.data.remote.CheckInRemoteDataSource
import com.codigitech.belay.data.remote.FirestoreChallengeRemoteDataSource
import com.codigitech.belay.data.remote.FirestoreCheckInRemoteDataSource
import com.codigitech.belay.data.remote.FirestoreHabitRemoteDataSource
import com.codigitech.belay.data.remote.FirestorePairingRemoteDataSource
import com.codigitech.belay.data.remote.FirestoreUserRemoteDataSource
import com.codigitech.belay.data.remote.HabitRemoteDataSource
import com.codigitech.belay.data.remote.PairingRemoteDataSource
import com.codigitech.belay.data.remote.UserRemoteDataSource
import com.codigitech.belay.data.repository.PairingRepository
import com.codigitech.belay.data.repository.PairingRepositoryImpl
import com.codigitech.belay.data.repository.RecapRepository
import com.codigitech.belay.data.repository.RecapRepositoryImpl
import com.codigitech.belay.data.repository.UserRepository
import com.codigitech.belay.data.repository.UserRepositoryImpl
import com.codigitech.belay.domain.pairing.PairCodeGenerator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

  @Binds abstract fun bindPairingRemoteDataSource(impl: FirestorePairingRemoteDataSource): PairingRemoteDataSource

  @Binds abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

  @Binds abstract fun bindUserRemoteDataSource(impl: FirestoreUserRemoteDataSource): UserRemoteDataSource

  @Binds abstract fun bindChallengeRepository(impl: ChallengeRepositoryImpl): ChallengeRepository

  @Binds abstract fun bindChallengeRemoteDataSource(impl: FirestoreChallengeRemoteDataSource): ChallengeRemoteDataSource

  @Binds abstract fun bindInteractionRepository(impl: InteractionRepositoryImpl): InteractionRepository

  @Binds abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

  @Binds abstract fun bindHabitRemoteDataSource(impl: FirestoreHabitRemoteDataSource): HabitRemoteDataSource

  @Binds abstract fun bindRecapRepository(impl: RecapRepositoryImpl): RecapRepository

  @Binds abstract fun bindCheckInRepository(impl: CheckInRepositoryImpl): CheckInRepository

  @Binds abstract fun bindCheckInRemoteDataSource(impl: FirestoreCheckInRemoteDataSource): CheckInRemoteDataSource

  @Binds abstract fun bindClock(impl: SystemBelayClock): BelayClock

  @Binds abstract fun bindIdGenerator(impl: UuidIdGenerator): IdGenerator

  @Binds abstract fun bindLocalDataReset(impl: RoomLocalDataReset): LocalDataReset

  @Binds abstract fun bindCheckInSyncScheduler(impl: WorkManagerCheckInSyncScheduler): CheckInSyncScheduler

  @Binds abstract fun bindErrorReporter(impl: CrashlyticsErrorReporter): ErrorReporter

  companion object {
    @Provides @Singleton fun providePairCodeGenerator(): PairCodeGenerator = PairCodeGenerator()

    @Provides @Singleton fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton fun provideFirebaseCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
  }
}
