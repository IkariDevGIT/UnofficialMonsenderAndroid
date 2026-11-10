package io.github.ikaridevgit.unofficialmonsender.overlay

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendFailure
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SendResult
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteSupport
import io.github.ikaridevgit.unofficialmonsender.data.settings.Pause
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.zIndex
import io.github.ikaridevgit.unofficialmonsender.ui.components.CompanionKitty
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.components.linkOriginLabel
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors
import io.github.ikaridevgit.unofficialmonsender.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val SCRIM_ALPHA = 0.78f
private const val DOWNWARD_RESISTANCE = 0.25f
private const val PAUSE_RESISTANCE = 0.6f

/**
 * Full-screen send prompt: the link sits in the upper half, swiping it up sends it to MonLoader,
 * swiping it down pauses copy detection for a while when [onPause] is given, and tapping the lower
 * half dismisses. Hosted by both the accessibility overlay and [OverlayActivity].
 */
@Composable
fun SendOverlay(
    request: OverlayRequest,
    options: OverlayOptions,
    send: suspend () -> SendResult,
    onClosed: () -> Unit,
    onPause: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = remember(view, options.haptics) { Haptics(view, options.haptics) }
    val touchExploration = remember {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }

    var phase by remember { mutableStateOf<OverlayPhase>(OverlayPhase.Ready) }
    var dragging by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var pauseArmed by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var rawDrag by remember { mutableFloatStateOf(0f) }
    var remaining by remember { mutableFloatStateOf(1f) }
    val reveal = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val pauseGlow = remember { Animatable(0f) }

    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(280, easing = LinearOutSlowInEasing)) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            reveal.animateTo(0f, tween(180))
            onClosed()
        }
    }

    BackHandler { close() }

    LaunchedEffect(phase, dragging) {
        val seconds = options.autoDismissSeconds
        if (seconds <= 0 || phase != OverlayPhase.Ready || dragging) return@LaunchedEffect
        val step = 50L
        while (remaining > 0f) {
            delay(step)
            remaining -= step / (seconds * 1000f)
        }
        close()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val badgeTop = maxHeight * 0.22f
        val pauseBadgeBottom = maxHeight * 0.2f
        val thresholdPx = min(heightPx * 0.2f, with(density) { 180.dp.toPx() })
        val pauseThresholdPx = min(heightPx * 0.14f, with(density) { 120.dp.toPx() })
        val flingPx = with(density) { 1200.dp.toPx() }
        val minFlingDistancePx = with(density) { 32.dp.toPx() }
        val canSwipe = phase == OverlayPhase.Ready || phase is OverlayPhase.Failed

        fun startPause() {
            val pause = onPause ?: return
            if (!canSwipeNow(phase, closing)) return
            haptics.confirm()
            phase = OverlayPhase.Paused
            pause()
            scope.launch { offsetY.animateTo(heightPx, tween(360, easing = FastOutLinearInEasing)) }
            scope.launch {
                pauseGlow.animateTo(1f, tween(160))
                pauseGlow.animateTo(0f, tween(1100))
            }
            scope.launch {
                delay(1400)
                close()
            }
        }

        fun startSend() {
            if (!canSwipeNow(phase, closing)) return
            haptics.confirm()
            phase = OverlayPhase.Sending
            scope.launch { offsetY.animateTo(-heightPx, tween(340, easing = FastOutLinearInEasing)) }
            scope.launch {
                glow.animateTo(1f, tween(160))
                glow.animateTo(0.3f, tween(700))
            }
            scope.launch {
                when (val result = send()) {
                    is SendResult.Queued -> {
                        phase = OverlayPhase.Sent
                        launch {
                            glow.animateTo(1f, tween(140))
                            glow.animateTo(0f, tween(900))
                        }
                        delay(1100)
                        close()
                    }
                    is SendResult.Failed -> {
                        phase = OverlayPhase.Failed(result.reason, result.detail)
                        haptics.reject()
                        launch { glow.animateTo(0f, tween(300)) }
                        offsetY.snapTo(-heightPx * 0.35f)
                        offsetY.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }

        fun onDrag(dy: Float) {
            rawDrag += dy
            val shown = when {
                rawDrag < 0f -> rawDrag
                onPause != null -> rawDrag * PAUSE_RESISTANCE
                else -> rawDrag * DOWNWARD_RESISTANCE
            }
            scope.launch { offsetY.snapTo(shown) }
            val nowArmed = shown < -thresholdPx
            if (nowArmed != armed) {
                armed = nowArmed
                if (nowArmed) haptics.threshold()
            }
            val nowPauseArmed = onPause != null && shown > pauseThresholdPx
            if (nowPauseArmed != pauseArmed) {
                pauseArmed = nowPauseArmed
                if (nowPauseArmed) haptics.threshold()
            }
        }

        fun onDragEnd(velocityY: Float) {
            dragging = false
            val fling = velocityY < -flingPx && offsetY.value < -minFlingDistancePx
            val pauseFling = onPause != null && velocityY > flingPx && offsetY.value > minFlingDistancePx
            val shouldSend = armed || fling
            val shouldPause = !shouldSend && (pauseArmed || pauseFling)
            armed = false
            pauseArmed = false
            rawDrag = 0f
            if (shouldSend) {
                startSend()
            } else if (shouldPause) {
                startPause()
            } else {
                scope.launch {
                    offsetY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }

        val sendLabel = stringResource(R.string.overlay_action_send)
        val dismissLabel = stringResource(R.string.overlay_action_dismiss)
        val pauseLabel = stringResource(R.string.overlay_action_pause, Pause.QUICK_MINUTES)
        val paneLabel = stringResource(R.string.overlay_pane_title)
        val primary = MaterialTheme.colorScheme.primary
        val dragProgress = if (canSwipe) (-offsetY.value / thresholdPx).coerceIn(0f, 1f) else 0f
        val pauseProgress = if (canSwipe && onPause != null) (offsetY.value / pauseThresholdPx).coerceIn(0f, 1f) else 0f

        Box(
            Modifier
                .fillMaxSize()
                .semantics { paneTitle = paneLabel }
                .pointerInput(heightPx, thresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = 0f
                        var isDrag = false
                        var releaseConsumed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                releaseConsumed = change.isConsumed
                                break
                            }
                            val dy = change.positionChange().y
                            moved += dy
                            if (!isDrag && abs(moved) > viewConfiguration.touchSlop && canSwipeNow(phase, closing)) {
                                isDrag = true
                                dragging = true
                                rawDrag = offsetY.value
                            }
                            if (isDrag) {
                                change.consume()
                                onDrag(dy)
                            }
                        }
                        if (isDrag) {
                            onDragEnd(tracker.calculateVelocity().y)
                        } else if (!releaseConsumed && down.position.y > size.height / 2f) {
                            close()
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = reveal.value }
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
            )

            EdgeGlow(
                color = primary,
                intensity = { (glow.value + dragProgress * 0.35f) * reveal.value },
                top = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (onPause != null) {
                EdgeGlow(
                    color = StatusColors.warning,
                    intensity = { (pauseGlow.value + pauseProgress * 0.35f) * reveal.value },
                    top = false,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val flight = (abs(offsetY.value) / heightPx).coerceIn(0f, 1f)
                        translationY = offsetY.value - (1f - reveal.value) * 32.dp.toPx()
                        alpha = reveal.value * (1f - flight * 1.6f).coerceIn(0f, 1f)
                        val scale = 1f - flight * 0.12f
                        scaleX = scale
                        scaleY = scale
                    },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SwipeArrow(
                    color = primary,
                    progress = { dragProgress },
                    bouncing = !dragging && phase == OverlayPhase.Ready,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .zIndex(1f),
                ) {
                    CompanionKitty(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp))
                }
                LinkCard(
                    request = request,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(sendLabel) { startSend(); true },
                            CustomAccessibilityAction(dismissLabel) { close(); true },
                            onPause?.let { CustomAccessibilityAction(pauseLabel) { startPause(); true } },
                        )
                    },
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(if (armed) R.string.overlay_release_to_send else R.string.overlay_swipe_to_send),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (armed) primary else Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.graphicsLayer {
                        alpha = if (phase == OverlayPhase.Ready || phase is OverlayPhase.Failed) 1f else 0f
                    },
                )
            }

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = badgeTop),
            ) {
                AnimatedVisibility(
                    visible = phase == OverlayPhase.Sending || phase == OverlayPhase.Sent,
                    enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.7f),
                    exit = fadeOut(tween(160)),
                ) {
                    SendBadge(sent = phase == OverlayPhase.Sent, spinner = primary)
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pauseBadgeBottom),
            ) {
                AnimatedVisibility(
                    visible = phase == OverlayPhase.Paused,
                    enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.7f),
                    exit = fadeOut(tween(160)),
                ) {
                    PauseBadge(stringResource(R.string.overlay_paused, Pause.QUICK_MINUTES))
                }
            }

            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            ) {
                AnimatedContent(
                    targetState = phase as? OverlayPhase.Failed,
                    transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it != null },
                    label = "failure",
                ) { failure ->
                    if (failure != null) {
                        FailureCard(
                            message = sendFailureMessage(failure.reason, failure.detail),
                            onRetry = ::startSend,
                            onClose = ::close,
                        )
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
                    .graphicsLayer { alpha = reveal.value * if (phase == OverlayPhase.Paused) 0f else 1f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (touchExploration && canSwipe) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonButton(dismissLabel, onClick = ::close)
                        if (onPause != null) MonButton(pauseLabel, onClick = ::startPause)
                        MonButton(sendLabel, onClick = ::startSend, accent = true)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                if (onPause != null) {
                    PauseHint(armed = pauseArmed, progress = { pauseProgress })
                    Spacer(Modifier.height(18.dp))
                }
                Text(
                    text = stringResource(R.string.overlay_tap_to_dismiss),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
                if (options.autoDismissSeconds > 0) {
                    Spacer(Modifier.height(12.dp))
                    CountdownBar(fraction = { if (phase == OverlayPhase.Ready) remaining else 0f })
                }
            }
        }
    }
}

private fun canSwipeNow(phase: OverlayPhase, closing: Boolean) =
    !closing && (phase == OverlayPhase.Ready || phase is OverlayPhase.Failed)

@Composable
private fun LinkCard(request: OverlayRequest, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        color = MonColors.surface,
        border = BorderStroke(1.dp, MonColors.border),
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = request.siteName ?: request.host
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SupportLabel(request.support)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = displayUrl(request.url, dim = colors.onSurfaceVariant),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            val origin = linkOriginLabel(request.origin, request.sourceLabel)
            if (origin != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = origin,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SupportLabel(support: SiteSupport) {
    val (text, color) = when (support) {
        SiteSupport.SUPPORTED -> R.string.overlay_support_supported to StatusColors.success
        SiteSupport.IMAGE -> R.string.overlay_support_image to StatusColors.success
        SiteSupport.UNKNOWN -> R.string.overlay_support_unknown to StatusColors.warning
        SiteSupport.CATALOG_UNAVAILABLE -> R.string.overlay_support_unchecked to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = stringResource(text), style = MaterialTheme.typography.labelMedium, color = color)
}

private fun displayUrl(url: String, dim: Color) = buildAnnotatedString {
    val prefix = Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE).find(url)?.value.orEmpty()
    withStyle(SpanStyle(color = dim)) { append(prefix) }
    append(url.removeRange(0, prefix.length))
}

@Composable
private fun SwipeArrow(color: Color, progress: () -> Float, bouncing: Boolean) {
    val transition = rememberInfiniteTransition(label = "arrow")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                0f at 0 using FastOutSlowInEasing
                1f at 380 using FastOutSlowInEasing
                0f at 800
                0f at 1500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "bounce",
    )
    Canvas(
        Modifier
            .size(width = 44.dp, height = 40.dp)
            .graphicsLayer {
                val p = progress()
                translationY = if (bouncing) -bounce * 10.dp.toPx() else -p * 6.dp.toPx()
                val scale = 1f + p * 0.15f
                scaleX = scale
                scaleY = scale
            },
    ) {
        val p = progress()
        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter)
        fun chevron(top: Float, alpha: Float) {
            val path = Path().apply {
                moveTo(size.width * 0.18f, top + size.height * 0.3f)
                lineTo(size.width * 0.5f, top)
                lineTo(size.width * 0.82f, top + size.height * 0.3f)
            }
            drawPath(path, color.copy(alpha = alpha), style = stroke)
        }
        chevron(top = size.height * 0.08f, alpha = 0.95f)
        chevron(top = size.height * 0.52f, alpha = 0.4f + p * 0.55f)
    }
}

/** Light spilling in from the top edge for sending, or from the bottom edge for pausing. */
@Composable
private fun EdgeGlow(color: Color, intensity: () -> Float, top: Boolean, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(360.dp),
    ) {
        val i = intensity().coerceIn(0f, 1f)
        if (i <= 0.001f) return@Canvas
        val edge = if (top) 0f else size.height
        drawRect(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = 0.6f * i),
                0.45f to color.copy(alpha = 0.22f * i),
                1f to Color.Transparent,
                center = Offset(size.width / 2f, edge),
                radius = min(size.width * 0.85f, size.height),
            ),
        )
        val line = 3.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = i), Color.Transparent)),
            topLeft = Offset(0f, if (top) 0f else size.height - line),
            size = Size(size.width, line),
        )
    }
}

/** The pause sign and "swipe down" line; both warm up and sink a little as the card is pulled down. */
@Composable
private fun PauseHint(armed: Boolean, progress: () -> Float) {
    val idle = Color.White.copy(alpha = 0.55f)
    val transition = rememberInfiniteTransition(label = "pauseHint")
    val nudge by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                0f at 0
                0f at 1200 using FastOutSlowInEasing
                1f at 1520 using FastOutSlowInEasing
                0f at 1900
                0f at 2400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "nudge",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            val p = progress()
            translationY = p * 10.dp.toPx() + (1f - p) * nudge * 4.dp.toPx()
            val scale = 1f + p * 0.08f
            scaleX = scale
            scaleY = scale
        },
    ) {
        Canvas(Modifier.size(14.dp)) {
            val color = lerp(idle, StatusColors.warning, progress())
            val bar = size.width * 0.3f
            drawRect(color, topLeft = Offset(size.width * 0.1f, 0f), size = Size(bar, size.height))
            drawRect(color, topLeft = Offset(size.width * 0.9f - bar, 0f), size = Size(bar, size.height))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(
                if (armed) R.string.overlay_release_to_pause else R.string.overlay_swipe_to_pause,
                Pause.QUICK_MINUTES,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (armed) StatusColors.warning else lerp(idle, StatusColors.warning, progress() * 0.6f),
        )
    }
}

/** Amber badge whose two bars grow in, with the same square ripple as the sent badge. */
@Composable
private fun PauseBadge(label: String) {
    val pop = remember { Animatable(0.8f) }
    val bars = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 480f)) }
        launch { ripple.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
        delay(90)
        bars.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = label
        },
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val p = ripple.value
                if (p < 1f) {
                    val side = BADGE_SIZE.dp.toPx() * (1f + 0.75f * p)
                    drawRect(
                        color = StatusColors.warning.copy(alpha = 0.7f * (1f - p)),
                        topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                        size = Size(side, side),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(BADGE_SIZE.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .background(StatusColors.warning),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(36.dp)) {
                    val t = bars.value
                    val bar = size.width * 0.3f
                    val height = size.height * t
                    val top = (size.height - height) / 2f
                    drawRect(MonColors.bg, topLeft = Offset(size.width * 0.08f, top), size = Size(bar, height))
                    drawRect(MonColors.bg, topLeft = Offset(size.width * 0.92f - bar, top), size = Size(bar, height))
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.graphicsLayer { alpha = bars.value.coerceIn(0f, 1f) },
        )
    }
}

/**
 * Sending shows a spinner in a square badge; once MonLoader accepts the link the badge turns green,
 * pops, draws a checkmark and sends out a ripple, so success reads at a glance without any text.
 */
@Composable
private fun SendBadge(sent: Boolean, spinner: Color) {
    val label = stringResource(if (sent) R.string.overlay_sent else R.string.overlay_sending)
    val fill by animateColorAsState(if (sent) StatusColors.success else MonColors.surface, tween(220), label = "badge")
    val pop = remember { Animatable(1f) }
    val check = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(sent) {
        if (!sent) return@LaunchedEffect
        launch {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 480f))
        }
        launch { ripple.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
        delay(90)
        check.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .size(150.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = ripple.value
            if (sent && p < 1f) {
                val side = BADGE_SIZE.dp.toPx() * (1f + 0.75f * p)
                drawRect(
                    color = StatusColors.success.copy(alpha = 0.7f * (1f - p)),
                    topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                    size = Size(side, side),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(BADGE_SIZE.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .background(fill)
                .border(1.dp, if (sent) StatusColors.success else MonColors.border),
            contentAlignment = Alignment.Center,
        ) {
            if (!sent) {
                CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp, color = spinner)
            }
            Canvas(Modifier.size(46.dp)) {
                val t = check.value
                if (t <= 0f) return@Canvas
                val mark = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.54f)
                    lineTo(size.width * 0.40f, size.height * 0.80f)
                    lineTo(size.width * 0.90f, size.height * 0.22f)
                }
                val measure = PathMeasure().apply { setPath(mark, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * t, drawn, true)
                drawPath(
                    path = drawn,
                    color = MonColors.bg,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
                )
            }
        }
    }
}

private const val BADGE_SIZE = 84

@Composable
private fun FailureCard(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 400.dp),
        color = MonColors.surface,
        border = BorderStroke(1.dp, MonColors.danger),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.overlay_failed_title) + "  " + stringResource(R.string.kaomoji_sad),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                MonButton(stringResource(R.string.action_close), onClick = onClose)
                MonButton(stringResource(R.string.action_try_again), onClick = onRetry, accent = true)
            }
        }
    }
}

@Composable
private fun CountdownBar(fraction: () -> Float) {
    val track = Color.White.copy(alpha = 0.12f)
    val fill = Color.White.copy(alpha = 0.45f)
    Canvas(
        Modifier
            .width(64.dp)
            .height(3.dp),
    ) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * fraction().coerceIn(0f, 1f), size.height))
    }
}

@Composable
fun sendFailureMessage(reason: SendFailure, detail: String?): String = when (reason) {
    SendFailure.NOT_CONFIGURED -> stringResource(R.string.send_failure_not_configured)
    SendFailure.UNREACHABLE -> stringResource(R.string.send_failure_unreachable)
    SendFailure.NOT_PAIRED -> stringResource(R.string.send_failure_not_paired)
    SendFailure.TOKEN_REJECTED -> stringResource(R.string.send_failure_token_rejected)
    SendFailure.MONBOORU_UNCONFIGURED -> stringResource(R.string.send_failure_monbooru_unconfigured)
    SendFailure.MONBOORU_PAUSED -> stringResource(R.string.send_failure_monbooru_paused)
    SendFailure.SETUP_REQUIRED -> stringResource(R.string.send_failure_setup_required)
    SendFailure.INVALID_URL -> stringResource(R.string.send_failure_invalid_url)
    SendFailure.REFUSED -> stringResource(R.string.send_failure_refused, detail.orEmpty())
}

/** Brief message for the activity host when there is nothing to send. */
@Composable
fun OverlayNotice(text: String, onDone: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(200))
        delay(1600)
        reveal.animateTo(0f, tween(180))
        onDone()
    }
    BackHandler(onBack = onDone)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = reveal.value }
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); onDone() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MonColors.surface,
            border = BorderStroke(1.dp, MonColors.border),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = StatusColors.warning)
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
