package com.friends.ships.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Navy,
    secondary = Cyan,
    onSecondary = Navy,
    background = Navy,
    onBackground = Cyan,
    surface = NavySoft,
    onSurface = Cyan,
    error = Coral
)

private val LightColors = lightColorScheme(
    primary = ColorTokens.teal,
    onPrimary = ColorTokens.navy,
    secondary = ColorTokens.deepTeal,
    background = ColorTokens.background,
    surface = ColorTokens.surface,
    onSurface = ColorTokens.text,
    onBackground = ColorTokens.text,
    error = ColorTokens.coral
)

private object ColorTokens {
    val teal = androidx.compose.ui.graphics.Color(0xFF087E78)
    val navy = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val deepTeal = androidx.compose.ui.graphics.Color(0xFF14545A)
    val background = androidx.compose.ui.graphics.Color(0xFFF1F7F7)
    val surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val text = androidx.compose.ui.graphics.Color(0xFF102A40)
    val coral = androidx.compose.ui.graphics.Color(0xFFB4233E)
}

@Composable
fun ShipsTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
