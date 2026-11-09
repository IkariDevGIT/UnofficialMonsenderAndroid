package io.github.unofficialmonsender.detection

import io.github.unofficialmonsender.link.SkipReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DetectionOutcome {
    SHOWN,
    NO_LINK,
    UNREADABLE,
    REPEATED,
    NOT_CONFIGURED,
    APP_FILTERED,
    SITE_BLOCKED,
    UNSUPPORTED,
    ;

    companion object {
        fun of(reason: SkipReason): DetectionOutcome = when (reason) {
            SkipReason.DETECTION_OFF, SkipReason.NOT_CONFIGURED -> NOT_CONFIGURED
            SkipReason.APP_FILTERED -> APP_FILTERED
            SkipReason.SITE_BLOCKED -> SITE_BLOCKED
            SkipReason.UNSUPPORTED -> UNSUPPORTED
        }
    }
}

data class DetectionEntry(
    val timeMillis: Long,
    val sourcePackage: String?,
    val url: String?,
    val outcome: DetectionOutcome,
)

/** In-memory record of recent detections, shown in settings to explain why an overlay did or didn't appear. */
class DetectionLog {
    private val _entries = MutableStateFlow<List<DetectionEntry>>(emptyList())
    val entries: StateFlow<List<DetectionEntry>> = _entries.asStateFlow()

    fun add(sourcePackage: String?, url: String?, outcome: DetectionOutcome) {
        val entry = DetectionEntry(System.currentTimeMillis(), sourcePackage, url, outcome)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    private companion object {
        const val MAX_ENTRIES = 20
    }
}
