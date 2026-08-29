package com.codigitech.belay.ui.onboarding

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingRoute(onContinue: () -> Unit, modifier: Modifier = Modifier, viewModel: OnboardingViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.didContinue) {
    if (uiState.didContinue) onContinue()
  }

  OnboardingScreen(
    uiState = uiState,
    onPickRole = viewModel::pickRole,
    onPairCodeInputChange = viewModel::onPairCodeInputChange,
    onSubmitPairCode = viewModel::submitPairCode,
    onContinueClick = viewModel::continueOnboarding,
    modifier = modifier,
  )
}

@Composable
fun OnboardingScreen(
  uiState: OnboardingUiState,
  onPickRole: (OnboardingRole) -> Unit,
  onPairCodeInputChange: (String) -> Unit,
  onSubmitPairCode: () -> Unit,
  onContinueClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Text(text = OnboardingCopy.HEADLINE, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
      Text(text = OnboardingCopy.SUBTITLE, style = MaterialTheme.typography.bodyMedium)

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoleCard(
          title = OnboardingCopy.ROLE_CHALLENGER_TITLE,
          detail = OnboardingCopy.ROLE_CHALLENGER_DETAIL,
          selected = uiState.role == OnboardingRole.Challenger,
          onClick = { onPickRole(OnboardingRole.Challenger) },
        )
        RoleCard(
          title = OnboardingCopy.ROLE_WITNESS_TITLE,
          detail = OnboardingCopy.ROLE_WITNESS_DETAIL,
          selected = uiState.role == OnboardingRole.Witness,
          onClick = { onPickRole(OnboardingRole.Witness) },
        )
      }

      when (uiState.role) {
        OnboardingRole.Challenger -> ShareCodeSection(shareCode = uiState.shareCode)
        OnboardingRole.Witness ->
          PairCodeSection(
            codeInput = uiState.pairCodeInput,
            onCodeInputChange = onPairCodeInputChange,
            onSubmit = onSubmitPairCode,
            isLoading = uiState.isLoading,
            success = uiState.pairingSuccess,
            error = uiState.pairingError,
          )
        null -> Unit
      }
    }

    Button(onClick = onContinueClick, enabled = uiState.role != null, modifier = Modifier.fillMaxWidth()) {
      Text(OnboardingCopy.CONTINUE)
    }
  }
}

@Composable
private fun RoleCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    colors =
      CardDefaults.cardColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
      ),
    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (selected) {
          Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
      }
      Text(text = detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
  }
}

@Composable
private fun ShareCodeSection(shareCode: String?) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = OnboardingCopy.SHARE_CODE_LABEL, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Text(text = shareCode ?: "····", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun PairCodeSection(
  codeInput: String,
  onCodeInputChange: (String) -> Unit,
  onSubmit: () -> Unit,
  isLoading: Boolean,
  success: Boolean,
  error: String?,
) {
  Column(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(text = OnboardingCopy.PAIR_CODE_LABEL, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = codeInput,
        onValueChange = onCodeInputChange,
        placeholder = { Text(OnboardingCopy.PAIR_CODE_HINT) },
        singleLine = true,
        enabled = !success,
        modifier = Modifier.weight(1f),
      )
      Button(onClick = onSubmit, enabled = !isLoading && !success && codeInput.isNotBlank()) { Text(OnboardingCopy.PAIR_BUTTON) }
    }
    if (success) {
      Text(text = OnboardingCopy.PAIRED_MESSAGE, color = MaterialTheme.colorScheme.primary)
    }
    error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
  }
}
