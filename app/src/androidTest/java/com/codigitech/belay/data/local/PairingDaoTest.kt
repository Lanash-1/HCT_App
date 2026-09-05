package com.codigitech.belay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codigitech.belay.data.local.entity.PairingEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingDaoTest {
  @get:Rule val dbRule = InMemoryBelayDatabaseRule()

  private fun dao() = dbRule.db.pairingDao()

  private fun pairing(id: String, pairCode: String, fromUserId: String, toUserId: String?, status: String) =
    PairingEntity(
      pairingId = id,
      pairCode = pairCode,
      fromUserId = fromUserId,
      toUserId = toUserId,
      status = status,
      createdAt = 0L,
    )

  @Test
  fun findPendingByCode_ignoresExpiredAndAlreadyPairedCodes() = runTest {
    dao().upsert(pairing("p1", "AAAA", "user-1", null, status = "expired"))
    dao().upsert(pairing("p2", "AAAA", "user-1", "user-2", status = "paired"))
    dao().upsert(pairing("p3", "BBBB", "user-3", null, status = "pending"))

    assertNull(dao().findPendingByCode("AAAA"))
    assertEquals("p3", dao().findPendingByCode("BBBB")?.pairingId)
  }

  @Test
  fun getPairedFor_matchesEitherSideOfThePairing_butOnlyOncePaired() = runTest {
    dao().upsert(pairing("p1", "AAAA", "user-1", "user-2", status = "paired"))
    dao().upsert(pairing("p2", "BBBB", "user-3", "user-1", status = "paired"))
    dao().upsert(pairing("p3", "CCCC", "user-1", "user-9", status = "pending"))

    val result = dao().getPairedFor("user-1")

    assertEquals(setOf("p1", "p2"), result.map { it.pairingId }.toSet())
  }
}
