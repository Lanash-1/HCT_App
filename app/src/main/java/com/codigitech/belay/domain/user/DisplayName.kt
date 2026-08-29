package com.codigitech.belay.domain.user

/** No display-name field is collected at sign-up (email + password only, see TECH_STACK.md §10), so onboarding derives a placeholder from the email's local-part; Profile lets the user change it later. */
fun displayNameFromEmail(email: String): String {
  val localPart = email.substringBefore('@')
  if (localPart.isBlank()) return "Belay user"
  return localPart.replaceFirstChar { it.uppercase() }
}
