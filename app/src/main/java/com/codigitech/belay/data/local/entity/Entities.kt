package com.codigitech.belay.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mirrors DATA_MODEL.md `users`. Local cache of the signed-in user and anyone they interact with. */
@Entity(tableName = "users")
data class UserEntity(
  @PrimaryKey val userId: String,
  val displayName: String,
  val pairCode: String,
  val defaultMode: String, // "challenger" | "witness"
  val themePref: String, // "light" | "dark" | "system"
  val notifDailyReminderTime: String?, // HH:mm
  val notifAllowNudge: Boolean,
  val createdAt: Long,
)

/** Mirrors DATA_MODEL.md `challenges`. */
@Entity(
  tableName = "challenges",
  indices = [Index("challengerUserId"), Index("witnessUserId")],
)
data class ChallengeEntity(
  @PrimaryKey val challengeId: String,
  val challengerUserId: String,
  val witnessUserId: String,
  val title: String,
  val durationDays: Int,
  val graceDaysTotal: Int,
  val graceDaysUsed: Int,
  val perfectDays: Int, // running total for the whole challenge, written only by the dayRollover Catalyst Function
  val startDate: Long, // epoch day
  val status: String, // "active" | "completed" | "abandoned"
)

/** Mirrors DATA_MODEL.md `habits`. `currentStreak` is written only from Catalyst Function results. */
@Entity(
  tableName = "habits",
  foreignKeys = [
    ForeignKey(
      entity = ChallengeEntity::class,
      parentColumns = ["challengeId"],
      childColumns = ["challengeId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("challengeId")],
)
data class HabitEntity(
  @PrimaryKey val habitId: String,
  val challengeId: String,
  val name: String,
  val detail: String?,
  val icon: String?,
  val reminderTime: String?, // HH:mm, nullable
  val sortOrder: Int,
  val currentStreak: Int,
  val streakBrokenAt: String? = null, // ISO date, nullable — set by dayRollover only on the day grace exhaustion broke this habit's streak (PRD §6.2)
)

/** Mirrors DATA_MODEL.md `check_ins`. One row per habit per day. */
@Entity(
  tableName = "check_ins",
  foreignKeys = [
    ForeignKey(
      entity = HabitEntity::class,
      parentColumns = ["habitId"],
      childColumns = ["habitId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("habitId"), Index("challengeId"), Index(value = ["habitId", "date"], unique = true)],
)
data class CheckInEntity(
  @PrimaryKey val checkInId: String,
  val habitId: String,
  val challengeId: String, // denormalized for query-by-day
  val date: Long, // epoch day
  val done: Boolean,
  val checkedAt: Long?,
  val clientIdempotencyKey: String,
  val synced: Boolean = true, // local-only bookkeeping — false until the Firestore write succeeds (PRD §6.6)
)

/** Mirrors DATA_MODEL.md `pairings`. */
@Entity(tableName = "pairings", indices = [Index("pairCode")])
data class PairingEntity(
  @PrimaryKey val pairingId: String,
  val pairCode: String,
  val fromUserId: String,
  val toUserId: String?,
  val status: String, // "pending" | "paired" | "expired"
  val createdAt: Long,
)

/** Mirrors DATA_MODEL.md `interactions` — cheer/nudge events, also the witness-detail activity log. */
@Entity(
  tableName = "interactions",
  foreignKeys = [
    ForeignKey(
      entity = ChallengeEntity::class,
      parentColumns = ["challengeId"],
      childColumns = ["challengeId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("challengeId")],
)
data class InteractionEntity(
  @PrimaryKey val interactionId: String,
  val challengeId: String,
  val fromUserId: String,
  val type: String, // "cheer" | "nudge" | "checkin_summary"
  val date: Long, // epoch day
  val message: String,
  val createdAt: Long,
)

/** Mirrors DATA_MODEL.md `recaps`. `perHabitSummaryJson` holds the raw JSON blob from the server. */
@Entity(
  tableName = "recaps",
  foreignKeys = [
    ForeignKey(
      entity = ChallengeEntity::class,
      parentColumns = ["challengeId"],
      childColumns = ["challengeId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("challengeId")],
)
data class RecapEntity(
  @PrimaryKey val recapId: String,
  val challengeId: String,
  val weekStart: Long, // epoch day
  val weekEnd: Long, // epoch day
  val checkInsTotal: Int,
  val checkInsPossible: Int,
  val perfectDays: Int,
  val perHabitSummaryJson: String,
  val generatedAt: Long,
)
