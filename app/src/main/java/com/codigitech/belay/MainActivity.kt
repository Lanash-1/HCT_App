package com.codigitech.belay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.codigitech.belay.theme.BelayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  /** Set when the app was opened by a pairing invite link (PRD §6.11), consumed once by onboarding. */
  private val pendingPairingLink = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingPairingLink.value = intent?.dataString

    enableEdgeToEdge()
    setContent {
      val sessionViewModel: AppSessionViewModel = hiltViewModel()
      val sessionState by sessionViewModel.uiState.collectAsState()
      val darkTheme =
        when (sessionState.themePref) {
          "dark" -> true
          "light" -> false
          else -> isSystemInDarkTheme()
        }
      BelayTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(pairingLink = pendingPairingLink.value, onPairingLinkHandled = { pendingPairingLink.value = null })
        }
      }
    }
  }

  /** The activity is single-instance for App Links in practice — a second invite arrives here, not in onCreate. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    pendingPairingLink.value = intent.dataString
  }
}
