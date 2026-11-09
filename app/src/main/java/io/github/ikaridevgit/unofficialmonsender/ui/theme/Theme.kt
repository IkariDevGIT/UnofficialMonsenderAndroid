package io.github.ikaridevgit.unofficialmonsender.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.ikaridevgit.unofficialmonsender.R

/**
 * The Mon palette: monbooru is crimson, monloader and MonSender are Material indigo. This app takes
 * Material deep purple, the step between them, on the same near-black terminal base.
 */
object MonColors {
    val bg = Color(0xFF0E0D10)
    val surface = Color(0xFF18161C)
    val input = Color(0xFF0B0A0D)
    val border = Color(0xFF2C2834)
    val fg = Color(0xFFE5E2E8)
    val fgDim = Color(0xFF958CA2)
    val accent = Color(0xFF7E57C2)
    val link = Color(0xFFB39DDB)
    val success = Color(0xFF7FC99A)
    val warning = Color(0xFFE8C06A)
    val error = Color(0xFFEF8F99)
    val danger = Color(0xFF9D2235)
}

object StatusColors {
    val success = MonColors.success
    val warning = MonColors.warning
}

private val Colors = darkColorScheme(
    primary = MonColors.link,
    onPrimary = MonColors.bg,
    primaryContainer = MonColors.accent,
    onPrimaryContainer = Color.White,
    inversePrimary = MonColors.accent,
    secondary = MonColors.link,
    onSecondary = MonColors.bg,
    secondaryContainer = Color(0xFF2A2236),
    onSecondaryContainer = MonColors.fg,
    tertiary = MonColors.warning,
    onTertiary = MonColors.bg,
    background = MonColors.bg,
    onBackground = MonColors.fg,
    surface = MonColors.bg,
    onSurface = MonColors.fg,
    surfaceVariant = MonColors.surface,
    onSurfaceVariant = MonColors.fgDim,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF25212B),
    surfaceDim = MonColors.bg,
    surfaceContainerLowest = MonColors.input,
    surfaceContainerLow = Color(0xFF131117),
    surfaceContainer = MonColors.surface,
    surfaceContainerHigh = Color(0xFF1E1B23),
    surfaceContainerHighest = Color(0xFF25212B),
    inverseSurface = MonColors.fg,
    inverseOnSurface = MonColors.bg,
    outline = MonColors.border,
    outlineVariant = MonColors.border,
    error = MonColors.error,
    onError = MonColors.bg,
    errorContainer = MonColors.danger,
    onErrorContainer = Color.White,
    scrim = Color.Black,
)

private val Mono = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(
            R.font.jetbrains_mono,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

val Wordmark = FontFamily(Font(R.font.dela_gothic_one))

private fun mono(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)

private val Type = Typography(
    displayLarge = mono(44, 52),
    displayMedium = mono(36, 44),
    displaySmall = mono(30, 38),
    headlineLarge = mono(26, 34, FontWeight.SemiBold),
    headlineMedium = mono(22, 30, FontWeight.SemiBold),
    headlineSmall = mono(20, 28, FontWeight.SemiBold),
    titleLarge = mono(18, 26, FontWeight.SemiBold),
    titleMedium = mono(15, 22, FontWeight.Medium),
    titleSmall = mono(14, 20, FontWeight.Medium),
    bodyLarge = mono(14, 21),
    bodyMedium = mono(13, 19),
    bodySmall = mono(12, 17),
    labelLarge = mono(13, 18, FontWeight.Medium),
    labelMedium = mono(12, 16, FontWeight.Medium),
    labelSmall = mono(11, 15, FontWeight.Medium),
)

private val Square = RoundedCornerShape(0)

private val MonShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square,
)

@Composable
fun MonsenderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = MonShapes, content = content)
}
