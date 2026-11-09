package io.github.ikaridevgit.unofficialmonsender.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.detection.LinkOrigin

/** "Copied from Chrome", "Shared from Firefox", "From the quick settings tile"; null when there's nothing to say. */
@Composable
fun linkOriginLabel(origin: LinkOrigin, app: String?): String? = when (origin) {
    LinkOrigin.COPY -> app?.let { stringResource(R.string.overlay_copied_from, it) }
    LinkOrigin.SHARE -> if (app != null) stringResource(R.string.origin_shared_from, app) else stringResource(R.string.origin_shared)
    LinkOrigin.TILE -> stringResource(R.string.origin_tile)
}
