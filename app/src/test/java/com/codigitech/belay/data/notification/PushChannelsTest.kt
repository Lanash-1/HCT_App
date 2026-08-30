package com.codigitech.belay.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushChannelsTest {

  @Test
  fun `channel ids match the ones the backend sends`() {
    // These strings are the contract with backend/functions/lib/buildPushMessage.js — a push
    // naming a channel this app never created is silently dropped by Android.
    assertEquals("belay_day_complete", PushChannels.DAY_COMPLETE.id)
    assertEquals("belay_cheer", PushChannels.CHEER.id)
    assertEquals("belay_nudge", PushChannels.NUDGE.id)
    assertEquals("belay_recap_ready", PushChannels.RECAP_READY.id)
  }

  @Test
  fun `every push type the backend can send resolves to a channel`() {
    listOf("day_complete", "cheer", "nudge", "recap_ready").forEach { type -> assertNotNull(type, PushChannels.forType(type)) }
  }

  @Test
  fun `an unrecognised type resolves to nothing rather than a wrong channel`() {
    assertNull(PushChannels.forType("something_new_from_a_later_backend"))
  }

  @Test
  fun `every channel is user-visibly named and described, so notification settings are legible`() {
    PushChannels.all.forEach { channel ->
      assertEquals(channel.id, true, channel.displayName.isNotBlank())
      assertEquals(channel.id, true, channel.description.isNotBlank())
    }
  }
}
