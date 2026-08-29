package com.codigitech.belay

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Auth : NavKey

@Serializable data object Onboarding : NavKey

@Serializable data object CreateChallenge : NavKey

@Serializable data object Today : NavKey
