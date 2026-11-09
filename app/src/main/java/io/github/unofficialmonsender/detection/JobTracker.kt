package io.github.unofficialmonsender.detection

import android.os.SystemClock
import io.github.unofficialmonsender.data.monloader.MonLoaderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Polls MonLoader's queue for each sent link until its job finishes, so the log shows the real outcome. */
class JobTracker(
    private val log: DetectionLog,
    private val monLoader: MonLoaderRepository,
    private val scope: CoroutineScope,
) {
    fun track(entryId: Long, jobId: Long) {
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            while (true) {
                val job = monLoader.job(jobId)
                if (job != null) {
                    log.setJob(entryId, job)
                    if (job.status.isFinished) return@launch
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                if (elapsed > GIVE_UP_MS) return@launch
                delay(if (elapsed < FAST_PHASE_MS) FAST_INTERVAL_MS else SLOW_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val FAST_INTERVAL_MS = 2_000L
        const val SLOW_INTERVAL_MS = 15_000L
        const val FAST_PHASE_MS = 2 * 60_000L
        const val GIVE_UP_MS = 60 * 60_000L
    }
}
