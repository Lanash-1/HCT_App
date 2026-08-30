package com.codigitech.belay.ui.onboarding

/** Centralized user-facing copy for onboarding/role-pick/pairing (see CLAUDE.md conventions). */
object OnboardingCopy {
  const val HEADLINE = "Habits stick when someone's watching."
  const val SUBTITLE = "Start by choosing a role. You can be both later; it's a switch in your profile, not a second account."
  const val ROLE_CHALLENGER_TITLE = "I'm taking the challenge"
  const val ROLE_CHALLENGER_DETAIL = "Up to five habits a day, one person holding you to all of them."
  const val ROLE_WITNESS_TITLE = "I'm the witness"
  const val ROLE_WITNESS_DETAIL = "You watch a friend's stack. No streak of your own to protect."
  const val SHARE_CODE_LABEL = "Share this code with your witness"
  const val PAIR_CODE_LABEL = "Pair with a code"
  const val PAIR_CODE_HINT = "Enter the code your challenger shared"
  const val PAIR_BUTTON = "Pair"
  const val PAIRED_MESSAGE = "You're paired."
  const val PAIR_CODE_INVALID = "That code isn't valid, or it's already been used."
  const val PAIR_CODE_NETWORK_ERROR = "Couldn't reach the server — check your connection and try again."
  const val SHARE_LINK_LABEL = "Send an invite link"
  const val SHARE_LINK_CHOOSER_TITLE = "Invite your witness"
  const val CONTINUE = "Continue"

  fun shareLinkMessage(url: String): String = "Be my witness on Belay — you'll see every habit I check off, and every one I don't: $url"
}
