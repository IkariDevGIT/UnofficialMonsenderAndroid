package io.github.ikaridevgit.unofficialmonsender.detection

import io.github.ikaridevgit.unofficialmonsender.data.monloader.JobSnapshot
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendFailure
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * The links that were sent or failed. Kept in [file] so the list survives restarts. A link whose
 * prompt is still open is held only in memory and hidden, and is dropped if the prompt is closed
 * without sending. Only links are ever stored, and only ones the caller has cleared for logging.
 */
class DetectionLog(private val file: File, scope: CoroutineScope) {
    @Volatile private var enabled = true

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DetectionEntry>> = _entries
        .map { list -> list.filter { it.delivery != Delivery.Waiting } }
        .stateIn(scope, SharingStarted.Eagerly, _entries.value)
    private val ids = AtomicLong(_entries.value.maxOfOrNull { it.id } ?: 0L)

    init {
        scope.launch(Dispatchers.IO) {
            entries.drop(1).collectLatest {
                delay(SAVE_DELAY_MS)
                save(it)
            }
        }
    }

    /** Null when logging is off; the link is then sent without leaving a trace. */
    fun add(sourcePackage: String?, url: String, origin: LinkOrigin = LinkOrigin.COPY): Long? =
        put(DetectionEntry(ids.incrementAndGet(), System.currentTimeMillis(), sourcePackage, url, DetectionOutcome.SHOWN, Delivery.Waiting, origin))

    /** Records that a copy couldn't be sent because MonLoader isn't set up, without the link itself. */
    fun notConfigured(sourcePackage: String?): Long? =
        put(DetectionEntry(ids.incrementAndGet(), System.currentTimeMillis(), sourcePackage, null, DetectionOutcome.NOT_CONFIGURED))

    private fun put(entry: DetectionEntry): Long? {
        if (!enabled) return null
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
        return entry.id
    }

    /** Turning logging off forgets everything at once, file included. */
    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) clear()
    }

    fun clear() {
        _entries.value = emptyList()
        file.delete()
    }

    fun setDelivery(id: Long, delivery: Delivery) = edit(id) { it.copy(delivery = delivery) }

    fun setJob(id: Long, job: JobSnapshot) = edit(id) {
        val queued = it.delivery as? Delivery.Queued ?: return@edit it
        it.copy(delivery = queued.copy(job = job))
    }

    /** Closing a prompt without sending forgets the link. */
    fun promptClosed(id: Long) {
        _entries.update { list -> list.filterNot { it.id == id && it.delivery == Delivery.Waiting } }
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
        json.decodeFromString(serializer, file.readText())
            .filter { it.isSettled }
            .map { if (it.outcome == DetectionOutcome.NOT_CONFIGURED) it.copy(url = null) else it }
    }.getOrDefault(emptyList())

    private fun save(list: List<DetectionEntry>) {
        if (!enabled) return
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(serializer, list))
            temp.renameTo(file)
        }
    }

    private companion object {
        val DetectionEntry.isSettled
            get() = delivery != Delivery.Waiting && delivery != Delivery.Sending && delivery != Delivery.Dismissed
        const val MAX_ENTRIES = 20
        const val SAVE_DELAY_MS = 400L
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(DetectionEntry.serializer())
    }
}
