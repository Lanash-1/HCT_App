package com.codigitech.belay.di

import android.app.AlarmManager
import android.content.Context
import com.codigitech.belay.data.notification.AlarmManagerReminderScheduler
import com.codigitech.belay.data.notification.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
  @Binds abstract fun bindReminderScheduler(impl: AlarmManagerReminderScheduler): ReminderScheduler

  companion object {
    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)
  }
}
