package com.codigitech.belay.data.repository

/** Testability seam around enqueuing the offline check-in sync worker (PRD §6.6, TECH_STACK.md §5). */
interface CheckInSyncScheduler {
  /** Schedules a retry for whenever connectivity returns — safe to call repeatedly; only one retry stays queued. */
  fun scheduleSync()
}
