package io.github.unofficialmonsender.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenApps: () -> Unit,
    onOpenSites: () -> Unit,
) {
    val resources = LocalResources.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.detectionRunning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    LaunchedEffect(settings?.serverUrl, settings?.token) {
        if (settings != null) viewModel.refreshConnection()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshConnection()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = settings
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item { SectionHeader(stringResource(R.string.section_monloader)) }
            item {
                MonLoaderSection(
                    settings = current,
                    connection = connection,
                    pairing = pairing,
                    onSaveServerUrl = viewModel::saveServerUrl,
                    onRefresh = viewModel::refreshConnection,
                    onPair = viewModel::startPairing,
                    onCancelPairing = viewModel::cancelPairing,
                    onAcknowledgePairing = viewModel::acknowledgePairing,
                    onUnpair = viewModel::unpair,
                )
            }
            item { SectionHeader(stringResource(R.string.section_detection)) }
            item {
                DetectionSection(
                    settings = current,
                    serviceRunning = serviceRunning,
                    onUpdate = viewModel::update,
                    onOpenApps = onOpenApps,
                    onOpenSites = onOpenSites,
                    onMessage = viewModel::message,
                )
            }
            item { SectionHeader(stringResource(R.string.section_overlay)) }
            item { OverlaySection(current, viewModel::update) }
            item {
                Text(
                    text = stringResource(R.string.footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                )
            }
        }
    }
}
