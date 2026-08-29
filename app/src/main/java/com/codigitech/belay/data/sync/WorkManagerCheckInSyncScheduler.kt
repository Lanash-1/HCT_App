package com.codigitech.belay.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codigitech.belay.data.repository.CheckInSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerCheckInSyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) : CheckInSyncScheduler {

  override fun scheduleSync() {
    val request =
      OneTimeWorkRequestBuilder<CheckInSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORK_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
    // KEEP: if a retry is already queued, don't pile on another — the worker syncs every unsynced
    // row it finds, so one pending job covers whatever's accumulated since it was scheduled.
    WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
  }

  companion object {
    const val WORK_NAME = "check_in_sync"
    private const val WORK_BACKOFF_MILLIS = 30_000L
  }
}
