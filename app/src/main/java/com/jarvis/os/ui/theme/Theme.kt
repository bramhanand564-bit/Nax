package com.jarvis.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NaxBg = Color(0xFF0A0D16)
val NaxSurface = Color(0xFF121826)
val NaxCard = Color(0xFF171F32)
val NaxCardElevated = Color(0xFF1E2942)
val NaxBorder = Color(0xFF283552)

val NaxSandboxPurple = Color(0xFF7C5CFF)
val NaxLiveRed = Color(0xFFFF4D6D)
val NaxCyan = Color(0xFF38BDF8)
val NaxGreen = Color(0xFF22C55E)
val NaxAmber = Color(0xFFF59E0B)

val NaxTextPrimary = Color(0xFFF8FAFC)
val NaxTextSecondary = Color(0xFF94A3B8)

private val NaxDarkColorScheme = darkColorScheme(
    primary = NaxSandboxPurple,
    onPrimary = Color.White,
    secondary = NaxCyan,
    onSecondary = NaxBg,
    tertiary = NaxLiveRed,
    background = NaxBg,
    onBackground = NaxTextPrimary,
    surface = NaxSurface,
    onSurface = NaxTextPrimary,
    surfaceVariant = NaxCard,
    onSurfaceVariant = NaxTextSecondary,
    outline = NaxBorder
)

@Composable
fun NaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NaxDarkColorScheme,
        content = content
    )
}
