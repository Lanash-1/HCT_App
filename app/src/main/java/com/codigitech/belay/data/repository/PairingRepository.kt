package com.codigitech.belay.data.repository

import com.codigitech.belay.core.BelayClock
import com.codigitech.belay.core.IdGenerator
import com.codigitech.belay.data.local.dao.PairingDao
import com.codigitech.belay.data.local.entity.PairingEntity
import com.codigitech.belay.data.remote.PairingRemoteDataSource
import com.codigitech.belay.domain.pairing.PairCodeGenerator
import javax.inject.Inject

sealed interface PairingResult {
  data class Success(val pairing: PairingEntity) : PairingResult

  data object NotFound : PairingResult

  /** The remote lookup itself failed (offline, flaky connection) — distinct from a genuinely invalid code. */
  data object NetworkError : PairingResult
}

interface PairingRepository {
  suspend fun createPendingPairing(fromUserId: String): PairingEntity

  suspend fun completePairing(pairCode: String, toUserId: String): PairingResult
}

class PairingRepositoryImpl
@Inject
constructor(
  private val pairingDao: PairingDao,
  private val remoteDataSource: PairingRemoteDataSource,
  private val codeGenerator: PairCodeGenerator,
  private val clock: BelayClock,
  private val idGenerator: IdGenerator,
) : PairingRepository {

  override suspend fun createPendingPairing(fromUserId: String): PairingEntity {
    val pairing =
      PairingEntity(
        pairingId = idGenerator.newId(),
        pairCode = codeGenerator.generate(),
        fromUserId = fromUserId,
        toUserId = null,
        status = "pending",
        createdAt = clock.nowEpochMillis(),
      )
    // Best-effort remote write: a Firestore hiccup shouldn't crash role pick, and the code is
    // still usable once connectivity returns (no dedicated retry queue for this yet, unlike
    // check-ins' WorkManager queue — see TECH_STACK.md §5).
    runCatching { remoteDataSource.upsert(pairing) }
    pairingDao.upsert(pairing)
    return pairing
  }

  override suspend fun completePairing(pairCode: String, toUserId: String): PairingResult {
    // The pairing was created on the other person's device, so it must be resolved remotely —
    // local Room only ever has pairings this device itself created or already completed.
    val pending =
      try {
        remoteDataSource.findPendingByCode(pairCode) ?: return PairingResult.NotFound
      } catch (e: Exception) {
        return PairingResult.NetworkError
      }
    val paired = pending.copy(toUserId = toUserId, status = "paired")
    runCatching { remoteDataSource.upsert(paired) }
    pairingDao.upsert(paired)
    return PairingResult.Success(paired)
  }
}
