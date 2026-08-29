@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.codigitech.belay.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codigitech.belay.ui.common.BelayTimePickerDialog

@Composable
fun ProfileRoute(onOpenPerson: (String) -> Unit, modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  ProfileScreen(
    uiState = uiState,
    onOpenPerson = onOpenPerson,
    onSetMode = viewModel::setMode,
    onSetThemePref = viewModel::setThemePref,
    onSetNudgeAllowed = viewModel::setNudgeAllowed,
    onSetDailyReminderTime = viewModel::setDailyReminderTime,
    modifier = modifier,
  )
}

@Composable
fun ProfileScreen(
  uiState: ProfileUiState,
  onOpenPerson: (String) -> Unit,
  onSetMode: (String) -> Unit,
  onSetThemePref: (String) -> Unit,
  onSetNudgeAllowed: (Boolean) -> Unit,
  onSetDailyReminderTime: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (uiState.isLoading) return
  var showReminderDialog by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Avatar(uiState.displayName, size = 68.dp)
      Column {
        Text(uiState.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
          ProfileCopy.pairCodeLine(uiState.pairCode, uiState.joinedLabel),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    SectionLabel(ProfileCopy.MODE_LABEL)
    ModeSegment(mode = uiState.mode, onSetMode = onSetMode)
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
      Text(
        if (uiState.mode == "witness") ProfileCopy.WITNESS_MODE_EXPLAINER else ProfileCopy.CHALLENGER_MODE_EXPLAINER,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall,
      )
    }

    SectionLabel(ProfileCopy.APPEARANCE_LABEL)
    ThemeSegment(themePref = uiState.themePref, onSetThemePref = onSetThemePref)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatCard(uiState.habitCount.toString(), ProfileCopy.HABITS_STAT_LABEL, Modifier.weight(1f))
      StatCard(uiState.bestStreak.toString(), ProfileCopy.BEST_STREAK_STAT_LABEL, Modifier.weight(1f))
      StatCard(uiState.peopleWatchedCount.toString(), ProfileCopy.PEOPLE_WATCHED_STAT_LABEL, Modifier.weight(1f))
    }

    SectionLabel(ProfileCopy.PEOPLE_LABEL)
    Column {
      uiState.witnessRow?.let { PersonRow(it, onClick = {}) }
      uiState.watchingRows.forEach { row -> PersonRow(row, onClick = { row.challengeId?.let(onOpenPerson) }) }
    }

    SectionLabel(ProfileCopy.SETTINGS_LABEL)
    Column {
      SettingsRow(
        ProfileCopy.DAILY_REMINDER_LABEL,
        uiState.dailyReminderTime ?: ProfileCopy.NO_REMINDER_SET,
        onClick = { showReminderDialog = true },
      )
      SettingsRowSwitch(uiState.nudgeToggleLabel, uiState.nudgeAllowed, onSetNudgeAllowed)
      SettingsRow(ProfileCopy.GRACE_DAYS_LEFT_LABEL, uiState.graceDaysLeft?.toString() ?: ProfileCopy.NO_ACTIVE_CHALLENGE, onClick = null)
    }

    Text(
      ProfileCopy.SWITCH_NOTE,
      modifier = Modifier.fillMaxWidth(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }

  if (showReminderDialog) {
    BelayTimePickerDialog(
      initial = uiState.dailyReminderTime,
      onDismiss = { showReminderDialog = false },
      onConfirm = { time ->
        onSetDailyReminderTime(time)
        showReminderDialog = false
      },
    )
  }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.size(size).background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
  }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    modifier = modifier,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun ModeSegment(mode: String, onSetMode: (String) -> Unit, modifier: Modifier = Modifier) {
  SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
    SegmentedButton(
      selected = mode == "challenger",
      onClick = { onSetMode("challenger") },
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
    ) {
      Text(ProfileCopy.CHALLENGER_LABEL)
    }
    SegmentedButton(
      selected = mode == "witness",
      onClick = { onSetMode("witness") },
      shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
    ) {
      Text(ProfileCopy.WITNESS_LABEL)
    }
  }
}

@Composable
private fun ThemeSegment(themePref: String, onSetThemePref: (String) -> Unit, modifier: Modifier = Modifier) {
  val options = listOf("system" to ProfileCopy.SYSTEM_LABEL, "light" to ProfileCopy.LIGHT_LABEL, "dark" to ProfileCopy.DARK_LABEL)
  SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
    options.forEachIndexed { index, (value, label) ->
      SegmentedButton(
        selected = themePref == value,
        onClick = { onSetThemePref(value) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
      ) {
        Text(label)
      }
    }
  }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun PersonRow(row: ProfilePersonRowUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Avatar(row.title, size = 40.dp)
      Column(modifier = Modifier.weight(1f)) {
        Text(row.title, style = MaterialTheme.typography.titleSmall)
        Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (row.challengeId != null) {
        TextButton(onClick = onClick) { Text("›") }
      }
    }
    HorizontalDivider()
  }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
  }
}

@Composable
private fun SettingsRowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider()
  }
}

