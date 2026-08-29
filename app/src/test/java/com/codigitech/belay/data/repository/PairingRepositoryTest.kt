package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.PairingDao
import com.codigitech.belay.data.local.entity.PairingEntity
import com.codigitech.belay.data.remote.PairingRemoteDataSource
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

private class FakePairingRemoteDataSource : PairingRemoteDataSource {
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

  private fun repository(dao: PairingDao, remote: PairingRemoteDataSource) =
    PairingRepositoryImpl(
      pairingDao = dao,
      remoteDataSource = remote,
      codeGenerator = PairCodeGenerator(Random(1)),
      clock = fixedClock,
      idGenerator = fixedIds,
    )

  @Test
  fun `creating a pending pairing stores it remotely and locally with pending status and no witness yet`() = runTest {
    val dao = FakePairingDao()
    val remote = FakePairingRemoteDataSource()

    val pairing = repository(dao, remote).createPendingPairing(fromUserId = "user-1")

    assertEquals("pairing-1", pairing.pairingId)
    assertEquals("user-1", pairing.fromUserId)
    assertEquals("pending", pairing.status)
    assertNull(pairing.toUserId)
    assertEquals(1_000L, pairing.createdAt)
    assertEquals(dao.stored[pairing.pairingId], pairing)
    assertEquals(remote.stored[pairing.pairingId], pairing)
  }

  @Test
  fun `completing a pairing resolves the code remotely (created on a different device) and mirrors it locally`() = runTest {
    val dao = FakePairingDao()
    val remote = FakePairingRemoteDataSource()
    // Simulate the pairing having been created on the challenger's device: it only ever reached
    // this device's remote data source, never its local Room.
    val pending = PairingRepositoryImpl(dao, remote, PairCodeGenerator(Random(1)), fixedClock, fixedIds).createPendingPairing("user-1")
    dao.stored.clear()

    val result = repository(dao, remote).completePairing(pairCode = pending.pairCode, toUserId = "user-2")

    assertTrue(result is PairingResult.Success)
    val paired = (result as PairingResult.Success).pairing
    assertEquals("paired", paired.status)
    assertEquals("user-2", paired.toUserId)
    assertEquals("paired", remote.stored[pending.pairingId]?.status)
    assertEquals("paired", dao.stored[pending.pairingId]?.status)
  }

  @Test
  fun `completing a pairing with an unknown code returns not found`() = runTest {
    val result = repository(FakePairingDao(), FakePairingRemoteDataSource()).completePairing(pairCode = "ZZZZ", toUserId = "user-2")

    assertEquals(PairingResult.NotFound, result)
  }

  @Test
  fun `completing an already-paired code returns not found (it's no longer pending)`() = runTest {
    val dao = FakePairingDao()
    val remote = FakePairingRemoteDataSource()
    val pending = repository(dao, remote).createPendingPairing(fromUserId = "user-1")
    repository(dao, remote).completePairing(pairCode = pending.pairCode, toUserId = "user-2")

    val secondAttempt = repository(dao, remote).completePairing(pairCode = pending.pairCode, toUserId = "user-3")

    assertEquals(PairingResult.NotFound, secondAttempt)
  }
}
