package com.codigitech.belay.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * Builds a fresh in-memory [BelayDatabase] per test — real SQLite via Room, not a fake DAO. Covers
 * SQL the unit-test fakes in `data/repository` can't: query correctness, unique-index conflict
 * handling, and foreign-key cascade deletes.
 */
class InMemoryBelayDatabaseRule : ExternalResource() {
  lateinit var db: BelayDatabase
    private set

  override fun before() {
    db =
      Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BelayDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  override fun after() {
    db.close()
  }
}
