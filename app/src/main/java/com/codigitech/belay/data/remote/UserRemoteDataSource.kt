package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.UserEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Firestore-backed access to the `users` collection (DATA_MODEL.md), doc ID = Firebase UID. */
interface UserRemoteDataSource {
  suspend fun get(userId: String): UserEntity?

  suspend fun upsert(user: UserEntity)
}

private const val COLLECTION = "users"

class FirestoreUserRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : UserRemoteDataSource {

  override suspend fun get(userId: String): UserEntity? =
    firestore.collection(COLLECTION).document(userId).get().await().toUserEntity()

  // Merged rather than replaced so a field this client version doesn't know about survives a
  // profile edit — a plain set() silently deletes anything absent from the map.
  override suspend fun upsert(user: UserEntity) {
    firestore.collection(COLLECTION).document(user.userId).set(user.toFirestoreMap(), SetOptions.merge()).await()
  }
}

private fun UserEntity.toFirestoreMap(): Map<String, Any?> =
  mapOf(
    "user_id" to userId,
    "display_name" to displayName,
    "pair_code" to pairCode,
    "default_mode" to defaultMode,
    "theme_pref" to themePref,
    "notif_daily_reminder_time" to notifDailyReminderTime,
    "notif_allow_nudge" to notifAllowNudge,
    "created_at" to createdAt,
  )

private fun DocumentSnapshot.toUserEntity(): UserEntity? {
  if (!exists()) return null
  val displayName = getString("display_name") ?: return null
  val pairCode = getString("pair_code") ?: return null
  val defaultMode = getString("default_mode") ?: return null
  val themePref = getString("theme_pref") ?: return null
  val notifAllowNudge = getBoolean("notif_allow_nudge") ?: return null
  val createdAt = getLong("created_at") ?: return null
  return UserEntity(
    userId = id,
    displayName = displayName,
    pairCode = pairCode,
    defaultMode = defaultMode,
    themePref = themePref,
    notifDailyReminderTime = getString("notif_daily_reminder_time"),
    notifAllowNudge = notifAllowNudge,
    createdAt = createdAt,
  )
}
