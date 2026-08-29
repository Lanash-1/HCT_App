package com.codigitech.belay.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignupValidatorTest {

  @Test
  fun `valid email and password pass`() {
    assertNull(validateSignup(email = "arun@example.com", password = "hunter22"))
  }

  @Test
  fun `blank email is rejected`() {
    assertEquals(SignupValidationError.BlankEmail, validateSignup(email = "", password = "hunter22"))
  }

  @Test
  fun `email without an at-sign is rejected`() {
    assertEquals(SignupValidationError.InvalidEmail, validateSignup(email = "not-an-email", password = "hunter22"))
  }

  @Test
  fun `password shorter than 6 characters is rejected`() {
    assertEquals(SignupValidationError.PasswordTooShort, validateSignup(email = "arun@example.com", password = "abc12"))
  }

  @Test
  fun `password of exactly 6 characters passes`() {
    assertNull(validateSignup(email = "arun@example.com", password = "abc123"))
  }

  @Test
  fun `email is checked before password`() {
    assertEquals(SignupValidationError.BlankEmail, validateSignup(email = "", password = ""))
  }
}
