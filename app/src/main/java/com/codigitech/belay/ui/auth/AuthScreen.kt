package com.codigitech.belay.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthRoute(onAuthenticated: () -> Unit, modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var mode by remember { mutableStateOf(AuthMode.LogIn) }

  LaunchedEffect(uiState.signedInUserId) {
    if (uiState.signedInUserId != null) onAuthenticated()
  }

  AuthScreen(
    uiState = uiState,
    mode = mode,
    onEmailChange = viewModel::onEmailChange,
    onPasswordChange = viewModel::onPasswordChange,
    onModeToggle = { mode = if (mode == AuthMode.LogIn) AuthMode.SignUp else AuthMode.LogIn },
    onSubmit = { viewModel.submit(mode) },
    modifier = modifier,
  )
}

@Composable
fun AuthScreen(
  uiState: AuthUiState,
  mode: AuthMode,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onModeToggle: () -> Unit,
  onSubmit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(text = AuthCopy.TITLE, style = MaterialTheme.typography.headlineLarge)
    Text(
      text = AuthCopy.SUBTITLE,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
    )

    OutlinedTextField(
      value = uiState.email,
      onValueChange = onEmailChange,
      label = { Text(AuthCopy.EMAIL_LABEL) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = uiState.password,
      onValueChange = onPasswordChange,
      label = { Text(AuthCopy.PASSWORD_LABEL) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    uiState.errorMessage?.let {
      Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }

    Button(onClick = onSubmit, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
      if (uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = MaterialTheme.colorScheme.onPrimary)
      } else {
        Text(if (mode == AuthMode.SignUp) AuthCopy.SIGN_UP_SUBMIT else AuthCopy.LOG_IN_SUBMIT)
      }
    }

    TextButton(onClick = onModeToggle, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
      Text(
        if (mode == AuthMode.SignUp) AuthCopy.SWITCH_TO_LOG_IN else AuthCopy.SWITCH_TO_SIGN_UP,
        textAlign = TextAlign.Center,
      )
    }
  }
}
