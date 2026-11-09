package io.github.unofficialmonsender.detection

import io.github.unofficialmonsender.data.monloader.SendFailure
import io.github.unofficialmonsender.data.monloader.SendResult
import io.github.unofficialmonsender.link.SkipReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

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

/** What happened to a link after its prompt was shown. */
sealed interface Delivery {
    data object Waiting : Delivery
    data object Sending : Delivery
    data object Sent : Delivery
    data class Failed(val reason: SendFailure, val detail: String?) : Delivery
    data object Dismissed : Delivery

    companion object {
        fun of(result: SendResult): Delivery = when (result) {
            is SendResult.Queued -> Sent
            is SendResult.Failed -> Failed(result.reason, result.detail)
        }
    }
}

data class DetectionEntry(
    val id: Long,
    val timeMillis: Long,
    val sourcePackage: String?,
    val url: String?,
    val outcome: DetectionOutcome,
    val delivery: Delivery? = null,
)

/** In-memory record of the latest detections and, for prompted links, whether they were sent. */
class DetectionLog {
    private val ids = AtomicLong()
    private val _entries = MutableStateFlow<List<DetectionEntry>>(emptyList())
    val entries: StateFlow<List<DetectionEntry>> = _entries.asStateFlow()

    fun add(sourcePackage: String?, url: String?, outcome: DetectionOutcome): Long {
        val delivery = if (outcome == DetectionOutcome.SHOWN) Delivery.Waiting else null
        val entry = DetectionEntry(ids.incrementAndGet(), System.currentTimeMillis(), sourcePackage, url, outcome, delivery)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
        return entry.id
    }

    fun setDelivery(id: Long, delivery: Delivery) = edit(id) { it.copy(delivery = delivery) }

    /** Closing a prompt that was never sent, or whose send was cut off, counts as dismissed. */
    fun promptClosed(id: Long) = edit(id) {
        if (it.delivery == Delivery.Waiting || it.delivery == Delivery.Sending) it.copy(delivery = Delivery.Dismissed) else it
    }

    private fun edit(id: Long, transform: (DetectionEntry) -> DetectionEntry) {
        _entries.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private companion object {
        const val MAX_ENTRIES = 20
    }
}
