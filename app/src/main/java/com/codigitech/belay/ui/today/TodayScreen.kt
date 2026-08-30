package com.codigitech.belay.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TodayRoute(modifier: Modifier = Modifier, viewModel: TodayViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  TodayScreen(
    uiState = uiState,
    onToggleHabit = viewModel::toggleHabit,
    onDismissNudge = viewModel::dismissNudge,
    onDismissRecovery = viewModel::dismissRecovery,
    modifier = modifier,
  )
}

@Composable
fun TodayScreen(
  uiState: TodayUiState,
  onToggleHabit: (String) -> Unit,
  onDismissNudge: () -> Unit,
  onDismissRecovery: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!uiState.hasActiveChallenge) {
    EmptyState(modifier)
    return
  }

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Text(uiState.challengeTitle, style = MaterialTheme.typography.headlineSmall)

    // Dulled when nobody's actually watching (no witness, invite unopened, gone quiet) — a live
    // green pill for an absent witness overstates the one thing this app is for (PRD §6.7).
    WitnessStatusPill(uiState.witnessStatusText, isLive = uiState.hasWitness && !uiState.isWitnessAway)

    if (uiState.hasEnded) {
      NoticeCard(title = TodayCopy.ENDED_TITLE, detail = TodayCopy.ENDED_DETAIL)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      ProgressRing(
        fraction = uiState.progressFraction,
        label = TodayCopy.progressLabel(checked = uiState.habits.count { it.checkedToday }, total = uiState.habits.size),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      uiState.habits.forEach { habit -> HabitRow(habit = habit, onToggle = { onToggleHabit(habit.habitId) }) }
    }

    uiState.cheerMessage?.let { CheerCard(it) }

    SummaryStrip(uiState)

    // Grace is decided at creation and only spent (PRD §4.6), so running out is worth saying
    // plainly rather than leaving as a "0" in the summary strip.
    if (uiState.isGraceExhausted && !uiState.hasEnded) {
      NoticeCard(title = TodayCopy.GRACE_EXHAUSTED_TITLE, detail = TodayCopy.GRACE_EXHAUSTED_DETAIL)
    }

    uiState.nudgeMessage?.let { NudgeToast(message = it, onDismiss = onDismissNudge) }
  }

  if (uiState.brokenHabitNames.isNotEmpty()) {
    StreakRecoveryDialog(habitNames = uiState.brokenHabitNames, onDismiss = onDismissRecovery)
  }
}

/** A designed moment for hitting zero grace with a miss (PRD §6.2) — not a silent streak reset. */
@Composable
private fun StreakRecoveryDialog(habitNames: List<String>, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
      Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(TodayCopy.RECOVERY_TITLE, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
          TodayCopy.recoveryHabitList(habitNames),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
        )
        Text(
          TodayCopy.RECOVERY_DETAIL,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        TextButton(onClick = onDismiss) { Text(TodayCopy.RECOVERY_CONTINUE) }
      }
    }
  }
}

@Composable
private fun WitnessStatusPill(text: String, isLive: Boolean, modifier: Modifier = Modifier) {
  if (text.isBlank()) return
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(100),
    color = if (isLive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(7.dp)
            .background(
              color = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
              shape = CircleShape,
            )
      )
      Text(
        text,
        modifier = Modifier.padding(start = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (isLive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The shared shape for the §6.7 edge states — a stated situation, not an empty space. */
@Composable
private fun NoticeCard(title: String, detail: String, modifier: Modifier = Modifier) {
  Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun CheerCard(message: String, modifier: Modifier = Modifier) {
  Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun NudgeToast(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        message,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.inverseOnSurface,
      )
      TextButton(onClick = onDismiss) { Text(TodayCopy.NUDGE_DISMISS_LABEL) }
    }
  }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(TodayCopy.EMPTY_TITLE, style = MaterialTheme.typography.headlineMedium)
      Text(TodayCopy.EMPTY_DETAIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun ProgressRing(fraction: Float, label: String, modifier: Modifier = Modifier) {
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val progressColor = MaterialTheme.colorScheme.primary
  Box(modifier = modifier.size(140.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
      val inset = stroke.width / 2
      val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
      drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = arcSize,
        style = stroke,
      )
      drawArc(
        color = progressColor,
        startAngle = -90f,
        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = arcSize,
        style = stroke,
      )
    }
    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun HabitRow(habit: TodayHabitUiState, onToggle: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = habit.checkedToday, onCheckedChange = { onToggle() })
      Column(modifier = Modifier.weight(1f)) {
        Text(habit.name, style = MaterialTheme.typography.titleSmall)
        habit.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
      Text(
        "🔥 ${habit.streak}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SummaryStrip(uiState: TodayUiState) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    SummaryItem(TodayCopy.PERFECT_DAYS_LABEL, uiState.perfectDays.toString())
    SummaryItem(TodayCopy.GRACE_LEFT_LABEL, uiState.graceDaysLeft.toString())
    SummaryItem(TodayCopy.DAYS_TO_GO_LABEL, uiState.daysToGo.toString())
  }
}

@Composable
private fun SummaryItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
