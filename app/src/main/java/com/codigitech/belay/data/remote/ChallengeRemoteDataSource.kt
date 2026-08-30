package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed read/write path for the `challenges` collection (DATA_MODEL.md) — lets a
 * witness's device see a new challenge, and lets the challenger's own device pick up
 * server-computed fields (grace/perfect-days) written by the dayRollover Cloud Function.
 */
interface ChallengeRemoteDataSource {
  suspend fun upsert(challenge: ChallengeEntity)

  fun observe(challengeId: String): Flow<ChallengeEntity?>
}

private const val COLLECTION = "challenges"

class FirestoreChallengeRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : ChallengeRemoteDataSource {

  override suspend fun upsert(challenge: ChallengeEntity) {
    firestore.collection(COLLECTION).document(challenge.challengeId).set(challenge.toFirestoreMap()).await()
  }

  override fun observe(challengeId: String): Flow<ChallengeEntity?> = callbackFlow {
    val registration =
      firestore.collection(COLLECTION).document(challengeId).addSnapshotListener { snapshot, _ ->
        trySend(snapshot?.takeIf { it.exists() }?.toChallengeEntity())
      }
    awaitClose { registration.remove() }
  }
}

private fun DocumentSnapshot.toChallengeEntity(): ChallengeEntity? {
  val challengeId = getString("challenge_id") ?: return null
  val challengerUserId = getString("challenger_user_id") ?: return null
  val title = getString("title") ?: return null
  val status = getString("status") ?: return null
  return ChallengeEntity(
    challengeId = challengeId,
    challengerUserId = challengerUserId,
    // Absent when nobody has accepted yet, or when the witness deleted their account — the
    // challenge is still the challenger's, so it must survive the read (PRD §6.7).
    witnessUserId = getString("witness_user_id"),
    title = title,
    durationDays = (getLong("duration_days") ?: 0L).toInt(),
    graceDaysTotal = (getLong("grace_days_total") ?: 0L).toInt(),
    graceDaysUsed = (getLong("grace_days_used") ?: 0L).toInt(),
    perfectDays = (getLong("perfect_days") ?: 0L).toInt(),
    startDate = getLong("start_date") ?: 0L,
    status = status,
  )
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
