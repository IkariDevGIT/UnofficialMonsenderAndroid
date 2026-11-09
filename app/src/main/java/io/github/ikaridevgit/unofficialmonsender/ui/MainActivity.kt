package io.github.ikaridevgit.unofficialmonsender.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.ui.components.KittyStage
import io.github.ikaridevgit.unofficialmonsender.ui.components.LocalKitty
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonTab
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonTabBar
import io.github.ikaridevgit.unofficialmonsender.ui.components.rememberKitty
import io.github.ikaridevgit.unofficialmonsender.ui.detections.DetectionsScreen
import io.github.ikaridevgit.unofficialmonsender.ui.filters.AppFilterScreen
import io.github.ikaridevgit.unofficialmonsender.ui.filters.SiteRulesScreen
import io.github.ikaridevgit.unofficialmonsender.ui.settings.SettingsScreen
import io.github.ikaridevgit.unofficialmonsender.ui.settings.SettingsViewModel
import io.github.ikaridevgit.unofficialmonsender.ui.settings.WelcomeDialog
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonsenderTheme
import kotlinx.serialization.Serializable

@Serializable
private data object DetectionsRoute

@Serializable
private data object SettingsRoute

@Serializable
private data object AppsRoute

@Serializable
private data object SitesRoute

private enum class Tab(val route: Any, val label: Int) {
    DETECTIONS(DetectionsRoute, R.string.tab_detections),
    SETTINGS(SettingsRoute, R.string.tab_settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MonsenderTheme {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val nav = rememberNavController()
                val backStackEntry by nav.currentBackStackEntryAsState()
                val destination = backStackEntry?.destination
                val currentTab = Tab.entries.firstOrNull { tab ->
                    destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                }

                if (settings?.welcomeSeen == false) {
                    WelcomeDialog(onDismiss = { viewModel.update { it.copy(welcomeSeen = true) } })
                }

                val kitty = rememberKitty()
                CompositionLocalProvider(LocalKitty provides kitty) {
                    Box(Modifier.fillMaxSize()) {
                        AppScaffold(viewModel, settings, nav, currentTab)
                        KittyStage(kitty)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(
    viewModel: SettingsViewModel,
    settings: AppSettings?,
    nav: NavHostController,
    currentTab: Tab?,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                MonTabBar(
                    Tab.entries.map { tab ->
                        MonTab(
                            label = stringResource(tab.label),
                            selected = tab == currentTab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = DetectionsRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<DetectionsRoute> { DetectionsScreen(viewModel) }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenApps = { nav.navigate(AppsRoute) },
                    onOpenSites = { nav.navigate(SitesRoute) },
                )
            }
            composable<AppsRoute> {
                settings?.let { AppFilterScreen(it, viewModel::update, onBack = nav::navigateUp) }
            }
            composable<SitesRoute> {
                settings?.let { SiteRulesScreen(it, viewModel::update, onBack = nav::navigateUp) }
            }
        }
    }
}
