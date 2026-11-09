package io.github.unofficialmonsender

import android.content.Context
import io.github.unofficialmonsender.data.monloader.MonLoaderApi
import io.github.unofficialmonsender.data.monloader.MonLoaderRepository
import io.github.unofficialmonsender.data.monloader.PairingManager
import io.github.unofficialmonsender.data.monloader.SiteCatalog
import io.github.unofficialmonsender.data.settings.SettingsRepository
import io.github.unofficialmonsender.detection.DetectionLog
import io.github.unofficialmonsender.link.LinkInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    private val api = MonLoaderApi()
    private val siteCatalog = SiteCatalog(api)

    val monLoader = MonLoaderRepository(settings, api, siteCatalog, appScope)
    val pairing = PairingManager(settings, api, appScope)
    val linkInspector = LinkInspector(settings, monLoader)
    val detectionLog = DetectionLog()
}
