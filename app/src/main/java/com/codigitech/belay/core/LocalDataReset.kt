package com.codigitech.belay.core

import com.codigitech.belay.data.local.BelayDatabase
import javax.inject.Inject

/** Testability seam around wiping local (Room) data — the auth-lifecycle boundary (sign-out, account deletion). */
fun interface LocalDataReset {
  fun clearAll()
}

class RoomLocalDataReset @Inject constructor(private val database: BelayDatabase) : LocalDataReset {
  override fun clearAll() = database.clearAllTables()
}
