package com.codigitech.belay.core

import javax.inject.Inject

/** Testability seam around wall-clock time — repository logic should never call System.currentTimeMillis() directly. */
fun interface BelayClock {
  fun nowEpochMillis(): Long
}

class SystemBelayClock @Inject constructor() : BelayClock {
  override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
