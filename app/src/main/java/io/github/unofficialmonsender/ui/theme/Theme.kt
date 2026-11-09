package io.github.unofficialmonsender.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme()

object StatusColors {
    val success = Color(0xFF7FC99A)
    val warning = Color(0xFFE8C06A)
}

@Composable
fun MonsenderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
