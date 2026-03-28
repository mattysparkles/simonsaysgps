package com.simonsaysgps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = RouteWhite,
    primaryContainer = Color(0xFFDDF1FF),
    onPrimaryContainer = Ink,
    secondary = NightSky,
    onSecondary = RouteWhite,
    secondaryContainer = Color(0xFFB9F6FF),
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = RouteWhite,
    background = Color(0xFFFFFBF1),
    onBackground = Ink,
    surface = RouteWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAF2FF),
    onSurfaceVariant = Asphalt,
    outline = Color(0xFF70A6FF),
    error = Coral,
    onError = RouteWhite
)

@Composable
fun SimonSaysGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
