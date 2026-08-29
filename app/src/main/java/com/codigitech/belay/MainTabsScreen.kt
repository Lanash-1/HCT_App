package com.codigitech.belay

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codigitech.belay.ui.profile.ProfileRoute
import com.codigitech.belay.ui.recap.RecapRoute
import com.codigitech.belay.ui.today.TodayRoute
import com.codigitech.belay.ui.watching.WatchingRoute

private enum class MainTab(val label: String) {
  Today("Today"),
  Recap("Recap"),
  Watching("Watching"),
  Profile("Profile"),
}

private fun tabsFor(mode: String): List<MainTab> =
  if (mode == "witness") listOf(MainTab.Watching, MainTab.Profile) else listOf(MainTab.Today, MainTab.Recap, MainTab.Profile)

@Composable
fun MainTabsScreen(mode: String, onOpenWitnessDetail: (String) -> Unit, onSignedOut: () -> Unit, modifier: Modifier = Modifier) {
  val tabs = tabsFor(mode)
  var selectedTab by remember { mutableStateOf(tabs.first()) }
  LaunchedEffect(mode) {
    if (selectedTab !in tabs) selectedTab = tabs.first()
  }

  Scaffold(
    modifier = modifier,
    bottomBar = {
      NavigationBar {
        tabs.forEach { tab ->
          NavigationBarItem(selected = tab == selectedTab, onClick = { selectedTab = tab }, icon = {}, label = { Text(tab.label) })
        }
      }
    }
  ) { innerPadding ->
    val contentModifier = Modifier.padding(innerPadding).padding(16.dp)
    when (selectedTab) {
      MainTab.Today -> TodayRoute(modifier = contentModifier)
      MainTab.Recap -> RecapRoute(modifier = contentModifier)
      MainTab.Watching -> WatchingRoute(onOpenPerson = onOpenWitnessDetail, modifier = contentModifier)
      MainTab.Profile -> ProfileRoute(onOpenPerson = onOpenWitnessDetail, onSignedOut = onSignedOut, modifier = contentModifier)
    }
  }
}
