package com.codigitech.belay.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder for the Today screen (PRD §5.3) until the challenge/habit check-in flow is
 * built test-first per the TDD workflow in TECH_STACK.md §7.
 */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = "Belay", style = MaterialTheme.typography.headlineMedium)
  }
}
