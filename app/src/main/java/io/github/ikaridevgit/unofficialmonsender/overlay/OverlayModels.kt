package io.github.ikaridevgit.unofficialmonsender.overlay

import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendFailure
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteSupport
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import io.github.ikaridevgit.unofficialmonsender.detection.LinkOrigin
import io.github.ikaridevgit.unofficialmonsender.link.LinkCheck

data class OverlayRequest(
    val url: String,
    val host: String,
    val siteName: String?,
    val support: SiteSupport,
    val sourceLabel: String?,
    val origin: LinkOrigin,
) {
    companion object {
        fun from(check: LinkCheck, sourceLabel: String?, origin: LinkOrigin = LinkOrigin.COPY) = OverlayRequest(
            url = check.url,
            host = check.host,
            siteName = check.site.siteName,
            support = check.site.support,
            sourceLabel = sourceLabel,
            origin = origin,
        )
    }
}

data class OverlayOptions(val autoDismissSeconds: Int, val haptics: Boolean) {
    companion object {
        val DEFAULT = OverlayOptions(autoDismissSeconds = 10, haptics = true)
        fun from(settings: AppSettings) = OverlayOptions(settings.autoDismissSeconds, settings.haptics)
    }
}

sealed interface OverlayPhase {
    data object Ready : OverlayPhase
    data object Sending : OverlayPhase
    data object Sent : OverlayPhase
    data object Paused : OverlayPhase
    data class Failed(val reason: SendFailure, val detail: String?) : OverlayPhase
}
