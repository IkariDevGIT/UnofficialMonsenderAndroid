package io.github.ikaridevgit.unofficialmonsender.detection

import android.os.SystemClock
import io.github.ikaridevgit.unofficialmonsender.data.monloader.MonLoaderRepository
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sends a logged link to MonLoader and keeps its log entry up to date, including the job that follows. */
class LinkSender(
    private val log: DetectionLog,
    private val monLoader: MonLoaderRepository,
    private val tracker: JobTracker,
    private val scope: CoroutineScope,
) {
    @Volatile private var ownCopy: Pair<String, Long>? = null

    /** Sends [url]; with no [entryId] (logging off or not allowed) nothing is recorded. */
    suspend fun send(entryId: Long?, url: String): SendResult {
        report(entryId, Delivery.Sending)
        return monLoader.send(url).also { report(entryId, Delivery.of(it)) }
    }

    fun report(entryId: Long?, delivery: Delivery) {
        if (entryId == null) return
        log.setDelivery(entryId, delivery)
        if (delivery is Delivery.Queued && delivery.jobId != null) tracker.track(entryId, delivery.jobId)
    }

    /** Sends again from the log; keeps going after the screen that asked for it is gone. */
    fun resend(entry: DetectionEntry) {
        val url = entry.url ?: return
        if (entry.delivery == Delivery.Sending) return
        scope.launch { send(entry.id, url) }
    }

    /** Marks a link the app is about to put on the clipboard itself, so copy detection leaves it alone. */
    fun copiedByApp(text: String) {
        ownCopy = text to SystemClock.elapsedRealtime()
    }

    fun isOwnCopy(text: String): Boolean {
        val (own, at) = ownCopy ?: return false
        return own == text && SystemClock.elapsedRealtime() - at < OWN_COPY_MS
    }

    private companion object {
        const val OWN_COPY_MS = 10_000L
    }
}
