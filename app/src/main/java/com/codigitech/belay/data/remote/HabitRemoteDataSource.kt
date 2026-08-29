package com.codigitech.belay.data.remote

import com.codigitech.belay.data.local.entity.HabitEntity
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Firestore-backed write path for the `habits` collection (DATA_MODEL.md). */
interface HabitRemoteDataSource {
  suspend fun upsertAll(habits: List<HabitEntity>)
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
  )
