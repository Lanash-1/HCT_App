package com.codigitech.belay.data.notification

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class FirebaseMessagingPushService @Inject constructor(private val firebaseMessaging: FirebaseMessaging) : PushNotificationService {

  override suspend fun currentToken(): String? = firebaseMessaging.token.await()

  override suspend fun deleteToken() {
    firebaseMessaging.deleteToken().await()
  }
}
