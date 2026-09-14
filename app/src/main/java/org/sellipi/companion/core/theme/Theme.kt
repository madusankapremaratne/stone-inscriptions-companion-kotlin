package org.sellipi.companion.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Stone900,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = GoldPatina,
    onSecondary = Stone900,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    background = Stone900,
    onBackground = Stone100,
    surface = Stone800,
    onSurface = Stone100,
    surfaceVariant = Stone700,
    onSurfaceVariant = Stone300,
    outline = Stone500
)

private val SunlightHighContrastScheme = darkColorScheme(
    primary = SunlightAccent,
    onPrimary = SunlightBackground,
    primaryContainer = SunlightSurface,
    onPrimaryContainer = SunlightText,
    secondary = SunlightAccent,
    onSecondary = SunlightBackground,
    background = SunlightBackground,
    onBackground = SunlightText,
    surface = SunlightSurface,
    onSurface = SunlightText,
    surfaceVariant = SunlightSurface,
    onSurfaceVariant = SunlightText,
    outline = SunlightBorder
)

@Composable
fun SellipiTheme(
    isSunlightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isSunlightMode) SunlightHighContrastScheme else DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
