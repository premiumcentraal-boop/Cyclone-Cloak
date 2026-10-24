package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** Static calm-glass colors from Cyclone's R6 profile kit. No blur or animated backdrop. */
internal object CloakGlass {
    val ink = Color(0xFFF5FFFFFF)
    val muted = Color(0xFFBFC5E3)
    val ready = Color(0xFF34C759)
    val waiting = Color(0xFFFFB340)
    val destructive = Color(0xFFFF6B5E)
    val backdrop = Brush.verticalGradient(listOf(Color(0xFF2B3BF2), Color(0xFF1B28C9),
        Color(0xFF111A86), Color(0xFF0C1150), Color(0xFF090C2B)))
}

private val CloakColors = darkColorScheme(
    primary = Color.White, onPrimary = Color(0xFF0B1040),
    primaryContainer = Color(0xFF303B8B), onPrimaryContainer = CloakGlass.ink,
    secondary = CloakGlass.ready, onSecondary = Color(0xFF092A16),
    secondaryContainer = Color(0xFF153D38), onSecondaryContainer = Color(0xFF8DE5A5),
    tertiary = CloakGlass.waiting, onTertiary = Color(0xFF332000),
    tertiaryContainer = Color(0xFF483A35), onTertiaryContainer = Color(0xFFFFD28B),
    error = CloakGlass.destructive, onError = Color(0xFF3D0D0A),
    errorContainer = Color(0xFF4E243A), onErrorContainer = Color(0xFFFFB4AA),
    background = Color(0xFF090C2B), onBackground = CloakGlass.ink,
    surface = Color(0xFF171E59), onSurface = CloakGlass.ink,
    surfaceVariant = Color(0xFF293271), onSurfaceVariant = CloakGlass.muted,
    outline = Color(0xFF7F88B4), outlineVariant = Color(0xFF414A83),
    surfaceContainer = Color(0xFF171E59), surfaceContainerHigh = Color(0xFF222B67),
)

private val CloakShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(24.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun CloakTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CloakColors, shapes = CloakShapes) {
        CompositionLocalProvider(LocalContentColor provides CloakGlass.ink) {
            Box(Modifier.fillMaxSize().background(CloakGlass.backdrop)) { content() }
        }
    }
}

enum class CloakStatusTone { READY, PENDING, ERROR }

@Composable
fun CloakStatusPill(label: String, tone: CloakStatusTone = CloakStatusTone.READY) {
    val color = when (tone) {
        CloakStatusTone.READY -> CloakGlass.ready
        CloakStatusTone.PENDING -> CloakGlass.waiting
        CloakStatusTone.ERROR -> CloakGlass.destructive
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, color = CloakGlass.ink, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CloakHeroCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, color = CloakGlass.ink)
        Text(body, fontSize = 16.sp, lineHeight = 23.sp, color = CloakGlass.muted)
    }
}

@Composable
fun CloakEmptyState(title: String, body: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CloakActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 50.dp),
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0x38FFFFFF)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) { CloakActionButtonContent(label, icon) }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 50.dp),
            enabled = enabled,
            shape = CircleShape,
        ) { CloakActionButtonContent(label, icon) }
    }
}

@Composable
private fun CloakActionButtonContent(label: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
