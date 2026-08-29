package com.codigitech.belay.domain.pairing

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairCodeGeneratorTest {

  @Test
  fun `generated code is exactly 4 characters`() {
    val code = PairCodeGenerator(Random(seed = 1)).generate()
    assertEquals(4, code.length)
  }

  @Test
  fun `generated code only uses the unambiguous alphabet`() {
    val generator = PairCodeGenerator(Random(seed = 42))
    repeat(200) {
      val code = generator.generate()
      assertTrue("'$code' contains a character outside the allowed alphabet", code.all { it in PairCodeGenerator.ALPHABET })
    }
  }

  @Test
  fun `excludes visually ambiguous characters`() {
    val forbidden = setOf('0', 'O', '1', 'I', 'L')
    assertTrue(forbidden.none { it in PairCodeGenerator.ALPHABET })
  }

  @Test
  fun `same seed produces the same code (deterministic given a fixed random source)`() {
    assertEquals(PairCodeGenerator(Random(seed = 7)).generate(), PairCodeGenerator(Random(seed = 7)).generate())
  }
}
