package io.github.unofficialmonsender.data.monloader

import io.github.unofficialmonsender.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PairingFailure { NOT_CONFIGURED, UNREACHABLE, ALREADY_PAIRED, DENIED, EXPIRED, TOO_MANY_REQUESTS, REFUSED }

sealed interface PairingState {
    data object Idle : PairingState
    data object Requesting : PairingState
    data object WaitingForApproval : PairingState
    data object Paired : PairingState
    data class Failed(val reason: PairingFailure, val detail: String? = null) : PairingState
}

/**
 * MonLoader's pairing handshake: offer a request, then poll until the operator approves it under
 * Settings → Pairing. The token is handed out once, on the first poll after approval.
 */
class PairingManager(
    private val settings: SettingsRepository,
    private val api: MonLoaderApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        job = scope.launch { _state.value = pair() }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = PairingState.Idle
    }

    fun acknowledge() {
        if (!isRunning) _state.value = PairingState.Idle
    }

    private suspend fun pair(): PairingState {
        val base = settings.current().serverUrl
        if (base.isEmpty()) return PairingState.Failed(PairingFailure.NOT_CONFIGURED)

        _state.value = PairingState.Requesting
        val offer = api.pairRequest(base, APP_NAME)
        if (offer.isNetworkError) return PairingState.Failed(PairingFailure.UNREACHABLE)
        val requestId = offer.decode(PairAccepted.serializer())?.requestId
        if (!offer.isSuccessful || requestId.isNullOrEmpty()) {
            val error = offer.error
            val reason = when {
                offer.status == 409 -> PairingFailure.ALREADY_PAIRED
                offer.status == 429 -> PairingFailure.TOO_MANY_REQUESTS
                else -> PairingFailure.REFUSED
            }
            return PairingState.Failed(reason, error?.error ?: "HTTP ${offer.status}")
        }

        _state.value = PairingState.WaitingForApproval
        repeat(MAX_POLLS) {
            delay(POLL_INTERVAL_MS)
            val poll = api.pairStatus(base, requestId)
            if (poll.status == 404) return PairingState.Failed(PairingFailure.EXPIRED)
            val status = if (poll.isSuccessful) poll.decode(PairStatus.serializer()) else null
            when (status?.status) {
                "approved" -> if (!status.token.isNullOrEmpty()) {
                    val saved = settings.update { s ->
                        if (s.serverUrl == base) s.copy(token = status.token) else s
                    }
                    return if (saved.serverUrl == base) PairingState.Paired else PairingState.Idle
                }
                "denied" -> return PairingState.Failed(PairingFailure.DENIED)
            }
        }
        return PairingState.Failed(PairingFailure.EXPIRED)
    }

    companion object {
        const val APP_NAME = "UnofficialMonsenderAndroid"
        private const val POLL_INTERVAL_MS = 1500L
        private const val MAX_POLLS = 200
    }
}
