package com.codigitech.belay.di

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.core.SystemBelayClock
import com.codigitech.belay.core.UuidIdGenerator
import com.codigitech.belay.data.repository.PairingRepository
import com.codigitech.belay.data.repository.PairingRepositoryImpl
import com.codigitech.belay.domain.pairing.PairCodeGenerator
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

  @Binds abstract fun bindClock(impl: SystemBelayClock): BelayClock

  @Binds abstract fun bindIdGenerator(impl: UuidIdGenerator): IdGenerator

  companion object {
    @Provides @Singleton fun providePairCodeGenerator(): PairCodeGenerator = PairCodeGenerator()
  }
}
