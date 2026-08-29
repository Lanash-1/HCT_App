package com.codigitech.belay.data.repository

import com.codigitech.belay.data.local.dao.RecapDao
import com.codigitech.belay.data.local.entity.RecapEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Recaps are generated server-side by a weekly Catalyst Cron job (docs/DATA_MODEL.md `recaps`) — read-only locally. */
interface RecapRepository {
  fun observeForChallenge(challengeId: String): Flow<List<RecapEntity>>
}

class RecapRepositoryImpl
@Inject
constructor(private val recapDao: RecapDao) : RecapRepository {
  override fun observeForChallenge(challengeId: String): Flow<List<RecapEntity>> = recapDao.observeForChallenge(challengeId)
}
