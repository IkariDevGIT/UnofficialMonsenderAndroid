package io.github.unofficialmonsender.data.monloader

sealed interface ConnectionState {
    data object NotConfigured : ConnectionState
    data object Checking : ConnectionState
    data class Online(val version: String?, val galleryDlVersion: String?) : ConnectionState
    data object Unreachable : ConnectionState
    data object NotMonLoader : ConnectionState
    data object NotPaired : ConnectionState
    data object TokenRejected : ConnectionState
    data object SetupRequired : ConnectionState
}

enum class SendFailure {
    NOT_CONFIGURED,
    UNREACHABLE,
    NOT_PAIRED,
    TOKEN_REJECTED,
    MONBOORU_UNCONFIGURED,
    MONBOORU_PAUSED,
    SETUP_REQUIRED,
    INVALID_URL,
    REFUSED,
}

sealed interface SendResult {
    data class Queued(val jobId: Long?) : SendResult
    data class Failed(val reason: SendFailure, val detail: String? = null) : SendResult
}

enum class JobStatus {
    QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, CANCELED, INTERRUPTED, REMOVED;

    val isFinished: Boolean get() = this != QUEUED && this != RUNNING

    companion object {
        fun of(raw: String): JobStatus? = when (raw) {
            "queued" -> QUEUED
            "running" -> RUNNING
            "succeeded" -> SUCCEEDED
            "partial" -> PARTIAL
            "failed" -> FAILED
            "canceled" -> CANCELED
            "interrupted" -> INTERRUPTED
            else -> null
        }
    }
}

/** A job's state as MonLoader's queue reports it. */
data class JobSnapshot(
    val status: JobStatus,
    val created: Int = 0,
    val duplicate: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val error: String? = null,
)

internal object Outcomes {
    /** Same classification MonSender's `connState` applies to /health plus one authed call. */
    fun connection(health: ApiResponse, authed: ApiResponse?, tokenHeld: Boolean): ConnectionState {
        if (health.isNetworkError || health.status == 0) return ConnectionState.Unreachable
        val info = health.decode(Health.serializer())
        if (info?.app != null && info.app != "monloader") return ConnectionState.NotMonLoader
        val code = authed?.error?.code
        return when {
            authed?.status == 401 -> if (tokenHeld) ConnectionState.TokenRejected else ConnectionState.NotPaired
            authed?.status == 503 && code == "api_disabled" -> ConnectionState.NotPaired
            authed?.status == 409 && code == "setup_required" -> ConnectionState.SetupRequired
            !tokenHeld -> ConnectionState.NotPaired
            health.isSuccessful && info != null -> ConnectionState.Online(info.version, info.galleryDlVersion)
            else -> ConnectionState.Unreachable
        }
    }

    /** Mirrors MonSender's `enqueueOutcome` for a send without `?wait`. */
    fun send(response: ApiResponse, tokenHeld: Boolean): SendResult {
        if (response.isNetworkError || response.status == 0) return SendResult.Failed(SendFailure.UNREACHABLE)
        return when (response.status) {
            202 -> SendResult.Queued(response.decode(JobAccepted.serializer())?.jobId)
            200 -> SendResult.Queued(response.decode(JobRef.serializer())?.id)
            401 -> SendResult.Failed(if (tokenHeld) SendFailure.TOKEN_REJECTED else SendFailure.NOT_PAIRED)
            else -> {
                val error = response.error
                val reason = when (error?.code) {
                    "monbooru_unconfigured" -> SendFailure.MONBOORU_UNCONFIGURED
                    "monbooru_paused" -> SendFailure.MONBOORU_PAUSED
                    "api_disabled" -> SendFailure.NOT_PAIRED
                    "setup_required" -> SendFailure.SETUP_REQUIRED
                    "invalid_request" -> SendFailure.INVALID_URL
                    else -> SendFailure.REFUSED
                }
                SendResult.Failed(reason, error?.error ?: "HTTP ${response.status}")
            }
        }
    }
}
