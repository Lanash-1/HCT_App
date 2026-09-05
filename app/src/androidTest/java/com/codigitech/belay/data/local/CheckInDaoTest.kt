package com.codigitech.belay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codigitech.belay.data.local.entity.ChallengeEntity
import com.codigitech.belay.data.local.entity.CheckInEntity
import com.codigitech.belay.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckInDaoTest {
  @get:Rule val dbRule = InMemoryBelayDatabaseRule()

  private fun dao() = dbRule.db.checkInDao()

  @Before
  fun seedChallengeAndHabit() = runTest {
    dbRule.db.challengeDao().upsert(
      ChallengeEntity(
        challengeId = "c1",
        challengerUserId = "user-1",
        witnessUserId = "user-2",
        title = "Title",
        durationDays = 21,
        graceDaysTotal = 3,
        graceDaysUsed = 0,
        perfectDays = 0,
        startDate = 0L,
        status = "active",
      )
    )
    dbRule.db.habitDao().upsertAll(
      listOf(
        HabitEntity(
          habitId = "h1",
          challengeId = "c1",
          name = "Read",
          detail = null,
          icon = null,
          reminderTime = null,
          sortOrder = 0,
          currentStreak = 0,
        )
      )
    )
  }

  private fun checkIn(
    id: String,
    habitId: String = "h1",
    date: Long = 1L,
    done: Boolean = true,
    synced: Boolean = true,
  ) = CheckInEntity(
    checkInId = id,
    habitId = habitId,
    challengeId = "c1",
    date = date,
    done = done,
    checkedAt = 1000L,
    clientIdempotencyKey = id,
    synced = synced,
  )

  @Test
  fun upsert_onSameHabitAndDate_replacesThePriorRow_becauseOfTheUniqueIndex() = runTest {
    dao().upsert(checkIn(id = "first-attempt", done = false))
    dao().upsert(checkIn(id = "retry-same-day", done = true))

    val rows = dao().observeForHabit("h1").first()

    assertEquals(1, rows.size)
    assertEquals("retry-same-day", rows.single().checkInId)
    assertTrue(rows.single().done)
  }

  @Test
  fun get_findsTheRowForThatHabitAndDate_andNotOtherDates() = runTest {
    dao().upsert(checkIn(id = "day-1", date = 1L))
    dao().upsert(checkIn(id = "day-2", date = 2L))

    assertEquals("day-1", dao().get("h1", 1L)?.checkInId)
    assertEquals("day-2", dao().get("h1", 2L)?.checkInId)
  }

  @Test
  fun getUnsynced_returnsOnlyRowsNotYetPushedToFirestore() = runTest {
    dao().upsert(checkIn(id = "synced", date = 1L, synced = true))
    dao().upsert(checkIn(id = "pending", date = 2L, synced = false))

    val unsynced = dao().getUnsynced()

    assertEquals(listOf("pending"), unsynced.map { it.checkInId })
  }

  @Test
  fun deletingHabit_cascadesToItsCheckIns() = runTest {
    dao().upsert(checkIn(id = "ci1", date = 1L))

    dbRule.db.openHelper.writableDatabase.execSQL("DELETE FROM habits WHERE habitId = 'h1'")

    assertTrue(dao().observeForHabit("h1").first().isEmpty())
  }
}
