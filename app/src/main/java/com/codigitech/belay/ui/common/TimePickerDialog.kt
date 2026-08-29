@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.codigitech.belay.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** A Material 3 [TimePicker] wrapped in a confirm/cancel dialog — shared by any screen that lets the user pick a reminder time. */
@Composable
fun BelayTimePickerDialog(initial: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  val (initialHour, initialMinute) = parseTime(initial)
  val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TimePicker(state = state)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) { Text("Save") }
        }
      }
    }
  }
}

fun parseTime(time: String?): Pair<Int, Int> {
  val parts = time?.split(":")
  val hour = parts?.getOrNull(0)?.toIntOrNull() ?: 7
  val minute = parts?.getOrNull(1)?.toIntOrNull() ?: 0
  return hour to minute
}
