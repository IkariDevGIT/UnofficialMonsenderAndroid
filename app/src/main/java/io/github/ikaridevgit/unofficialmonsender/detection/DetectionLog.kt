package io.github.ikaridevgit.unofficialmonsender.detection

import io.github.ikaridevgit.unofficialmonsender.data.monloader.JobSnapshot
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendFailure
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Only real results are logged: a link that got the prompt, or one that couldn't because MonLoader
 * isn't set up. Copies the user's own filters skip are left out, as are false alarms.
 */
enum class DetectionOutcome { SHOWN, NOT_CONFIGURED }

/** How a link reached the app: noticed on the clipboard, shared to it, or sent from the tile. */
enum class LinkOrigin { COPY, SHARE, TILE }

/** What happened to a link after its prompt was shown. */
@Serializable
sealed interface Delivery {
    @Serializable
    data object Waiting : Delivery

    @Serializable
    data object Sending : Delivery

    @Serializable
    data class Failed(val reason: SendFailure, val detail: String?) : Delivery

    @Serializable
    data object Dismissed : Delivery

    /** Accepted by MonLoader; [job] follows the job through its queue once it has been checked. */
    @Serializable
    data class Queued(val jobId: Long?, val job: JobSnapshot? = null) : Delivery

    companion object {
        fun of(result: SendResult): Delivery = when (result) {
            is SendResult.Queued -> Queued(result.jobId?.takeIf { it > 0 })
            is SendResult.Failed -> Failed(result.reason, result.detail)
        }
    }
}

@Serializable
data class DetectionEntry(
    val id: Long,
    val timeMillis: Long,
    val sourcePackage: String?,
    val url: String?,
    val outcome: DetectionOutcome,
    val delivery: Delivery? = null,
    val origin: LinkOrigin = LinkOrigin.COPY,
)

/**
 * The latest links and, for prompted ones, whether they were sent. Kept in [file] so the list
 * survives restarts; a prompt still open when the app stopped counts as dismissed.
 */
class DetectionLog(private val file: File, scope: CoroutineScope) {
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DetectionEntry>> = _entries.asStateFlow()
    private val ids = AtomicLong(_entries.value.maxOfOrNull { it.id } ?: 0L)

    init {
        scope.launch(Dispatchers.IO) {
            entries.drop(1).collectLatest {
                delay(SAVE_DELAY_MS)
                save(it)
            }
        }
    }

    fun add(sourcePackage: String?, url: String?, outcome: DetectionOutcome, origin: LinkOrigin = LinkOrigin.COPY): Long {
        val delivery = if (outcome == DetectionOutcome.SHOWN) Delivery.Waiting else null
        val entry = DetectionEntry(ids.incrementAndGet(), System.currentTimeMillis(), sourcePackage, url, outcome, delivery, origin)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
        return entry.id
    }

    fun setDelivery(id: Long, delivery: Delivery) = edit(id) { it.copy(delivery = delivery) }

    fun setJob(id: Long, job: JobSnapshot) = edit(id) {
        val queued = it.delivery as? Delivery.Queued ?: return@edit it
        it.copy(delivery = queued.copy(job = job))
    }

    /** Closing a prompt that was never sent, or whose send was cut off, counts as dismissed. */
    fun promptClosed(id: Long) = edit(id) {
        if (it.delivery == Delivery.Waiting || it.delivery == Delivery.Sending) it.copy(delivery = Delivery.Dismissed) else it
    }

    /** Sent links whose job hadn't finished when the app last stopped. */
    fun unfinishedJobs(): List<Pair<Long, Long>> = _entries.value.mapNotNull { entry ->
        val queued = entry.delivery as? Delivery.Queued ?: return@mapNotNull null
        val jobId = queued.jobId ?: return@mapNotNull null
        if (queued.job?.status?.isFinished == true) null else entry.id to jobId
    }

    private fun edit(id: Long, transform: (DetectionEntry) -> DetectionEntry) {
        _entries.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun load(): List<DetectionEntry> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString(serializer, file.readText()).map {
            if (it.delivery == Delivery.Waiting || it.delivery == Delivery.Sending) it.copy(delivery = Delivery.Dismissed) else it
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<DetectionEntry>) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(serializer, list))
            temp.renameTo(file)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 20
        const val SAVE_DELAY_MS = 400L
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(DetectionEntry.serializer())
    }
}
