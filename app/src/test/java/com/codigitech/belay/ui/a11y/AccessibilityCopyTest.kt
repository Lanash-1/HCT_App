package com.codigitech.belay.ui.a11y

import com.codigitech.belay.ui.recap.RecapCopy
import com.codigitech.belay.ui.today.TodayCopy
import com.codigitech.belay.ui.watching.WatchingCopy
import com.codigitech.belay.ui.witnessdetail.WitnessDetailCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §7: screen-reader labels on the icon-only controls — the ring and the check dots. Each of
 * those renders as a shape or a bare number, which a screen reader can't say anything useful
 * about on its own.
 */
class AccessibilityCopyTest {

  @Test
  fun `the progress ring says what its fraction means`() {
    // Rendered as "3/5" inside a drawn arc, which reads aloud as "three fifths" at best.
    assertEquals("3 of 5 habits checked off today", TodayCopy.progressRingDescription(checked = 3, total = 5))
  }

  @Test
  fun `a full ring says the day is complete`() {
    assertEquals("All 5 habits checked off today", TodayCopy.progressRingDescription(checked = 5, total = 5))
  }

  @Test
  fun `an empty challenge's ring does not claim a completed day`() {
    assertEquals("No habits yet", TodayCopy.progressRingDescription(checked = 0, total = 0))
  }

  @Test
  fun `a habit row announces its name, state and streak as one thing`() {
    assertEquals(
      "Run 3km, before 8am. Checked off. 4 day streak.",
      TodayCopy.habitRowDescription(name = "Run 3km", detail = "before 8am", streak = 4, checked = true),
    )
    assertEquals(
      "Read. Not checked off yet. 1 day streak.",
      TodayCopy.habitRowDescription(name = "Read", detail = null, streak = 1, checked = false),
    )
  }

  @Test
  fun `a habit with no streak yet says so rather than "0 day streak"`() {
    assertTrue(TodayCopy.habitRowDescription("Read", null, streak = 0, checked = false).endsWith("No streak yet."))
  }

  @Test
  fun `a witness sees each habit's state spelled out, not just a time or a dash`() {
    assertEquals("Run 3km, checked off at 6:42 am", WatchingCopy.habitStatusDescription("Run 3km", "6:42 am", checkedToday = true))
    assertEquals("Run 3km, not checked off yet", WatchingCopy.habitStatusDescription("Run 3km", "—", checkedToday = false))
  }

  @Test
  fun `the witness count pill says what the two numbers are`() {
    assertEquals("2 of 5 habits done", WatchingCopy.countPillDescription(done = 2, total = 5))
  }

  @Test
  fun `the witness detail habit rows are labelled the same way`() {
    assertEquals("Read, checked off at 9:05 pm", WitnessDetailCopy.habitStatusDescription("Read", "9:05 pm", checkedToday = true))
    assertEquals("Read, not checked off yet", WitnessDetailCopy.habitStatusDescription("Read", "—", checkedToday = false))
  }

  @Test
  fun `a recap's seven-day grid is described rather than read as seven unlabelled boxes`() {
    assertEquals(
      "Run 3km: 6 of 7 days. Missed Wednesday.",
      RecapCopy.habitGridDescription("Run 3km", "6/7", listOf(true, true, false, true, true, true, true)),
    )
  }

  @Test
  fun `a perfect week's grid says so instead of listing nothing`() {
    assertEquals("Run 3km: 7 of 7 days. Every day.", RecapCopy.habitGridDescription("Run 3km", "7/7", List(7) { true }))
  }

  @Test
  fun `several missed days are all named`() {
    assertEquals(
      "Read: 5 of 7 days. Missed Monday, Thursday.",
      RecapCopy.habitGridDescription("Read", "5/7", listOf(false, true, true, false, true, true, true)),
    )
  }
}
