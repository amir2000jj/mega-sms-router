package com.megasmsrouter.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.megasmsrouter.R
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.screens.*

object Routes {
    const val HOME = "home"
    const val DESTINATIONS = "destinations"
    const val RULES = "rules"
    const val HISTORY = "history"
    const val ABOUT = "about"
    const val DESTINATION_EDIT = "destination/{id}"
    const val RULE_EDIT = "rule/{id}"
}

@Composable
fun MsrAppRoot(vm: MsrViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Triple(Routes.HOME, R.string.nav_home, "\uD83C\uDFE0"),
        Triple(Routes.DESTINATIONS, R.string.nav_destinations, "\uD83C\uDF10"),
        Triple(Routes.RULES, R.string.nav_rules, "\uD83D\uDCCB"),
        Triple(Routes.HISTORY, R.string.nav_history, "\uD83D\uDD52")
    )

    Scaffold(
        bottomBar = {
            if (tabs.any { it.first == route }) {
                NavigationBar {
                    tabs.forEach { (r, label, glyph) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(glyph) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) { HomeScreen(vm, onAbout = { nav.navigate(Routes.ABOUT) }) }
            composable(Routes.DESTINATIONS) {
                DestinationsScreen(vm, onEdit = { nav.navigate("destination/$it") })
            }
            composable(Routes.RULES) { RulesScreen(vm, onEdit = { nav.navigate("rule/$it") }) }
            composable(Routes.HISTORY) { HistoryScreen(vm) }
            composable(Routes.ABOUT) { AboutScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                Routes.DESTINATION_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { e ->
                DestinationEditorScreen(vm, e.arguments?.getString("id") ?: "new", onClose = { nav.popBackStack() })
            }
            composable(
                Routes.RULE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { e ->
                RuleEditorScreen(vm, e.arguments?.getString("id") ?: "new", onClose = { nav.popBackStack() })
            }
        }
    }
}
