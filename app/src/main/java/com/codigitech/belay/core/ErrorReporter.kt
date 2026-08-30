package com.codigitech.belay.core

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject

/**
 * Testability seam around Crashlytics (PRD §6.8) — records the non-fatal failures this app
 * otherwise swallows silently (best-effort remote writes: a Firestore hiccup must never block the
 * user, but that shouldn't mean nobody ever finds out it happened).
 */
interface ErrorReporter {
  fun recordException(throwable: Throwable)

  /** Tags subsequent reports with the signed-in user, or clears that tag on sign-out (pass null). */
  fun identify(userId: String?)
}

/**
 * Runs a best-effort remote write, reporting a failure instead of dropping it on the floor.
 *
 * Only for writes with no retry path — a failure here means the data never reaches Firestore at
 * all. Check-ins deliberately don't use this: they queue in WorkManager (PRD §6.6), so an offline
 * failure there is an expected, recoverable step, not something to page anyone about.
 */
internal inline fun <T> ErrorReporter.bestEffort(block: () -> T): Result<T> = runCatching(block).onFailure(::recordException)

class CrashlyticsErrorReporter @Inject constructor(private val crashlytics: FirebaseCrashlytics) : ErrorReporter {
  override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)

  override fun identify(userId: String?) = crashlytics.setUserId(userId.orEmpty())
}
