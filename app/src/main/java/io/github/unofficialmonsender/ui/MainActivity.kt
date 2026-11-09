package io.github.unofficialmonsender.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.unofficialmonsender.ui.filters.AppFilterScreen
import io.github.unofficialmonsender.ui.filters.SiteRulesScreen
import io.github.unofficialmonsender.ui.settings.SettingsScreen
import io.github.unofficialmonsender.ui.settings.SettingsViewModel
import io.github.unofficialmonsender.ui.theme.MonsenderTheme
import kotlinx.serialization.Serializable

@Serializable
private data object SettingsRoute

@Serializable
private data object AppsRoute

@Serializable
private data object SitesRoute

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
                NavHost(navController = nav, startDestination = SettingsRoute) {
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
    }
}
