package com.codigitech.belay.ui.createchallenge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codigitech.belay.ui.common.BelayTimePickerDialog

@Composable
fun CreateChallengeRoute(
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CreateChallengeViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.didCreate) {
    if (uiState.didCreate) onDone()
  }

  NotificationPermissionPrompt(triggerKey = uiState.selectedWitnessId)

  CreateChallengeScreen(
    uiState = uiState,
    onTitleChange = viewModel::onTitleChange,
    onHabitNameChange = viewModel::onHabitNameChange,
    onHabitDetailChange = viewModel::onHabitDetailChange,
    onHabitReminderTimeChange = viewModel::onHabitReminderTimeChange,
    onAddHabit = viewModel::addHabit,
    onRemoveHabit = viewModel::removeHabit,
    onSelectDuration = viewModel::selectDuration,
    onSelectWitness = viewModel::selectWitness,
    onIncrementGrace = viewModel::incrementGraceDays,
    onDecrementGrace = viewModel::decrementGraceDays,
    onSubmit = viewModel::submit,
    modifier = modifier,
  )
}

@Composable
fun CreateChallengeScreen(
  uiState: CreateChallengeUiState,
  onTitleChange: (String) -> Unit,
  onHabitNameChange: (Int, String) -> Unit,
  onHabitDetailChange: (Int, String) -> Unit,
  onHabitReminderTimeChange: (Int, String?) -> Unit,
  onAddHabit: () -> Unit,
  onRemoveHabit: (Int) -> Unit,
  onSelectDuration: (Int) -> Unit,
  onSelectWitness: (String) -> Unit,
  onIncrementGrace: () -> Unit,
  onDecrementGrace: () -> Unit,
  onSubmit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) {
      OutlinedTextField(
        value = uiState.title,
        onValueChange = onTitleChange,
        label = { Text(CreateChallengeCopy.TITLE_LABEL) },
        placeholder = { Text(CreateChallengeCopy.TITLE_HINT) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(CreateChallengeCopy.HABITS_SECTION, CreateChallengeCopy.habitCount(uiState.habits.size))
        uiState.habits.forEachIndexed { index, habit ->
          HabitRow(
            habit = habit,
            showRemove = uiState.habits.size > 1,
            onNameChange = { onHabitNameChange(index, it) },
            onDetailChange = { onHabitDetailChange(index, it) },
            onReminderTimeChange = { onHabitReminderTimeChange(index, it) },
            onRemove = { onRemoveHabit(index) },
          )
        }
        if (uiState.habits.size < CreateChallengeViewModel.MAX_HABITS) {
          TextButton(onClick = onAddHabit) { Text(CreateChallengeCopy.ADD_HABIT) }
        }
        Text(CreateChallengeCopy.HABITS_HELPER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(CreateChallengeCopy.DURATION_SECTION)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CreateChallengeViewModel.DURATIONS_DAYS.forEach { days ->
            FilterChip(
              selected = uiState.selectedDurationDays == days,
              onClick = { onSelectDuration(days) },
              label = { Text(CreateChallengeCopy.durationLabel(days)) },
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(CreateChallengeCopy.WITNESS_SECTION)
        if (uiState.witnessOptions.isEmpty()) {
          Text(CreateChallengeCopy.WITNESS_EMPTY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        uiState.witnessOptions.forEach { option ->
          WitnessRow(option = option, selected = uiState.selectedWitnessId == option.userId, onClick = { onSelectWitness(option.userId) })
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(CreateChallengeCopy.GRACE_SECTION)
        Row(
          modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(CreateChallengeCopy.GRACE_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(CreateChallengeCopy.GRACE_DETAIL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onDecrementGrace, modifier = Modifier) { Text("−") }
            Text(uiState.graceDays.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onIncrementGrace) { Text("+") }
          }
        }
      }

      uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    Button(onClick = onSubmit, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
      Text(CreateChallengeCopy.SAVE)
    }
  }
}

/**
 * Prompts for POST_NOTIFICATIONS (Android 13+) right after [triggerKey] first becomes non-null —
 * i.e. once the user has picked a witness and can see why reminders/cheers need it (PRD §6.5),
 * rather than firing blind on first launch. A no-op below API 33 or once already granted.
 */
@Composable
private fun NotificationPermissionPrompt(triggerKey: String?) {
  var showRationale by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

  LaunchedEffect(triggerKey) {
    if (
      triggerKey != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      showRationale = true
    }
  }

  if (showRationale) {
    AlertDialog(
      onDismissRequest = { showRationale = false },
      title = { Text(CreateChallengeCopy.NOTIFICATION_RATIONALE_TITLE) },
      text = { Text(CreateChallengeCopy.NOTIFICATION_RATIONALE_BODY) },
      confirmButton = {
        TextButton(
          onClick = {
            showRationale = false
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        ) {
          Text(CreateChallengeCopy.NOTIFICATION_RATIONALE_CONFIRM)
        }
      },
      dismissButton = { TextButton(onClick = { showRationale = false }) { Text(CreateChallengeCopy.NOTIFICATION_RATIONALE_DISMISS) } },
    )
  }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
private fun HabitRow(
  habit: HabitInput,
  showRemove: Boolean,
  onNameChange: (String) -> Unit,
  onDetailChange: (String) -> Unit,
  onReminderTimeChange: (String?) -> Unit,
  onRemove: () -> Unit,
) {
  var showReminderDialog by remember { mutableStateOf(false) }

  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = habit.name,
          onValueChange = onNameChange,
          placeholder = { Text(CreateChallengeCopy.HABIT_NAME_HINT) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = habit.detail,
          onValueChange = onDetailChange,
          placeholder = { Text(CreateChallengeCopy.HABIT_DETAIL_HINT) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = { showReminderDialog = true }) { Text(CreateChallengeCopy.reminderLabel(habit.reminderTime)) }
          if (habit.reminderTime != null) {
            TextButton(onClick = { onReminderTimeChange(null) }) { Text(CreateChallengeCopy.CLEAR_REMINDER) }
          }
        }
      }
      if (showRemove) {
        IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = CreateChallengeCopy.REMOVE_HABIT_DESCRIPTION }) {
          Text("×", style = MaterialTheme.typography.titleLarge)
        }
      }
    }
  }

  if (showReminderDialog) {
    BelayTimePickerDialog(
      initial = habit.reminderTime,
      onDismiss = { showReminderDialog = false },
      onConfirm = { time ->
        onReminderTimeChange(time)
        showReminderDialog = false
      },
    )
  }
}

@Composable
private fun WitnessRow(option: WitnessOption, selected: Boolean, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    colors =
      CardDefaults.cardColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
      ),
  ) {
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(option.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
  }
}
