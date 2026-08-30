package com.codigitech.belay.data.repository

import com.codigitech.belay.core.ErrorReporter
import com.codigitech.belay.core.bestEffort
import com.codigitech.belay.data.notification.PushNotificationService
import com.codigitech.belay.data.remote.PushTokenRemoteDataSource
import javax.inject.Inject

/**
 * Binds this device to the signed-in account for push (docs/TECH_STACK.md §4): FCM is the
 * fallback that delivers a check-in or a cheer to the tray when the recipient's app isn't open.
 *
 * Every path here is best-effort. A device with no Play Services, or one that's offline at
 * sign-in, simply doesn't get tray notifications — the app itself keeps working, since live state
 * comes from Firestore listeners rather than from push.
 */
interface PushTokenRepository {
  suspend fun register(userId: String)

  suspend fun unregister(userId: String)
}

class PushTokenRepositoryImpl
@Inject
constructor(
  private val pushNotificationService: PushNotificationService,
  private val remoteDataSource: PushTokenRemoteDataSource,
  private val errorReporter: ErrorReporter,
) : PushTokenRepository {

  override suspend fun register(userId: String) {
    errorReporter.bestEffort {
      val token = pushNotificationService.currentToken()
      if (token.isNullOrBlank()) return@bestEffort
      remoteDataSource.addPushToken(userId, token)
    }
  }

  override suspend fun unregister(userId: String) {
    errorReporter.bestEffort {
      val token = pushNotificationService.currentToken()
      if (!token.isNullOrBlank()) remoteDataSource.removePushToken(userId, token)
      // Dropping the device's own token too: without this, FCM would keep handing this device the
      // same token, and a re-register by a different account would silently resurrect it server-side.
      pushNotificationService.deleteToken()
    }
  }
}
