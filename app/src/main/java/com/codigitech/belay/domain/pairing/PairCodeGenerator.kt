package com.codigitech.belay.domain.pairing

import kotlin.random.Random

/**
 * Generates the 4-character pairing code shown in onboarding (PRD §5.1, e.g. "7K42"). Excludes
 * visually ambiguous characters (0/O, 1/I/L) since the code is read and typed by hand.
 */
class PairCodeGenerator(private val random: Random = Random.Default) {
  fun generate(): String = buildString { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

  companion object {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val CODE_LENGTH = 4
  }
}
