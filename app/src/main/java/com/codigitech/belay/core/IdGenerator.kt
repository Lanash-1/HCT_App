package com.codigitech.belay.core

import java.util.UUID
import javax.inject.Inject

/** Testability seam around id generation — repository logic should never call UUID.randomUUID() directly. */
fun interface IdGenerator {
  fun newId(): String
}

class UuidIdGenerator @Inject constructor() : IdGenerator {
  override fun newId(): String = UUID.randomUUID().toString()
}
