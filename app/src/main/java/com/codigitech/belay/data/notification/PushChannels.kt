package com.codigitech.belay.data.notification

/**
 * One notification channel per push event type (docs/TECH_STACK.md §4) — so a challenger can
 * mute nudges from Android's own settings without also muting cheers, and so each push can be
 * routed to the right in-app surface.
 *
 * The [id]s are a contract with backend/functions/lib/buildPushMessage.js: Android silently drops
 * a notification naming a channel the app never created, so the two lists must stay identical.
 */
data class PushChannel(val id: String, val displayName: String, val description: String)

object PushChannels {
  val DAY_COMPLETE =
    PushChannel("belay_day_complete", "Challenger finished the day", "Tells you when someone you witness checks off their last habit")
  val CHEER = PushChannel("belay_cheer", "Cheers", "When your witness cheers you on")
  val NUDGE = PushChannel("belay_nudge", "Nudges", "When your witness nudges you — at most one a day")
  val RECAP_READY = PushChannel("belay_recap_ready", "Weekly recap", "When your Sunday recap is ready to look at")

  val all = listOf(DAY_COMPLETE, CHEER, NUDGE, RECAP_READY)

  private val byType =
    mapOf("day_complete" to DAY_COMPLETE, "cheer" to CHEER, "nudge" to NUDGE, "recap_ready" to RECAP_READY)

  /** Null for a type this app version doesn't know — a newer backend must not be able to crash an older client. */
  fun forType(type: String?): PushChannel? = byType[type]
}
