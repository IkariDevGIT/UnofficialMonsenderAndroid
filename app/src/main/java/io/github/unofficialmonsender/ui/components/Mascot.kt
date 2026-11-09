package io.github.unofficialmonsender.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.unofficialmonsender.ui.theme.MonColors

private val Ink = Color(0xFF241D2E)
private val PawLine = Color(0xFF9575CD)

/**
 * The launcher icon's cat, peeking over whatever sits right below it with her paws on its edge.
 * Place it directly above that element; the paws hang [PAW_DROP] units past the bottom.
 */
@Composable
fun PeekingMascot(modifier: Modifier = Modifier, unit: Dp = 1.1.dp) {
    Canvas(
        modifier
            .offset(y = unit * PAW_DROP)
            .size(width = unit * WIDTH, height = unit * HEIGHT),
    ) {
        val u = size.width / WIDTH
        drawCat(u)
    }
}

private const val WIDTH = 50f
private const val HEIGHT = 43f
private const val PAW_DROP = 4f

private fun DrawScope.drawCat(u: Float) {
    val cx = 25f
    val cy = 28f
    val ledge = 39f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)

    clipRect(bottom = ledge * u) {
        val ears = Path().apply {
            moveTo(cx - 21f, cy - 6f); lineTo(cx - 17f, cy - 27f); lineTo(cx - 2f, cy - 14f); close()
            moveTo(cx + 2f, cy - 14f); lineTo(cx + 17f, cy - 27f); lineTo(cx + 21f, cy - 6f); close()
        }
        ears.transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) })
        drawPath(ears, MonColors.link)
        drawRoundRect(
            color = MonColors.link,
            topLeft = o(cx - 22f, cy - 20f),
            size = Size(44f * u, 40f * u),
            cornerRadius = CornerRadius(15f * u),
        )
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * 10f
            val ey = cy - 1f
            drawOval(Ink, topLeft = o(ex - 3f, ey - 3.6f), size = Size(6f * u, 7.2f * u))
            drawCircle(MonColors.fg, radius = 1f * u, center = o(ex + 0.2f, ey - 1.6f))
            drawOval(MonColors.error, topLeft = o(cx + side * 13f - 3.2f, cy + 2.6f), size = Size(6.4f * u, 3.6f * u))
        }
        val mouth = Stroke(width = 1.5f * u, cap = StrokeCap.Round)
        for (side in listOf(-1f, 1f)) {
            val mx = cx + side * 1.9f
            drawArc(
                color = Ink,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = o(mx - 1.9f, cy + 3.2f),
                size = Size(3.8f * u, 3.4f * u),
                style = mouth,
            )
        }
    }

    for (side in listOf(-1f, 1f)) {
        val px = cx + side * 18f
        val paw = Rect(o(px - 7f, ledge - 5f), o(px + 7f, ledge + 4f))
        drawRoundRect(MonColors.link, topLeft = paw.topLeft, size = paw.size, cornerRadius = CornerRadius(4.5f * u))
        for (k in listOf(-3f, 0f, 3f)) {
            drawLine(PawLine, o(px + k, ledge + 1f), o(px + k, ledge + 3f), strokeWidth = 1f * u, cap = StrokeCap.Round)
        }
    }
}
