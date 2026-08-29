package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.HabitEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed read/write path for the `habits` collection (DATA_MODEL.md) — the read side
 * lets a device pick up server-computed fields (current_streak, streak_broken_at) written by the
 * dayRollover Cloud Function.
 */
interface HabitRemoteDataSource {
  suspend fun upsertAll(habits: List<HabitEntity>)

  fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>>
}

private const val COLLECTION = "habits"

class FirestoreHabitRemoteDataSource
@Inject
constructor(private val firestore: FirebaseFirestore) : HabitRemoteDataSource {

  override suspend fun upsertAll(habits: List<HabitEntity>) {
    val batch = firestore.batch()
    habits.forEach { habit -> batch.set(firestore.collection(COLLECTION).document(habit.habitId), habit.toFirestoreMap()) }
    batch.commit().await()
  }

  override fun observeForChallenge(challengeId: String): Flow<List<HabitEntity>> = callbackFlow {
    val registration =
      firestore.collection(COLLECTION).whereEqualTo("challenge_id", challengeId).addSnapshotListener { snapshot, _ ->
        trySend(snapshot?.documents?.mapNotNull { it.toHabitEntity() }.orEmpty())
      }
    awaitClose { registration.remove() }
  }
}

private fun HabitEntity.toFirestoreMap(): Map<String, Any?> =
  mapOf(
    "habit_id" to habitId,
    "challenge_id" to challengeId,
    "name" to name,
    "detail" to detail,
    "icon" to icon,
    "reminder_time" to reminderTime,
    "sort_order" to sortOrder,
    "current_streak" to currentStreak,
    "streak_broken_at" to streakBrokenAt,
  )

private fun DocumentSnapshot.toHabitEntity(): HabitEntity? {
  val habitId = getString("habit_id") ?: return null
  val challengeId = getString("challenge_id") ?: return null
  val name = getString("name") ?: return null
  return HabitEntity(
    habitId = habitId,
    challengeId = challengeId,
    name = name,
    detail = getString("detail"),
    icon = getString("icon"),
    reminderTime = getString("reminder_time"),
    sortOrder = (getLong("sort_order") ?: 0L).toInt(),
    currentStreak = (getLong("current_streak") ?: 0L).toInt(),
    streakBrokenAt = getString("streak_broken_at"),
  )
}
