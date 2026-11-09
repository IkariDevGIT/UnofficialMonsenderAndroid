package io.github.unofficialmonsender.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.unofficialmonsender.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** One Kitty per screen: her spot in the layout and the full-screen stage share this state. */
val LocalKitty = compositionLocalOf<Kitty?> { null }

@Composable
fun rememberKitty(): Kitty {
    val scope = rememberCoroutineScope()
    return remember { Kitty(scope) }
}

/**
 * The launcher icon's cat, peeking over whatever sits right below it with her paws on its edge.
 * She blinks, twitches her ears, scratches and licks a paw on her own, and meows when tapped.
 * Place her directly above that element; the paws hang [PAW_DROP] units past the bottom.
 */
@Composable
fun PeekingMascot(modifier: Modifier = Modifier, unit: Dp = 1.6.dp) {
    val kitty = LocalKitty.current ?: return
    val label = stringResource(R.string.mascot_label)

    LaunchedEffect(kitty) { withContext(FullSpeed) { kitty.blinkLoop() } }
    LaunchedEffect(kitty) { withContext(FullSpeed) { kitty.idleLoop() } }

    Canvas(
        modifier
            .offset(y = unit * PAW_DROP)
            .size(width = unit * WIDTH, height = unit * HEIGHT)
            .onGloballyPositioned {
                kitty.homeAt = it.positionInRoot()
                kitty.homeSize = it.size.toSize()
            }
            .semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                kitty.tap()
            },
    ) {
        val u = size.width / WIDTH
        if (kitty.away) drawHearts(kitty, u) else drawCat(kitty, u, stage = 0f)
    }
}

/**
 * Full-screen layer for the times Kitty leaves her spot, and for the spit she leaves behind.
 * Put it last in the root so it draws over everything; it never takes touches.
 */
@Composable
fun KittyStage(kitty: Kitty, modifier: Modifier = Modifier) {
    val meow = rememberMeow()
    LaunchedEffect(meow) { kitty.playMeow = meow::play }
    LaunchedEffect(kitty) { kitty.clock() }

    Canvas(modifier.fillMaxSize()) {
        kitty.screen = size
        if (kitty.onStage) {
            drawRect(Color.Black.copy(alpha = 0.45f * kitty.dim.value))
            val (origin, u) = kitty.placement()
            translate(origin.x, origin.y) { drawCat(kitty, u, stage = 1f) }
        }
        drawTrail(kitty)
    }
}

/** Kitty is the app's character, so she keeps moving even when system animations are scaled down or off. */
private object FullSpeed : MotionDurationScale {
    override val scaleFactor = 1f
}

private const val WIDTH = 50f
private const val HEIGHT = 43f
private const val PAW_DROP = 4f
private const val CX = 25f
private const val CY = 28f
private const val LEDGE = 39f
private const val OFF_STAGE = 400f

private val Fur = Color(0xFFB39DDB)
private val FurShade = Color(0xFF9D86CC)
private val PawFur = Color(0xFFCDBDEC)
private val Bean = Color(0xFFF09CAB)
private val Muzzle = Color(0xFFC4B2E6)
private val Chest = Color(0xFFB6A2DE)
private val EarInside = Color(0xFFD9A3CF)
private val Blush = Color(0xDDEF8F99)
private val Nose = Color(0xFFEF8F99)
private val Ink = Color(0xFF241D2E)
private val MouthInside = Color(0xFF5A2A3A)
private val Tongue = Color(0xFFE87486)
private val TongueLine = Color(0xFFC9586C)
private val HeartPink = Color(0xFFEF8F99)
private val Spit = Color(0xFFE6F0FF)

private val RestL = Offset(CX - 18f, LEDGE - 0.6f)
private val RestR = Offset(CX + 18f, LEDGE - 0.6f)
private val ShoulderL = Offset(CX - 10f, CY + 10f)
private val ShoulderR = Offset(CX + 10f, CY + 10f)
private val ScratchSpot = Offset(CX + 21f, CY - 9f)
private val LickSpot = Offset(CX - 8f, CY + 11f)
private val GlassL = Offset(CX - 17f, CY + 10f)
private val GlassR = Offset(CX + 17f, CY + 10f)

/** Where each screen lick starts, as (head shift, head lift); every lick then drags 12 units up. */
private val ScreenLicks = listOf(Offset(-6f, 0f), Offset(1f, 1.5f), Offset(7f, 0.5f))
private const val LICK_RISE = 12f

private const val HEART_LIFE_NANOS = 1_700_000_000L
private const val MAX_HEARTS = 9
private const val SPIT_HOLD_NANOS = 1_400_000_000L
private const val SPIT_FADE_NANOS = 1_000_000_000L
private const val SHOW_TAPS = 10
private const val SHOW_WINDOW_MS = 5_000L
private const val SHOW_COOLDOWN_MS = 5_000L

internal class Heart(val x: Float, val phase: Float, val size: Float, val born: Long)

internal class SpitPoint(val at: Offset, val width: Float, val stroke: Int, val born: Long)

internal class Paw(rest: Offset) {
    val x = Animatable(rest.x)
    val y = Animatable(rest.y)
    val position get() = Offset(x.value, y.value)

    /** x leads and y follows, so the paw travels on an arc instead of a straight line. */
    suspend fun moveTo(target: Offset, millis: Int, rising: Boolean) = coroutineScope {
        launch { x.animateTo(target.x, tween(millis, easing = if (rising) LinearOutSlowInEasing else FastOutSlowInEasing)) }
        launch { y.animateTo(target.y, tween(millis, easing = if (rising) FastOutSlowInEasing else LinearOutSlowInEasing)) }
    }
}

class Kitty internal constructor(private val scope: CoroutineScope) {
    internal val blink = Animatable(0f)
    internal val happy = Animatable(0f)
    internal val mouth = Animatable(0f)
    internal val tongue = Animatable(0f)
    internal val lap = Animatable(0f)
    internal val tilt = Animatable(0f)
    internal val lift = Animatable(0f)
    internal val earL = Animatable(0f)
    internal val earR = Animatable(0f)
    internal val press = Animatable(0f)
    internal val slide = Animatable(-1.25f)
    internal val dim = Animatable(0f)
    internal val sink = Animatable(0f)
    internal val pawL = Paw(RestL)
    internal val pawR = Paw(RestR)
    internal var shift by mutableFloatStateOf(0f)
    internal var reach by mutableFloatStateOf(0f)
    internal val hearts = mutableStateListOf<Heart>()
    internal val spit = mutableStateListOf<SpitPoint>()
    internal var now by mutableLongStateOf(0L)
    internal var onStage by mutableStateOf(false)
    internal var away by mutableStateOf(false)

    internal var homeAt = Offset.Zero
    internal var homeSize = Size.Zero
    internal var screen = Size.Zero
    internal var playMeow: () -> Unit = {}

    private var action: Job? = null
    private var showing = false
    private val taps = ArrayDeque<Long>()
    private var cooldownUntil = 0L

    internal fun tap() {
        if (showing) return
        val nowMs = System.currentTimeMillis()
        taps.addLast(nowMs)
        while (taps.isNotEmpty() && nowMs - taps.first() > SHOW_WINDOW_MS) taps.removeFirst()
        if (taps.size >= SHOW_TAPS && nowMs >= cooldownUntil && homeSize.width > 0f && screen.width > 0f) {
            taps.clear()
            startShow()
        } else {
            playMeow()
            meow()
        }
    }

    /** Where the big Kitty is drawn, as canvas origin and unit size; [slide] moves her across in screen widths. */
    internal fun placement(): Pair<Offset, Float> {
        val u = screen.width * 0.80f / 44f
        val origin = Offset(
            screen.width / 2f - CX * u + slide.value * screen.width,
            screen.height * 0.70f - (CY + 6f) * u,
        )
        return origin to u
    }

    suspend fun blinkLoop() {
        while (true) {
            delay(Random.nextLong(1_800, 4_200))
            blinkOnce()
            if (Random.nextFloat() < 0.25f) {
                delay(160)
                blinkOnce()
            }
        }
    }

    private suspend fun blinkOnce() {
        blink.animateTo(1f, tween(120, easing = FastOutLinearInEasing))
        blink.animateTo(0f, tween(200, easing = LinearOutSlowInEasing))
    }

    suspend fun idleLoop() {
        while (true) {
            delay(Random.nextLong(1_500, 4_000))
            if (action?.isActive == true || showing) continue
            val job = scope.launch(FullSpeed) {
                when (Random.nextInt(10)) {
                    in 0..3 -> twitch()
                    in 4..6 -> scratch()
                    else -> lick()
                }
            }
            action = job
            job.join()
        }
    }

    internal suspend fun clock() {
        while (true) {
            snapshotFlow { hearts.size + spit.size }.first { it > 0 }
            while (hearts.isNotEmpty() || spit.isNotEmpty()) {
                withFrameNanos { frame ->
                    now = frame
                    hearts.removeAll { frame - it.born > HEART_LIFE_NANOS }
                    spit.removeAll { frame - it.born > SPIT_HOLD_NANOS + SPIT_FADE_NANOS }
                }
            }
        }
    }

    private fun meow() {
        action?.cancel()
        spawnHearts()
        action = scope.launch(FullSpeed) {
            coroutineScope {
                launch { mouth.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
                launch { lift.animateTo(1.6f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
                launch { happy.animateTo(0f, tween(120)) }
                launch { tongue.animateTo(0f, tween(120)) }
                launch { tilt.animateTo(0f, tween(160)) }
                launch { earL.animateTo(-6f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
                launch { earR.animateTo(6f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
                launch { pawL.moveTo(RestL, 320, rising = false) }
                launch { pawR.moveTo(RestR, 320, rising = false) }
            }
            delay(260)
            coroutineScope {
                launch { mouth.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                launch { lift.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                launch { happy.animateTo(1f, tween(280)) }
                launch { earL.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
                launch { earR.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
            }
            coroutineScope {
                launch {
                    for (angle in floatArrayOf(-6f, 6f, -5f, 4.5f, -3f, 1.5f)) {
                        tilt.animateTo(angle, tween(180, easing = FastOutSlowInEasing))
                    }
                    tilt.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                }
                launch {
                    repeat(3) {
                        pawL.y.animateTo(RestL.y - 2f, tween(180, easing = FastOutSlowInEasing))
                        pawL.y.animateTo(RestL.y, tween(180, easing = FastOutSlowInEasing))
                    }
                }
                launch {
                    delay(180)
                    repeat(3) {
                        pawR.y.animateTo(RestR.y - 2f, tween(180, easing = FastOutSlowInEasing))
                        pawR.y.animateTo(RestR.y, tween(180, easing = FastOutSlowInEasing))
                    }
                }
            }
            delay(400)
            happy.animateTo(0f, tween(450))
        }
    }

    private fun spawnHearts() {
        val start = System.nanoTime()
        repeat(3) { i ->
            if (hearts.size >= MAX_HEARTS) hearts.removeAt(0)
            hearts += Heart(
                x = CX + Random.nextFloat() * 14f - 7f,
                phase = Random.nextFloat() * 2f * PI.toFloat(),
                size = 0.8f + Random.nextFloat() * 0.35f,
                born = start + i * 200_000_000L,
            )
        }
    }

    private suspend fun twitch() {
        val left = Random.nextBoolean()
        val ear = if (left) earL else earR
        val flick = if (left) -16f else 16f
        repeat(if (Random.nextFloat() < 0.4f) 2 else 1) {
            ear.animateTo(flick, tween(130, easing = FastOutSlowInEasing))
            ear.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 260f))
            delay(160)
        }
    }

    private suspend fun scratch() {
        coroutineScope {
            launch { tilt.animateTo(7f, tween(700, easing = FastOutSlowInEasing)) }
            launch { happy.animateTo(1f, tween(400)) }
            launch { earR.animateTo(10f, tween(500)) }
            pawR.moveTo(ScratchSpot, 700, rising = true)
        }
        repeat(6) {
            coroutineScope {
                launch { pawR.y.animateTo(ScratchSpot.y + 1.8f, tween(150, easing = FastOutSlowInEasing)) }
                launch { earR.animateTo(17f, tween(150, easing = FastOutSlowInEasing)) }
            }
            coroutineScope {
                launch { pawR.y.animateTo(ScratchSpot.y - 1.2f, tween(150, easing = FastOutSlowInEasing)) }
                launch { earR.animateTo(9f, tween(150, easing = FastOutSlowInEasing)) }
            }
        }
        coroutineScope {
            launch { tilt.animateTo(0f, tween(750, easing = FastOutSlowInEasing)) }
            launch { earR.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 150f)) }
            launch {
                delay(300)
                happy.animateTo(0f, tween(400))
            }
            pawR.moveTo(RestR, 750, rising = false)
        }
    }

    private suspend fun lick() {
        coroutineScope {
            launch { tilt.animateTo(-5f, tween(700, easing = FastOutSlowInEasing)) }
            launch { happy.animateTo(1f, tween(400)) }
            pawL.moveTo(LickSpot, 700, rising = true)
        }
        repeat(3) {
            coroutineScope {
                launch { tongue.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
                launch { pawL.y.animateTo(LickSpot.y - 1.2f, tween(280, easing = FastOutSlowInEasing)) }
                launch { lift.animateTo(-0.6f, tween(280, easing = FastOutSlowInEasing)) }
            }
            coroutineScope {
                launch { tongue.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
                launch { pawL.y.animateTo(LickSpot.y, tween(280, easing = FastOutSlowInEasing)) }
                launch { lift.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
            }
            delay(120)
        }
        delay(300)
        coroutineScope {
            launch { tilt.animateTo(0f, tween(750, easing = FastOutSlowInEasing)) }
            launch {
                delay(300)
                happy.animateTo(0f, tween(400))
            }
            pawL.moveTo(RestL, 750, rising = false)
        }
    }

    /**
     * Ten quick taps: she ducks behind the panel, walks in big from the left, licks the glass three
     * times, walks off to the right and pops back up in her spot.
     */
    private fun startShow() {
        action?.cancel()
        showing = true
        action = scope.launch(FullSpeed) {
            try {
                coroutineScope {
                    launch { happy.animateTo(0f, tween(200)) }
                    launch { mouth.animateTo(0f, tween(200)) }
                    launch { tongue.animateTo(0f, tween(200)) }
                    launch { tilt.animateTo(0f, tween(250)) }
                    launch { earL.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
                    launch { earR.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
                    launch { pawL.moveTo(RestL, 250, rising = false) }
                    launch { pawR.moveTo(RestR, 250, rising = false) }
                    launch { lift.animateTo(1.5f, tween(180, easing = FastOutSlowInEasing)) }
                }
                coroutineScope {
                    launch { lift.animateTo(0f, tween(200)) }
                    sink.animateTo(48f, tween(380, easing = FastOutLinearInEasing))
                }
                away = true

                slide.snapTo(-1.25f)
                onStage = true
                coroutineScope {
                    launch { slide.animateTo(0f, tween(850, easing = FastOutSlowInEasing)) }
                    launch { dim.animateTo(1f, tween(600)) }
                    launch {
                        tilt.animateTo(7f, tween(350, easing = FastOutSlowInEasing))
                        tilt.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 120f))
                    }
                }

                playMeow()
                coroutineScope {
                    launch { mouth.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
                    launch { earL.animateTo(-7f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
                    launch { earR.animateTo(7f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
                }
                delay(220)
                coroutineScope {
                    launch { mouth.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                    launch { earL.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
                    launch { earR.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
                }
                coroutineScope {
                    launch { press.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
                    launch { pawL.moveTo(GlassL, 420, rising = true) }
                    launch { pawR.moveTo(GlassR, 420, rising = true) }
                }
                delay(200)
                glide(ScreenLicks.first(), 450, FastOutSlowInEasing)
                ScreenLicks.forEachIndexed { index, start ->
                    lap.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                    lickGlass(index, start)
                    lap.animateTo(0.25f, tween(180, easing = FastOutSlowInEasing))
                    if (index < ScreenLicks.lastIndex) glide(ScreenLicks[index + 1], 420, FastOutSlowInEasing)
                }
                coroutineScope {
                    launch { lap.animateTo(0f, tween(220)) }
                    launch { happy.animateTo(1f, tween(300)) }
                    glide(Offset.Zero, 500, FastOutSlowInEasing)
                }
                delay(450)
                coroutineScope {
                    launch { press.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
                    launch { pawL.moveTo(RestL, 420, rising = false) }
                    launch { pawR.moveTo(RestR, 420, rising = false) }
                }

                coroutineScope {
                    launch { tilt.animateTo(8f, tween(320, easing = FastOutSlowInEasing)) }
                    launch {
                        delay(120)
                        slide.animateTo(1.3f, tween(700, easing = FastOutLinearInEasing))
                    }
                    launch {
                        delay(250)
                        dim.animateTo(0f, tween(550))
                    }
                }
                onStage = false
                tilt.snapTo(0f)

                away = false
                delay(250)
                coroutineScope {
                    launch { sink.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 160f)) }
                    launch {
                        delay(180)
                        launch { earL.animateTo(-8f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
                        earR.animateTo(8f, spring(dampingRatio = 0.4f, stiffness = 400f))
                        launch { earL.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 250f)) }
                        earR.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 250f))
                    }
                    launch {
                        delay(600)
                        happy.animateTo(0f, tween(400))
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    slide.snapTo(-1.25f)
                    dim.snapTo(0f)
                    sink.snapTo(0f)
                    press.snapTo(0f)
                    lap.snapTo(0f)
                    pawL.x.snapTo(RestL.x)
                    pawL.y.snapTo(RestL.y)
                    pawR.x.snapTo(RestR.x)
                    pawR.y.snapTo(RestR.y)
                }
                shift = 0f
                reach = 0f
                onStage = false
                away = false
                showing = false
                cooldownUntil = System.currentTimeMillis() + SHOW_COOLDOWN_MS
            }
        }
    }

    /** Moves the head to [to] (shift, lift) with the tongue off the glass. */
    private suspend fun glide(to: Offset, millis: Int, easing: Easing) {
        val from = Offset(shift, reach)
        Animatable(0f).animateTo(1f, tween(millis, easing = easing)) {
            shift = from.x + (to.x - from.x) * value
            reach = from.y + (to.y - from.y) * value
        }
    }

    /** One upward drag of the tongue across the glass, leaving a tapered wet streak. */
    private suspend fun lickGlass(index: Int, start: Offset) {
        Animatable(0f).animateTo(1f, tween(800, easing = FastOutSlowInEasing)) {
            val e = value
            shift = start.x + sin(PI.toFloat() * e) * 1.4f
            reach = start.y + LICK_RISE * e
            val (origin, u) = placement()
            val tip = origin + headPoint(tongueTip()) * u
            val taper = 0.35f + 0.65f * sin(PI.toFloat() * e).pow(0.7f)
            val last = spit.lastOrNull()
            if (last == null || last.stroke != index || (last.at - tip).getDistance() > 1.5f) {
                spit += SpitPoint(tip, 5f * u * taper, index, System.nanoTime())
            }
        }
    }

    /** A point in head space after the head's own tilt, shift and lift. */
    internal fun headPoint(p: Offset): Offset {
        val angle = Math.toRadians(headTilt().toDouble())
        val pivot = Offset(CX, LEDGE + 6f)
        val d = p - pivot
        val r = Offset(
            (d.x * cos(angle) - d.y * sin(angle)).toFloat(),
            (d.x * sin(angle) + d.y * cos(angle)).toFloat(),
        )
        return pivot + r + Offset(shift, -(lift.value + reach))
    }

    internal fun headTilt() = tilt.value + shift * 0.9f

    internal fun mouthOpen() = maxOf(mouth.value, tongue.value * 0.5f, lap.value * 0.7f)

    internal fun tongueTip(): Offset {
        val m = mouthOpen()
        val rh = 0.8f + 2.8f * m
        val t = maxOf(tongue.value, mouth.value * 0.7f)
        val ty = CY + 5.6f + rh * 0.9f + 0.9f * tongue.value + 2.5f * lap.value
        val th = 1f + 1.2f * t + 3f * lap.value
        return Offset(CX, ty + th)
    }
}

private class Meow(context: Context) {
    @Volatile private var loaded = false
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .apply { setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded = true } }
    private val sound = pool.load(context, R.raw.meow, 1)

    fun play() {
        if (loaded) pool.play(sound, 1f, 1f, 1, 0, 0.94f + Random.nextFloat() * 0.14f)
    }

    fun release() = pool.release()
}

@Composable
private fun rememberMeow(): Meow {
    val context = LocalContext.current
    val meow = remember(context) { Meow(context.applicationContext) }
    DisposableEffect(meow) { onDispose { meow.release() } }
    return meow
}

private fun DrawScope.drawCat(s: Kitty, u: Float, stage: Float) {
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)
    fun oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
        drawOval(color, topLeft = o(cx - rx, cy - ry), size = sz(rx * 2, ry * 2))

    val far = 100_000f
    clipRect(left = -far, top = -far, right = far, bottom = (LEDGE + stage * OFF_STAGE) * u) {
        val sink = if (stage > 0f) 0f else s.sink.value
        withTransform({
            translate(s.shift * u, (sink - s.lift.value - s.reach) * u)
            rotate(s.headTilt(), pivot = o(CX, LEDGE + 6f))
        }) {
            if (stage > 0f) {
                val body = Path().apply {
                    moveTo((CX - 20f) * u, (CY + 24f) * u)
                    arcTo(androidx.compose.ui.geometry.Rect(o(CX - 20f, CY + 15f), o(CX + 20f, CY + 33f)), 180f, 180f, false)
                    lineTo((CX + 25f) * u, (CY + 110f) * u)
                    lineTo((CX - 25f) * u, (CY + 110f) * u)
                    close()
                }
                drawPath(body, FurShade)
                oval(Chest, CX, CY + 44f, 10f, 16f)
            }
            drawEar(u, left = true, angle = s.earL.value)
            drawEar(u, left = false, angle = s.earR.value)
            drawRoundRect(Fur, topLeft = o(CX - 22f, CY - 20f), size = sz(44f, 52f), cornerRadius = CornerRadius(15f * u))
            oval(Muzzle, CX, CY + 6.2f, 7.5f, 4.6f)
            drawFace(s, u)
        }
    }

    val sink = if (stage > 0f) 0f else s.sink.value
    val pawEdge = if (stage > 0f) LEDGE + OFF_STAGE else LEDGE + 4.5f - min(sink, 4.5f)
    clipRect(left = -far, top = -far, right = far, bottom = pawEdge * u) {
        val press = s.press.value
        val down = Offset(0f, sink)
        drawPaw(u, s.pawL.position + down, RestL + down, ShoulderL + down, press)
        drawPaw(u, s.pawR.position + down, RestR + down, ShoulderR + down, press)
    }

    if (stage == 0f) drawHearts(s, u)
}

private fun DrawScope.drawEar(u: Float, left: Boolean, angle: Float) {
    val tri = if (left) {
        listOf(Offset(CX - 21f, CY - 5f), Offset(CX - 17f, CY - 27f), Offset(CX - 2f, CY - 14f))
    } else {
        listOf(Offset(CX + 2f, CY - 14f), Offset(CX + 17f, CY - 27f), Offset(CX + 21f, CY - 5f))
    }
    val base = Offset(if (left) CX - 11.5f else CX + 11.5f, CY - 10f)
    val inner = listOf(
        lerp(tri[0], base, 0.5f),
        lerp(tri[1], base, 0.22f),
        lerp(tri[2], base, 0.5f),
    )
    rotate(angle, pivot = base * u) {
        drawPath(triangle(tri, u), Fur)
        drawPath(triangle(inner, u), EarInside)
    }
}

private fun DrawScope.drawFace(s: Kitty, u: Float) {
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val open = (1f - s.blink.value) * (1f - s.happy.value)
    val happy = s.happy.value
    val blinkLine = s.blink.value * (1f - happy)
    val lid = Stroke(width = 1.5f * u, cap = StrokeCap.Round)

    for (side in floatArrayOf(-1f, 1f)) {
        val ex = CX + side * 10f
        val ey = CY - 1f
        if (open > 0.04f) {
            drawOval(Ink, topLeft = o(ex - 3.1f, ey - 3.7f * open), size = Size(6.2f * u, 7.4f * open * u))
            if (open > 0.45f) {
                drawOval(Color.White, topLeft = o(ex + 0.6f - 1.1f, ey - 1.5f * open - 1.1f * open), size = Size(2.2f * u, 2.2f * open * u))
            }
        }
        if (happy > 0.04f) {
            drawArc(Ink.copy(alpha = min(1f, happy * 1.3f)), 200f, 140f, false, o(ex - 3.4f, ey + 1.6f - 3f), Size(6.8f * u, 6f * u), style = lid)
        }
        if (blinkLine > 0.04f) {
            drawArc(Ink.copy(alpha = min(1f, blinkLine * 1.3f)), 20f, 140f, false, o(ex - 3.2f, ey - 0.4f - 1.2f), Size(6.4f * u, 2.4f * u), style = lid)
        }
        drawOval(Blush, topLeft = o(CX + side * 14f - 3.2f, CY + 4.6f - 1.9f), size = Size(6.4f * u, 3.8f * u))
    }

    drawPath(triangle(listOf(Offset(CX - 1.3f, CY + 2.8f), Offset(CX + 1.3f, CY + 2.8f), Offset(CX, CY + 4f)), u), Nose)

    val m = s.mouthOpen()
    val smile = (1f - m / 0.18f).coerceIn(0f, 1f)
    if (smile > 0f) {
        val stroke = Stroke(width = 1.3f * u, cap = StrokeCap.Round)
        for (side in floatArrayOf(-1f, 1f)) {
            val mx = CX + side * 1.8f
            drawArc(Ink.copy(alpha = smile), 0f, 180f, false, o(mx - 1.8f, CY + 4.6f - 1.6f), Size(3.6f * u, 3.2f * u), style = stroke)
        }
    }
    if (m > 0.04f) {
        val alpha = (m / 0.18f).coerceIn(0f, 1f)
        val rw = 2.2f + 0.8f * m
        val rh = 0.8f + 2.8f * m
        val my = CY + 5.6f + rh * 0.4f
        drawOval(MouthInside.copy(alpha = alpha), topLeft = o(CX - rw, my - rh), size = Size(rw * 2 * u, rh * 2 * u))
        val lap = s.lap.value
        val tip = s.tongueTip()
        val tw = 1.8f + 1.4f * lap
        val th = 1f + 1.2f * maxOf(s.tongue.value, s.mouth.value * 0.7f) + 3f * lap
        val ty = tip.y - th
        drawOval(Tongue.copy(alpha = alpha), topLeft = o(CX - tw, ty - th), size = Size(tw * 2 * u, th * 2 * u))
        if (lap > 0.3f) {
            drawLine(TongueLine.copy(alpha = alpha * lap), o(CX, ty - th * 0.6f), o(CX, ty + th * 0.5f), strokeWidth = 0.5f * u, cap = StrokeCap.Round)
        }
    }
}

/**
 * A paw at rest lies flat on the ledge; once lifted it turns toward where its hidden shoulder
 * points. Pressed against the screen ([press]) it shows its pink beans instead.
 */
private fun DrawScope.drawPaw(u: Float, at: Offset, rest: Offset, shoulder: Offset, press: Float) {
    val raised = min(1f, hypot(at.x - rest.x, at.y - rest.y) / 12f)
    val toward = Math.toDegrees(atan2((at.y - shoulder.y).toDouble(), (at.x - shoulder.x).toDouble())).toFloat()
    val turn = raised * (toward + 90f) * (1f - press)
    val pressed = ((press - 0.3f) / 0.4f).coerceIn(0f, 1f)
    if (pressed < 1f) {
        rotate(turn, pivot = at * u) {
            val a = 1f - pressed
            drawRoundRect(PawFur.copy(alpha = a), topLeft = (at - Offset(6.5f, 4.2f)) * u, size = Size(13f * u, 8.4f * u), cornerRadius = CornerRadius(4.2f * u))
            for (k in floatArrayOf(-2.6f, 0f, 2.6f)) {
                drawLine(FurShade.copy(alpha = a), (at + Offset(k, -4f)) * u, (at + Offset(k, -1.6f)) * u, strokeWidth = 0.8f * u, cap = StrokeCap.Round)
            }
        }
    }
    if (pressed > 0f) {
        val grow = 0.85f + 0.15f * pressed
        fun oval(color: Color, dx: Float, dy: Float, rx: Float, ry: Float) = drawOval(
            color.copy(alpha = pressed),
            topLeft = (at + Offset((dx - rx) * grow, (dy - ry) * grow)) * u,
            size = Size(rx * 2 * grow * u, ry * 2 * grow * u),
        )
        oval(PawFur, 0f, 0.5f, 7f, 7f)
        oval(Bean, 0f, 2.35f, 3.6f, 2.85f)
        for ((bx, by) in listOf(-4.6f to -2.4f, -1.6f to -4.4f, 1.6f to -4.4f, 4.6f to -2.4f)) {
            oval(Bean, bx, by, 1.5f, 1.7f)
        }
    }
}

private fun DrawScope.drawTrail(s: Kitty) {
    val points = s.spit
    if (points.size < 2) return
    val now = s.now
    fun alpha(p: SpitPoint): Float {
        val age = now - p.born
        return if (age <= SPIT_HOLD_NANOS) 1f else (1f - (age - SPIT_HOLD_NANOS).toFloat() / SPIT_FADE_NANOS).coerceIn(0f, 1f)
    }
    for (pass in 0..1) {
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.stroke != b.stroke) continue
            val fade = alpha(b)
            if (fade <= 0f) continue
            if (pass == 0) {
                drawLine(Spit.copy(alpha = 0.3f * fade), a.at, b.at, strokeWidth = b.width, cap = StrokeCap.Round)
            } else {
                val shine = Offset(-b.width * 0.25f, 0f)
                drawLine(Color.White.copy(alpha = 0.6f * fade), a.at + shine, b.at + shine, strokeWidth = b.width * 0.16f, cap = StrokeCap.Round)
            }
        }
    }
}

private val HeartShape: Path = Path().apply {
    for (i in 0..60) {
        val t = 2.0 * PI * i / 60
        val x = (16 * sin(t).pow(3) / 16).toFloat()
        val y = (-(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) / 16).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawHearts(s: Kitty, u: Float) {
    val now = s.now
    for (heart in s.hearts) {
        if (now < heart.born) continue
        val t = ((now - heart.born).toFloat() / HEART_LIFE_NANOS).coerceIn(0f, 1f)
        val rise = 1f - (1f - t).pow(2.2f)
        val x = heart.x + sin(t * 2f * PI.toFloat() * 0.8f + heart.phase) * 2.2f
        val y = CY - 24f - rise * 20f
        val pop = if (t < 0.18f) popIn(t / 0.18f) else 1f
        val alpha = when {
            t < 0.08f -> t / 0.08f
            t > 0.6f -> 1f - (t - 0.6f) / 0.4f
            else -> 1f
        }
        val scale = 2.3f * heart.size * pop * u
        withTransform({
            translate(x * u, y * u)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(HeartShape, HeartPink.copy(alpha = alpha.coerceIn(0f, 1f)))
        }
    }
}

private fun popIn(t: Float): Float {
    val c = 1.9f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

private fun triangle(points: List<Offset>, u: Float) = Path().apply {
    moveTo(points[0].x * u, points[0].y * u)
    lineTo(points[1].x * u, points[1].y * u)
    lineTo(points[2].x * u, points[2].y * u)
    close()
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
