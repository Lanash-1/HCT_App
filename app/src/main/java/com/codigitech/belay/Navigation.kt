package com.codigitech.belay

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.codigitech.belay.ui.auth.AuthRoute
import com.codigitech.belay.ui.createchallenge.CreateChallengeRoute
import com.codigitech.belay.ui.onboarding.OnboardingRole
import com.codigitech.belay.ui.onboarding.OnboardingRoute
import com.codigitech.belay.ui.today.TodayRoute
import com.codigitech.belay.ui.watching.WatchingRoute
import com.codigitech.belay.ui.witnessdetail.WitnessDetailRoute

@Composable
fun MainNavigation() {
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
            onContinue = { role ->
              backStack.clear()
              backStack.add(if (role == OnboardingRole.Challenger) CreateChallenge else Watching)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<CreateChallenge> {
          CreateChallengeRoute(
            onDone = {
              backStack.clear()
              backStack.add(Today)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<Today> { TodayRoute(modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
        entry<Watching> {
          WatchingRoute(
            onOpenPerson = { challengeId -> backStack.add(WitnessDetail(challengeId)) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<WitnessDetail> { key ->
          WitnessDetailRoute(challengeId = key.challengeId, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
      },
  )
}
