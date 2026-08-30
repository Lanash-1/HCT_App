package com.codigitech.belay.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * This device's FCM registration tokens (docs/DATA_MODEL.md `users/{id}/private/push`).
 *
 * Deliberately not a field on the `users` document: that document is readable by any signed-in
 * user (a witness needs to resolve a challenger's display name), and Firestore rules can't
 * restrict reads per field — so a token stored there would be readable by everyone. The
 * subdocument is owner-only, and Cloud Functions reach it through the Admin SDK.
 */
interface PushTokenRemoteDataSource {
  suspend fun addPushToken(userId: String, token: String)

  suspend fun removePushToken(userId: String, token: String)
}

private const val COLLECTION = "users"
private const val PRIVATE_COLLECTION = "private"
private const val PUSH_DOC = "push"
private const val FIELD = "fcm_tokens"

class FirestorePushTokenRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : PushTokenRemoteDataSource {

  // arrayUnion/arrayRemove rather than read-modify-write: one user can have several devices, and
  // two of them registering at once must not clobber each other's token.
  override suspend fun addPushToken(userId: String, token: String) {
    pushDoc(userId).set(mapOf(FIELD to FieldValue.arrayUnion(token)), SetOptions.merge()).await()
  }

  override suspend fun removePushToken(userId: String, token: String) {
    pushDoc(userId).set(mapOf(FIELD to FieldValue.arrayRemove(token)), SetOptions.merge()).await()
  }

  private fun pushDoc(userId: String) =
    firestore.collection(COLLECTION).document(userId).collection(PRIVATE_COLLECTION).document(PUSH_DOC)
}
