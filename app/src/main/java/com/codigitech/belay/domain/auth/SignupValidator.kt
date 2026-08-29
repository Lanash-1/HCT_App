package com.codigitech.belay.domain.auth

enum class SignupValidationError {
  BlankEmail,
  InvalidEmail,
  PasswordTooShort,
}

private const val MIN_PASSWORD_LENGTH = 6

/**
 * Lightweight client-side sanity check before calling Firebase Auth, which does the
 * authoritative validation server-side. Not a full RFC email grammar — just enough to catch
 * an empty/obviously-wrong field before a round trip.
 */
fun validateSignup(email: String, password: String): SignupValidationError? =
  when {
    email.isBlank() -> SignupValidationError.BlankEmail
    !email.contains('@') -> SignupValidationError.InvalidEmail
    password.length < MIN_PASSWORD_LENGTH -> SignupValidationError.PasswordTooShort
    else -> null
  }
