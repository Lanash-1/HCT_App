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
import com.codigitech.belay.ui.today.TodayScreen

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
              backStack.add(Today)
            },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<Today> { TodayScreen(modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
      },
  )
}
