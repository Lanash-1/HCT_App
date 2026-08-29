package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.PairingEntity
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed lookup for the `pairings` collection (DATA_MODEL.md). A pairing is created by
 * one device and completed by another, so it can never be resolved from local Room alone — this
 * is the source of truth `PairingRepository` reads/writes through.
 */
interface PairingRemoteDataSource {
  suspend fun upsert(pairing: PairingEntity)

  suspend fun findPendingByCode(pairCode: String): PairingEntity?
}

private const val COLLECTION = "pairings"

class FirestorePairingRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : PairingRemoteDataSource {

  override suspend fun upsert(pairing: PairingEntity) {
    firestore.collection(COLLECTION).document(pairing.pairingId).set(pairing.toFirestoreMap()).await()
  }

  override suspend fun findPendingByCode(pairCode: String): PairingEntity? {
    val snapshot =
      firestore
        .collection(COLLECTION)
        .whereEqualTo("pair_code", pairCode)
        .whereEqualTo("status", "pending")
        .limit(1)
        .get()
        .await()
    return snapshot.documents.firstOrNull()?.toPairingEntity()
  }
}

private fun PairingEntity.toFirestoreMap(): Map<String, Any?> =
  mapOf(
    "pairing_id" to pairingId,
    "pair_code" to pairCode,
    "from_user_id" to fromUserId,
    "to_user_id" to toUserId,
    "status" to status,
    "created_at" to createdAt,
  )

private fun com.google.firebase.firestore.DocumentSnapshot.toPairingEntity(): PairingEntity? {
  val pairingId = getString("pairing_id") ?: return null
  val pairCode = getString("pair_code") ?: return null
  val fromUserId = getString("from_user_id") ?: return null
  val status = getString("status") ?: return null
  val createdAt = getLong("created_at") ?: return null
  return PairingEntity(
    pairingId = pairingId,
    pairCode = pairCode,
    fromUserId = fromUserId,
    toUserId = getString("to_user_id"),
    status = status,
    createdAt = createdAt,
  )
}
