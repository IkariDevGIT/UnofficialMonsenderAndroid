package io.github.unofficialmonsender.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.unofficialmonsender.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

/**
 * The launcher icon's cat, peeking over whatever sits right below it with her paws on its edge.
 * She blinks, twitches her ears, scratches and licks a paw on her own, and meows when tapped.
 * Place her directly above that element; the paws hang [PAW_DROP] units past the bottom.
 */
@Composable
fun PeekingMascot(modifier: Modifier = Modifier, unit: Dp = 1.6.dp) {
    val scope = rememberCoroutineScope()
    val state = remember { MascotState(scope) }
    val meow = rememberMeow()
    val label = stringResource(R.string.mascot_label)

    LaunchedEffect(state) { withContext(FullSpeed) { state.blinkLoop() } }
    LaunchedEffect(state) { withContext(FullSpeed) { state.idleLoop() } }
    LaunchedEffect(state) { state.heartClock() }

    Canvas(
        modifier
            .offset(y = unit * PAW_DROP)
            .size(width = unit * WIDTH, height = unit * HEIGHT)
            .semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                meow.play()
                state.meow()
            },
    ) {
        drawCat(state, size.width / WIDTH)
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

private val Fur = Color(0xFFB39DDB)
private val FurShade = Color(0xFF9D86CC)
private val PawFur = Color(0xFFCDBDEC)
private val Muzzle = Color(0xFFC4B2E6)
private val EarInside = Color(0xFFD9A3CF)
private val Blush = Color(0xDDEF8F99)
private val Nose = Color(0xFFEF8F99)
private val Ink = Color(0xFF241D2E)
private val MouthInside = Color(0xFF5A2A3A)
private val Tongue = Color(0xFFE87486)
private val HeartPink = Color(0xFFEF8F99)

private val RestL = Offset(CX - 18f, LEDGE - 0.6f)
private val RestR = Offset(CX + 18f, LEDGE - 0.6f)
private val ShoulderL = Offset(CX - 10f, CY + 10f)
private val ShoulderR = Offset(CX + 10f, CY + 10f)
private val ScratchSpot = Offset(CX + 21f, CY - 9f)
private val LickSpot = Offset(CX - 8f, CY + 11f)

private const val HEART_LIFE_NANOS = 1_700_000_000L
private const val MAX_HEARTS = 9

private class Heart(val x: Float, val phase: Float, val size: Float, val born: Long)

private class Paw(rest: Offset) {
    val x = Animatable(rest.x)
    val y = Animatable(rest.y)
    val position get() = Offset(x.value, y.value)

    /** x leads and y follows, so the paw travels on an arc instead of a straight line. */
    suspend fun moveTo(target: Offset, millis: Int, rising: Boolean) = coroutineScope {
        launch { x.animateTo(target.x, tween(millis, easing = if (rising) LinearOutSlowInEasing else FastOutSlowInEasing)) }
        launch { y.animateTo(target.y, tween(millis, easing = if (rising) FastOutSlowInEasing else LinearOutSlowInEasing)) }
    }
}

private class MascotState(private val scope: CoroutineScope) {
    val blink = Animatable(0f)
    val happy = Animatable(0f)
    val mouth = Animatable(0f)
    val tongue = Animatable(0f)
    val tilt = Animatable(0f)
    val lift = Animatable(0f)
    val earL = Animatable(0f)
    val earR = Animatable(0f)
    val pawL = Paw(RestL)
    val pawR = Paw(RestR)
    val hearts = mutableStateListOf<Heart>()
    var now by mutableLongStateOf(0L)

    private var action: Job? = null

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
            if (action?.isActive == true) continue
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

    suspend fun heartClock() {
        while (true) {
            snapshotFlow { hearts.size }.first { it > 0 }
            while (hearts.isNotEmpty()) {
                withFrameNanos { frame ->
                    now = frame
                    hearts.removeAll { frame - it.born > HEART_LIFE_NANOS }
                }
            }
        }
    }

    fun meow() {
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
        scope.launch {
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
}

private class Meow(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    @Volatile private var loaded = false
    private val sound = pool.load(context, R.raw.meow, 1)

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (id == sound && status == 0) loaded = true }
    }

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

private fun DrawScope.drawCat(s: MascotState, u: Float) {
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)
    fun oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
        drawOval(color, topLeft = o(cx - rx, cy - ry), size = sz(rx * 2, ry * 2))

    clipRect(bottom = LEDGE * u) {
        withTransform({
            translate(0f, -s.lift.value * u)
            rotate(s.tilt.value, pivot = o(CX, LEDGE + 6f))
        }) {
            drawEar(u, left = true, angle = s.earL.value)
            drawEar(u, left = false, angle = s.earR.value)
            drawRoundRect(Fur, topLeft = o(CX - 22f, CY - 20f), size = sz(44f, 52f), cornerRadius = CornerRadius(15f * u))
            oval(Muzzle, CX, CY + 6.2f, 7.5f, 4.6f)
            drawFace(s, u)
        }
    }

    clipRect(bottom = (LEDGE + 4.5f) * u) {
        drawPaw(u, s.pawL.position, RestL, ShoulderL)
        drawPaw(u, s.pawR.position, RestR, ShoulderR)
    }

    drawHearts(s, u)
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

private fun DrawScope.drawFace(s: MascotState, u: Float) {
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

    val m = maxOf(s.mouth.value, s.tongue.value * 0.5f)
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
        val t = maxOf(s.tongue.value, s.mouth.value * 0.7f)
        val ty = CY + 5.6f + rh * 0.9f + 0.9f * s.tongue.value
        val th = 1f + 1.2f * t
        drawOval(Tongue.copy(alpha = alpha), topLeft = o(CX - 1.8f, ty - th), size = Size(3.6f * u, th * 2 * u))
    }
}

/** A paw at rest lies flat on the ledge; once lifted it turns toward where its hidden shoulder points. */
private fun DrawScope.drawPaw(u: Float, at: Offset, rest: Offset, shoulder: Offset) {
    val raised = min(1f, hypot(at.x - rest.x, at.y - rest.y) / 12f)
    val toward = Math.toDegrees(atan2((at.y - shoulder.y).toDouble(), (at.x - shoulder.x).toDouble())).toFloat()
    val turn = raised * (toward + 90f)
    rotate(turn, pivot = at * u) {
        drawRoundRect(PawFur, topLeft = (at - Offset(6.5f, 4.2f)) * u, size = Size(13f * u, 8.4f * u), cornerRadius = CornerRadius(4.2f * u))
        for (k in floatArrayOf(-2.6f, 0f, 2.6f)) {
            drawLine(FurShade, (at + Offset(k, -4f)) * u, (at + Offset(k, -1.6f)) * u, strokeWidth = 0.8f * u, cap = StrokeCap.Round)
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

private fun DrawScope.drawHearts(s: MascotState, u: Float) {
    val now = s.now
    for (heart in s.hearts) {
        val t = ((now - heart.born).toFloat() / HEART_LIFE_NANOS).coerceIn(0f, 1f)
        if (now < heart.born) continue
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
