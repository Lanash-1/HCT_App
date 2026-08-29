package com.codigitech.belay.ui.witnessdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codigitech.belay.data.repository.CheerOrNudgeResult
import com.codigitech.belay.ui.common.CheerNudgeDialog

@Composable
fun WitnessDetailRoute(challengeId: String, modifier: Modifier = Modifier, viewModel: WitnessDetailViewModel = hiltViewModel()) {
  LaunchedEffect(challengeId) { viewModel.load(challengeId) }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  WitnessDetailScreen(uiState = uiState, onSendCheer = viewModel::sendCheer, onSendNudge = viewModel::sendNudge, modifier = modifier)
}

@Composable
fun WitnessDetailScreen(
  uiState: WitnessDetailUiState,
  onSendCheer: suspend (String) -> CheerOrNudgeResult,
  onSendNudge: suspend (String) -> CheerOrNudgeResult,
  modifier: Modifier = Modifier,
) {
  if (uiState.isLoading) return

  var dialog by remember { mutableStateOf<Boolean?>(null) } // null = closed, true = nudge, false = cheer

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Column {
      Text(WitnessDetailCopy.WATCHING_LABEL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          WitnessDetailCopy.subtitle(uiState.challengerName, uiState.challengeTitle),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          WitnessDetailCopy.dayCount(uiState.dayNo, uiState.totalDays),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    StatusCard(uiState)

    ProgressCard(uiState)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(onClick = { dialog = false }, modifier = Modifier.weight(1f)) { Text(WitnessDetailCopy.CHEER_LABEL) }
      OutlinedButton(onClick = { dialog = true }, modifier = Modifier.weight(1f)) { Text(WitnessDetailCopy.NUDGE_LABEL) }
    }
    Text(
      WitnessDetailCopy.NUDGE_HINT,
      modifier = Modifier.fillMaxWidth(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    LogSection(uiState)

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
      Text(
        WitnessDetailCopy.FOOTNOTE,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  dialog?.let { isNudge ->
    CheerNudgeDialog(
      isNudge = isNudge,
      onDismiss = { dialog = null },
      onSend = { message -> if (isNudge) onSendNudge(message) else onSendCheer(message) },
    )
  }
}

@Composable
private fun StatusCard(uiState: WitnessDetailUiState, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = if (uiState.allDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(
        uiState.headline,
        style = MaterialTheme.typography.headlineSmall,
        color = if (uiState.allDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
      )
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiState.habits.forEach { habit ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              habit.name,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodyMedium,
              color = if (uiState.allDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
              habit.time,
              style = MaterialTheme.typography.bodySmall,
              color = if (uiState.allDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProgressCard(uiState: WitnessDetailUiState, modifier: Modifier = Modifier) {
  Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row {
        Text(WitnessDetailCopy.PROGRESS_LABEL, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text("${uiState.progressPercent}%", style = MaterialTheme.typography.titleSmall)
      }
      LinearProgressIndicator(
        progress = { uiState.progressPercent / 100f },
        modifier = Modifier.fillMaxWidth().height(10.dp),
      )
      Text(
        WitnessDetailCopy.progressDetail(uiState.perfectDays, uiState.checkInsTotal, uiState.graceDaysLeft),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LogSection(uiState: WitnessDetailUiState, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(WitnessDetailCopy.LOG_LABEL, style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      uiState.log.forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(row.dayLabel, style = MaterialTheme.typography.bodyMedium)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text(row.score, style = MaterialTheme.typography.titleSmall)
        }
      }
    }
  }
}
