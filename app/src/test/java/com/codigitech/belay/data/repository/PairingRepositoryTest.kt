package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.PairingDao
import com.codigitech.belay.data.local.entity.PairingEntity
import com.codigitech.belay.domain.pairing.PairCodeGenerator
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePairingDao : PairingDao {
  val stored = mutableMapOf<String, PairingEntity>()

  override suspend fun upsert(pairing: PairingEntity) {
    stored[pairing.pairingId] = pairing
  }

  override suspend fun findPendingByCode(pairCode: String): PairingEntity? =
    stored.values.firstOrNull { it.pairCode == pairCode && it.status == "pending" }
}

class PairingRepositoryTest {

  private val fixedClock = BelayClock { 1_000L }
  private val fixedIds = IdGenerator { "pairing-1" }

  private fun repository(dao: PairingDao) =
    PairingRepositoryImpl(pairingDao = dao, codeGenerator = PairCodeGenerator(Random(1)), clock = fixedClock, idGenerator = fixedIds)

  @Test
  fun `creating a pending pairing stores it with pending status and no witness yet`() = runTest {
    val dao = FakePairingDao()

    val pairing = repository(dao).createPendingPairing(fromUserId = "user-1")

    assertEquals("pairing-1", pairing.pairingId)
    assertEquals("user-1", pairing.fromUserId)
    assertEquals("pending", pairing.status)
    assertNull(pairing.toUserId)
    assertEquals(1_000L, pairing.createdAt)
    assertEquals(dao.stored[pairing.pairingId], pairing)
  }

  @Test
  fun `completing a pairing with a known pending code marks it paired`() = runTest {
    val dao = FakePairingDao()
    val pending = repository(dao).createPendingPairing(fromUserId = "user-1")

    val result = repository(dao).completePairing(pairCode = pending.pairCode, toUserId = "user-2")

    assertTrue(result is PairingResult.Success)
    val paired = (result as PairingResult.Success).pairing
    assertEquals("paired", paired.status)
    assertEquals("user-2", paired.toUserId)
    assertEquals("paired", dao.stored[pending.pairingId]?.status)
  }

  @Test
  fun `completing a pairing with an unknown code returns not found`() = runTest {
    val result = repository(FakePairingDao()).completePairing(pairCode = "ZZZZ", toUserId = "user-2")

    assertEquals(PairingResult.NotFound, result)
  }

  @Test
  fun `completing an already-paired code returns not found (it's no longer pending)`() = runTest {
    val dao = FakePairingDao()
    val pending = repository(dao).createPendingPairing(fromUserId = "user-1")
    repository(dao).completePairing(pairCode = pending.pairCode, toUserId = "user-2")

    val secondAttempt = repository(dao).completePairing(pairCode = pending.pairCode, toUserId = "user-3")

    assertEquals(PairingResult.NotFound, secondAttempt)
  }
}
