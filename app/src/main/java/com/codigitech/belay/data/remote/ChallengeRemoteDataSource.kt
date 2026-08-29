package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Firestore-backed write path for the `challenges` collection (DATA_MODEL.md) — lets a witness's device see a new challenge. */
interface ChallengeRemoteDataSource {
  suspend fun upsert(challenge: ChallengeEntity)
}

private const val COLLECTION = "challenges"

class FirestoreChallengeRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : ChallengeRemoteDataSource {

  override suspend fun upsert(challenge: ChallengeEntity) {
    firestore.collection(COLLECTION).document(challenge.challengeId).set(challenge.toFirestoreMap()).await()
  }
}

private fun ChallengeEntity.toFirestoreMap(): Map<String, Any?> =
  mapOf(
    "challenge_id" to challengeId,
    "challenger_user_id" to challengerUserId,
    "witness_user_id" to witnessUserId,
    "title" to title,
    "duration_days" to durationDays,
    "grace_days_total" to graceDaysTotal,
    "grace_days_used" to graceDaysUsed,
    "perfect_days" to perfectDays,
    "start_date" to startDate,
    "status" to status,
  )
