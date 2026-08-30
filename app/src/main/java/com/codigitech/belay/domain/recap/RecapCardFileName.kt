package com.codigitech.belay.domain.recap

private const val MAX_LENGTH = 100
private const val EXTENSION = ".png"
private const val FALLBACK = "Belay-recap$EXTENSION"

/**
 * File name for a saved or shared weekly recap card (PRD §5.4).
 *
 * Challenge titles are free text, so this can't be a straight interpolation: everything outside a
 * conservative safe set is collapsed to hyphens, and the result is capped — some filesystems and
 * share targets reject long names, and a 400-character habit title is a legal challenge title.
 */
fun recapCardFileName(challengeTitle: String, weekRangeLabel: String): String {
  val slug =
    "$challengeTitle $weekRangeLabel"
      .map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }
      .joinToString("")
      .split("-")
      .filter { it.isNotBlank() }
      .joinToString("-")

  if (slug.isBlank()) return FALLBACK

  val stem = "Belay-$slug".take(MAX_LENGTH - EXTENSION.length).trimEnd('-')
  return "$stem$EXTENSION"
}
