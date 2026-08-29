package com.codigitech.belay.ui.recap

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecapRoute(modifier: Modifier = Modifier, viewModel: RecapViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  RecapScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun RecapScreen(uiState: RecapUiState, modifier: Modifier = Modifier) {
  if (uiState.isLoading) return
  val context = LocalContext.current

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(RecapCopy.TITLE, style = MaterialTheme.typography.headlineSmall)

    if (!uiState.hasRecap) {
      EmptyState()
    } else {
      RecapCard(uiState)

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, uiState.shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, RecapCopy.SHARE_LABEL))
          },
          modifier = Modifier.weight(1f),
        ) {
          Text(RecapCopy.SHARE_LABEL)
        }
        OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text(RecapCopy.SAVE_LABEL) }
      }

      Text(
        RecapCopy.autoSendNote(uiState.witnessName),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(RecapCopy.EMPTY_TITLE, style = MaterialTheme.typography.titleMedium)
      Text(RecapCopy.EMPTY_DETAIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun RecapCard(uiState: RecapUiState, modifier: Modifier = Modifier) {
  Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(
        "${uiState.weekRangeLabel} · ${uiState.challengeTitle}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        RecapCopy.checkInsLine(uiState.checkInsTotal, uiState.checkInsPossible, uiState.perfectDays),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        uiState.habitRows.forEach { row -> HabitRecapRow(row) }
      }

      Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary) {
        Text(
          RecapCopy.witnessedByLine(uiState.witnessName),
          modifier = Modifier.padding(14.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
  }
}

@Composable
private fun HabitRecapRow(row: RecapHabitRowUiState, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text(row.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
      Text(row.score, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
      row.cells.forEach { done ->
        Box(
          modifier =
            Modifier.weight(1f)
              .padding(vertical = 2.dp)
              .background(
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
              )
        ) {
          Box(modifier = Modifier.padding(vertical = 13.dp))
        }
      }
    }
  }
}
