package com.simonsaysgps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Blue40,
    secondary = Green40,
    primaryContainer = Blue80,
    secondaryContainer = Green80
)

@Composable
fun SimonSaysGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
