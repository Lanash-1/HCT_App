package com.codigitech.belay

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.codigitech.belay.ui.auth.AuthRoute
import com.codigitech.belay.ui.createchallenge.CreateChallengeRoute
import com.codigitech.belay.ui.onboarding.OnboardingRole
import com.codigitech.belay.ui.onboarding.OnboardingRoute
import com.codigitech.belay.ui.witnessdetail.WitnessDetailRoute

@Composable
fun MainNavigation(pairingLink: String? = null, onPairingLinkHandled: () -> Unit = {}) {
  val backStack = rememberNavBackStack(Auth)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Auth> {
          AuthRoute(
            onAuthenticated = {
              backStack.clear()
              backStack.add(Onboarding)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<Onboarding> {
          OnboardingRoute(
            pairingLink = pairingLink,
            onPairingLinkHandled = onPairingLinkHandled,
            onContinue = { role ->
              backStack.clear()
              backStack.add(if (role == OnboardingRole.Challenger) CreateChallenge else MainTabs)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<CreateChallenge> {
          CreateChallengeRoute(
            onDone = {
              backStack.clear()
              backStack.add(MainTabs)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<MainTabs> {
          val sessionViewModel: AppSessionViewModel = hiltViewModel()
          val sessionState by sessionViewModel.uiState.collectAsState()
          MainTabsScreen(
            mode = sessionState.mode,
            onOpenWitnessDetail = { challengeId -> backStack.add(WitnessDetail(challengeId)) },
            onSignedOut = {
              backStack.clear()
              backStack.add(Auth)
            },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<WitnessDetail> { key ->
          WitnessDetailRoute(challengeId = key.challengeId, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
      },
  )
}
