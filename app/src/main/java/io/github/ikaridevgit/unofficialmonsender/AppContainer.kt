package io.github.ikaridevgit.unofficialmonsender

import android.content.Context
import io.github.ikaridevgit.unofficialmonsender.data.monloader.MonLoaderApi
import io.github.ikaridevgit.unofficialmonsender.data.monloader.MonLoaderRepository
import io.github.ikaridevgit.unofficialmonsender.data.monloader.PairingManager
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteCatalog
import io.github.ikaridevgit.unofficialmonsender.data.settings.SettingsRepository
import io.github.ikaridevgit.unofficialmonsender.detection.DetectionLog
import io.github.ikaridevgit.unofficialmonsender.detection.JobTracker
import io.github.ikaridevgit.unofficialmonsender.link.LinkInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    private val api = MonLoaderApi()
    private val siteCatalog = SiteCatalog(api)

    val monLoader = MonLoaderRepository(settings, api, siteCatalog, appScope)
    val pairing = PairingManager(settings, api, appScope)
    val linkInspector = LinkInspector(settings, monLoader)
    val detectionLog = DetectionLog(File(context.filesDir, "detections.json"), appScope)
    val jobTracker = JobTracker(detectionLog, monLoader, appScope)

    init {
        detectionLog.unfinishedJobs().forEach { (entry, job) -> jobTracker.track(entry, job) }
    }
}
