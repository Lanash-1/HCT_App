package com.codigitech.belay.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.codigitech.belay.data.repository.CheerOrNudgeResult
import kotlinx.coroutines.launch

/** Centralized user-facing copy for the shared cheer/nudge compose dialog. */
object CheerNudgeCopy {
  const val CHEER_TITLE = "Send a cheer"
  const val NUDGE_TITLE = "Send a nudge"
  const val PLACEHOLDER = "Say something..."
  const val SEND_LABEL = "Send"
  const val CANCEL_LABEL = "Cancel"
  const val MESSAGE_BLANK_ERROR = "Say something first."
  const val MESSAGE_TOO_LONG_ERROR = "Keep it under 140 characters."
  const val ALREADY_NUDGED_ERROR = "Already nudged today — try a cheer instead."

  fun errorFor(result: CheerOrNudgeResult): String? =
    when (result) {
      is CheerOrNudgeResult.Success -> null
      CheerOrNudgeResult.MessageBlank -> MESSAGE_BLANK_ERROR
      CheerOrNudgeResult.MessageTooLong -> MESSAGE_TOO_LONG_ERROR
      CheerOrNudgeResult.AlreadyNudgedToday -> ALREADY_NUDGED_ERROR
    }
}

/** A free-text compose dialog for sending a cheer or nudge (PRD §6.10 — witness-typed, not preset copy). */
@Composable
fun CheerNudgeDialog(isNudge: Boolean, onDismiss: () -> Unit, onSend: suspend (String) -> CheerOrNudgeResult, modifier: Modifier = Modifier) {
  var message by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var sending by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  AlertDialog(
    modifier = modifier,
    onDismissRequest = onDismiss,
    title = { Text(if (isNudge) CheerNudgeCopy.NUDGE_TITLE else CheerNudgeCopy.CHEER_TITLE) },
    text = {
      Column {
        OutlinedTextField(
          value = message,
          onValueChange = { message = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text(CheerNudgeCopy.PLACEHOLDER) },
          isError = error != null,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !sending,
        onClick = {
          sending = true
          scope.launch {
            val result = onSend(message)
            sending = false
            val resultError = CheerNudgeCopy.errorFor(result)
            if (resultError == null) onDismiss() else error = resultError
          }
        },
      ) {
        Text(CheerNudgeCopy.SEND_LABEL)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(CheerNudgeCopy.CANCEL_LABEL) } },
  )
}
