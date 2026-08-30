package com.codigitech.belay.domain.pairing

/**
 * The shareable pairing link (PRD §6.11, TECH_STACK.md §11): a challenger sends a link instead of
 * dictating a 4-character code, and it opens Belay straight to the pairing step with the code
 * filled in. The typed code stays the primary path — this is additive.
 *
 * Parsing is deliberately strict. A pairing code is a capability (redeeming one binds two
 * accounts), so anything not exactly a Belay pairing link for a well-formed code is ignored
 * rather than passed to the server: a host check stops an arbitrary site from handing the app a
 * code, and a format check stops junk from reaching the pairing lookup.
 *
 * NOTE: [HOST] is provisional — the domain still needs confirming and an `assetlinks.json` hosted
 * on it before App Links verification works. See docs/OPEN_DECISIONS.md.
 */
object PairingDeepLink {
  const val SCHEME = "belay"
  const val HOST = "belay.codigitech.com"
  const val PATH_PREFIX = "/pair"

  private val CODE_PATTERN = Regex("^[${PairCodeGenerator.ALPHABET}]{${PairCodeGenerator.CODE_LENGTH}}$")

  /** The pairing code carried by [url], or null if it isn't a Belay pairing link for a valid code. */
  fun parseCode(url: String?): String? {
    if (url.isNullOrBlank()) return null

    val withoutQuery = url.substringBefore('?').substringBefore('#')
    val body =
      when {
        withoutQuery.startsWith("https://$HOST$PATH_PREFIX/") -> withoutQuery.removePrefix("https://$HOST$PATH_PREFIX/")
        // Custom-scheme form has no host segment: belay://pair/CODE
        withoutQuery.startsWith("$SCHEME://pair/") -> withoutQuery.removePrefix("$SCHEME://pair/")
        else -> return null
      }

    val code = body.trim('/').uppercase()
    return if (CODE_PATTERN.matches(code)) code else null
  }

  fun shareUrl(pairCode: String): String = "https://$HOST$PATH_PREFIX/$pairCode"
}
