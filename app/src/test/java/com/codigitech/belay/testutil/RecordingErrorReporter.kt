package com.codigitech.belay.testutil

import com.codigitech.belay.core.ErrorReporter

/** Captures what would have gone to Crashlytics (PRD §6.8) so tests can assert on it. */
class RecordingErrorReporter : ErrorReporter {
  val recorded = mutableListOf<Throwable>()
  val identified = mutableListOf<String?>()

  override fun recordException(throwable: Throwable) {
    recorded.add(throwable)
  }

  override fun identify(userId: String?) {
    identified.add(userId)
  }
}
