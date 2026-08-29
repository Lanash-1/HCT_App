package com.codigitech.belay.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codigitech.belay.data.local.dao.CheckInDao
import com.codigitech.belay.data.remote.CheckInRemoteDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CheckInSyncEntryPoint {
  fun checkInDao(): CheckInDao

  fun checkInRemoteDataSource(): CheckInRemoteDataSource
}

/** Retries every not-yet-synced check-in — runs only once WorkManager's network constraint is satisfied (PRD §6.6). */
class CheckInSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val entryPoint = EntryPointAccessors.fromApplication(applicationContext, CheckInSyncEntryPoint::class.java)
    val checkInDao = entryPoint.checkInDao()
    val remoteDataSource = entryPoint.checkInRemoteDataSource()

    val unsynced = checkInDao.getUnsynced()
    var allSucceeded = true
    for (checkIn in unsynced) {
      val succeeded = runCatching { remoteDataSource.upsert(checkIn) }.isSuccess
      if (succeeded) {
        checkInDao.upsert(checkIn.copy(synced = true))
      } else {
        allSucceeded = false
      }
    }
    return if (allSucceeded) Result.success() else Result.retry()
  }
}
