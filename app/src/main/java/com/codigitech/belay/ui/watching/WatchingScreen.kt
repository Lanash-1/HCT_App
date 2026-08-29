package com.codigitech.belay.ui.watching

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codigitech.belay.data.repository.CheerOrNudgeResult
import com.codigitech.belay.ui.common.CheerNudgeDialog

@Composable
fun WatchingRoute(onOpenPerson: (String) -> Unit, modifier: Modifier = Modifier, viewModel: WatchingViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  WatchingScreen(
    uiState = uiState,
    onOpenPerson = onOpenPerson,
    onSendCheer = viewModel::sendCheer,
    onSendNudge = viewModel::sendNudge,
    modifier = modifier,
  )
}

@Composable
fun WatchingScreen(
  uiState: WatchingUiState,
  onOpenPerson: (String) -> Unit,
  onSendCheer: suspend (String, String) -> CheerOrNudgeResult,
  onSendNudge: suspend (String, String) -> CheerOrNudgeResult,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(WatchingCopy.watchingCount(uiState.people.size), style = MaterialTheme.typography.headlineSmall)

    if (uiState.people.isEmpty() && !uiState.isLoading) {
      EmptyState()
    } else {
      uiState.people.forEach { person ->
        PersonCard(
          person = person,
          onOpen = { onOpenPerson(person.challengeId) },
          onSendCheer = { message -> onSendCheer(person.challengeId, message) },
          onSendNudge = { message -> onSendNudge(person.challengeId, message) },
        )
      }
    }

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
      Text(
        WatchingCopy.FOOTNOTE,
        modifier = Modifier.padding(16.dp),
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
      Text(WatchingCopy.EMPTY_TITLE, style = MaterialTheme.typography.titleMedium)
      Text(WatchingCopy.EMPTY_DETAIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun PersonCard(
  person: WatchingPersonUiState,
  onOpen: () -> Unit,
  onSendCheer: suspend (String) -> CheerOrNudgeResult,
  onSendNudge: suspend (String) -> CheerOrNudgeResult,
  modifier: Modifier = Modifier,
) {
  var dialog by remember { mutableStateOf<Boolean?>(null) } // null = closed, true = nudge, false = cheer

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    onClick = onOpen,
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(person.challengerName, style = MaterialTheme.typography.titleMedium)
          Text(person.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
          shape = RoundedCornerShape(100),
          color = if (person.onTrack) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        ) {
          Text(
            WatchingCopy.countPill(person.doneCount, person.habitCount),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        person.habits.forEach { habit ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(habit.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { dialog = false }, modifier = Modifier.weight(1f)) { Text(WatchingCopy.CHEER_LABEL) }
        OutlinedButton(onClick = { dialog = true }, modifier = Modifier.weight(1f)) { Text(WatchingCopy.NUDGE_LABEL) }
      }
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
