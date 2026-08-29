package com.codigitech.belay.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TodayRoute(modifier: Modifier = Modifier, viewModel: TodayViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  TodayScreen(uiState = uiState, onToggleHabit = viewModel::toggleHabit, modifier = modifier)
}

@Composable
fun TodayScreen(uiState: TodayUiState, onToggleHabit: (String) -> Unit, modifier: Modifier = Modifier) {
  if (!uiState.hasActiveChallenge) {
    EmptyState(modifier)
    return
  }

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Text(uiState.challengeTitle, style = MaterialTheme.typography.headlineSmall)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      ProgressRing(
        fraction = uiState.progressFraction,
        label = TodayCopy.progressLabel(checked = uiState.habits.count { it.checkedToday }, total = uiState.habits.size),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      uiState.habits.forEach { habit -> HabitRow(habit = habit, onToggle = { onToggleHabit(habit.habitId) }) }
    }

    SummaryStrip(uiState)
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
