package com.codigitech.belay.domain.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapCardFileNameTest {

  @Test
  fun `names the file after the challenge and week`() {
    assertEquals("Belay-Morning-reset-Mar-2-8.png", recapCardFileName("Morning reset", "Mar 2–8"))
  }

  @Test
  fun `strips characters that are not legal in a file name`() {
    // Challenge titles are free text — a "/" would otherwise be read as a path separator.
    val name = recapCardFileName("Q1: run/walk 5k", "Mar 2–8")

    assertTrue(name, name.none { it in "/\\:*?\"<>|" })
  }

  @Test
  fun `always ends in png, whatever the title`() {
    assertTrue(recapCardFileName("***", "***").endsWith(".png"))
  }

  @Test
  fun `falls back to a usable name when the title is empty or all punctuation`() {
    assertEquals("Belay-recap.png", recapCardFileName("", ""))
    assertEquals("Belay-recap.png", recapCardFileName("///", "***"))
  }

  @Test
  fun `keeps the name short enough for any filesystem`() {
    val name = recapCardFileName("x".repeat(400), "y".repeat(400))

    assertTrue("$name is ${name.length} chars", name.length <= 100)
  }
}
