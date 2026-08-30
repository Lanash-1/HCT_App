package com.codigitech.belay.data.notification

/**
 * Provider-agnostic wrapper around FCM token access (CLAUDE.md: no `com.google.firebase.*`
 * import outside an `Impl` class or a DI module).
 */
interface PushNotificationService {
  /** This device's current push token, or null if the device can't get one (e.g. no Play Services). */
  suspend fun currentToken(): String?

  /** Drops this device's token entirely — used on sign-out so pushes stop reaching a signed-out device. */
  suspend fun deleteToken()
}
