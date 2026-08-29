package com.codigitech.belay.domain.user

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {

  @Test
  fun `takes the email local-part and capitalizes its first letter`() {
    assertEquals("Arun", displayNameFromEmail("arun@example.com"))
  }

  @Test
  fun `leaves an already-capitalized local-part as-is`() {
    assertEquals("Ana", displayNameFromEmail("Ana@example.com"))
  }

  @Test
  fun `falls back to a generic name for a blank or malformed email`() {
    assertEquals("Belay user", displayNameFromEmail(""))
    assertEquals("Belay user", displayNameFromEmail("@example.com"))
  }
}
