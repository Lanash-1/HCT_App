package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.CheckInEntity
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Firestore-backed write path for the `check_ins` collection (DATA_MODEL.md) — lets a witness's device see today's check-ins. */
interface CheckInRemoteDataSource {
  suspend fun upsert(checkIn: CheckInEntity)
}

private const val COLLECTION = "check_ins"

class FirestoreCheckInRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : CheckInRemoteDataSource {

  override suspend fun upsert(checkIn: CheckInEntity) {
    firestore.collection(COLLECTION).document(checkIn.checkInId).set(checkIn.toFirestoreMap()).await()
  }
}

private fun CheckInEntity.toFirestoreMap(): Map<String, Any?> =
  mapOf(
    "check_in_id" to checkInId,
    "habit_id" to habitId,
    "challenge_id" to challengeId,
    // Room stores `date` as an epoch day (Long); the backend's dayRollover/weeklyRecap Cloud
    // Functions query check_ins.date as an ISO "yyyy-MM-dd" string — convert so those queries
    // actually match.
    "date" to LocalDate.ofEpochDay(date).toString(),
    "done" to done,
    "checked_at" to checkedAt,
    "client_idempotency_key" to clientIdempotencyKey,
  )
