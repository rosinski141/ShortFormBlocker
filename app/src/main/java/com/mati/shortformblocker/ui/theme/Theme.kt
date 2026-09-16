package com.mati.shortformblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF12131A)
private val Mint = Color(0xFF4ADE80)
private val MintDark = Color(0xFF167C45)
private val Amber = Color(0xFFF59E0B)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = Amber,
    background = Ink,
    surface = Color(0xFF1B1D26),
    onSurface = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFFA7ACBE),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = MintDark,
    secondary = Amber,
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun BlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
