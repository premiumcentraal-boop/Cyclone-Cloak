package dev.cyclone.cloak

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF002023),
    secondary = Color(0xFF4A6368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE6EA),
    onSecondaryContainer = Color(0xFF062F33),
    tertiary = Color(0xFF5C5B7D),
    surface = Color(0xFFFDFCF9),
    surfaceVariant = Color(0xFFDBE4E7),
    background = Color(0xFFFDFCF9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD8E4),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Color(0xFFB2CBD0),
    onSecondary = Color(0xFF233C40),
    secondaryContainer = Color(0xFF3F4849),
    onSecondaryContainer = Color(0xFFDBE4E7),
    tertiary = Color(0xFFC5C3EA),
    surface = Color(0xFF101414),
    surfaceVariant = Color(0xFF3F4849),
    background = Color(0xFF101414),
)

@Composable
fun CloakTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
