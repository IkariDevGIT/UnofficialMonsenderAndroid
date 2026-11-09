package io.github.ikaridevgit.unofficialmonsender.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.ikaridevgit.unofficialmonsender.AppContainer
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.appContainer
import io.github.ikaridevgit.unofficialmonsender.data.monloader.ConnectionState
import io.github.ikaridevgit.unofficialmonsender.data.monloader.PairingState
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import io.github.ikaridevgit.unofficialmonsender.detection.CopyDetectionService
import io.github.ikaridevgit.unofficialmonsender.detection.DetectionEntry
import io.github.ikaridevgit.unofficialmonsender.link.Links
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Checking)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    val pairing: StateFlow<PairingState> = container.pairing.state
    val detectionRunning: StateFlow<Boolean> = CopyDetectionService.running
    val detectionLog: StateFlow<List<DetectionEntry>> = container.detectionLog.entries

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private var connectionJob: Job? = null

    fun refreshConnection() {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            _connection.value = ConnectionState.Checking
            _connection.value = container.monLoader.checkConnection()
        }
    }

    /** Returns false right away when the address can't be parsed. */
    fun saveServerUrl(raw: String): Boolean {
        if (Links.normalizeServerUrl(raw) == null) return false
        if (container.pairing.isRunning) container.pairing.cancel()
        viewModelScope.launch { container.monLoader.setServerUrl(raw) }
        return true
    }

    fun startPairing() = container.pairing.start()

    fun cancelPairing() = container.pairing.cancel()

    fun acknowledgePairing() = container.pairing.acknowledge()

    fun unpair() {
        viewModelScope.launch {
            val notified = container.monLoader.unpair()
            message(if (notified) R.string.message_unpaired else R.string.message_unpaired_offline)
        }
    }

    fun resend(entry: DetectionEntry) = container.linkSender.resend(entry)

    fun copiedByApp(text: String) = container.linkSender.copiedByApp(text)

    fun clearLog() = container.detectionLog.clear()

    private val _supportedDomains = MutableStateFlow<List<String>>(emptyList())
    val supportedDomains: StateFlow<List<String>> = _supportedDomains.asStateFlow()

    fun loadSupportedDomains() {
        viewModelScope.launch { _supportedDomains.value = container.monLoader.supportedDomains() }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun message(@StringRes text: Int) {
        _messages.trySend(text)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(this[APPLICATION_KEY]!!.appContainer) }
        }
    }
}
