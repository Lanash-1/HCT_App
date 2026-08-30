package com.codigitech.belay.domain.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingDeepLinkTest {

  @Test
  fun `reads the code out of an App Link`() {
    assertEquals("7K42", PairingDeepLink.parseCode("https://belay.codigitech.com/pair/7K42"))
  }

  @Test
  fun `reads the code out of the custom-scheme fallback`() {
    // The fallback matters on devices where App Links verification hasn't completed (or the user
    // picked a different handler) — the invite still has to work.
    assertEquals("7K42", PairingDeepLink.parseCode("belay://pair/7K42"))
  }

  @Test
  fun `normalises a lowercased code, since links get retyped and mangled by chat apps`() {
    assertEquals("7K42", PairingDeepLink.parseCode("https://belay.codigitech.com/pair/7k42"))
  }

  @Test
  fun `tolerates a trailing slash and query string`() {
    assertEquals("7K42", PairingDeepLink.parseCode("https://belay.codigitech.com/pair/7K42/?utm_source=whatsapp"))
  }

  @Test
  fun `ignores a link that is not a pairing link`() {
    assertNull(PairingDeepLink.parseCode("https://belay.codigitech.com/privacy"))
    assertNull(PairingDeepLink.parseCode("https://belay.codigitech.com/pair/"))
    assertNull(PairingDeepLink.parseCode(null))
    assertNull(PairingDeepLink.parseCode(""))
  }

  @Test
  fun `ignores a pairing path on someone else's domain`() {
    // A link is a capability here: following an attacker-chosen host would let any site hand the
    // app a code to redeem.
    assertNull(PairingDeepLink.parseCode("https://not-belay.example.com/pair/7K42"))
  }

  @Test
  fun `rejects a code that is not the 4-character format, rather than sending junk to the server`() {
    assertNull(PairingDeepLink.parseCode("https://belay.codigitech.com/pair/7K42EXTRA"))
    assertNull(PairingDeepLink.parseCode("https://belay.codigitech.com/pair/7K"))
    assertNull(PairingDeepLink.parseCode("https://belay.codigitech.com/pair/7K-2"))
  }

  @Test
  fun `builds a shareable link for a code`() {
    assertEquals("https://belay.codigitech.com/pair/7K42", PairingDeepLink.shareUrl("7K42"))
  }
}
