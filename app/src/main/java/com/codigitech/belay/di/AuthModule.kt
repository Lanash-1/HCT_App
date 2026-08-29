package com.codigitech.belay.di

import com.codigitech.belay.data.repository.AuthRepository
import com.codigitech.belay.data.repository.FirebaseAuthRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
  @Binds abstract fun bindAuthRepository(impl: FirebaseAuthRepositoryImpl): AuthRepository

  companion object {
    @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
  }
}
