package dev.cyclone.cloak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation only: no names, colors or selection are written to profile storage. */
internal data class CloakProfileFace(val id: String, val label: String, val status: String,
    val current: Boolean, val ready: Boolean)

internal fun profileInitial(label: String): String {
    val words = label.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val word = if (words.size > 1 && words.first().equals("Profile", true)) words[1] else words.firstOrNull()
    return word?.let { String(Character.toChars(it.codePointAt(0))).uppercase(java.util.Locale.ROOT) } ?: "?"
}

@Composable
internal fun CloakProfileAvatar(id: String, label: String, current: Boolean = false,
    dimmed: Boolean = false, size: Int = 70) {
    val palette = listOf(0xFF3E7BFA, 0xFF7B61FF, 0xFF30B85A, 0xFFFF9F0A,
        0xFFFF6B5E, 0xFF32ADE6, 0xFFE056FD, 0xFF8E8E93)
    val color = Color(palette[Math.floorMod(id.hashCode(), palette.size)])
    Box(Modifier.size(size.dp).padding(4.dp)
        .then(if (current) Modifier.border(2.dp, Color.White, CircleShape).padding(4.dp) else Modifier)
        .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.28f), color)), CircleShape),
        contentAlignment = Alignment.Center) {
        Text(profileInitial(label), fontSize = (size * 0.38f).sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (dimmed) Box(Modifier.matchParentSize().background(Color(0x59000000), CircleShape))
    }
}

@Composable
internal fun CloakProfileGallery(profiles: List<CloakProfileFace>, selectedId: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        profiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { profile ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { selected = profile.id == selectedId }
                        .clickable(role = Role.Button, onClickLabel = "Review ${profile.label}") { onSelect(profile.id) }
                        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CloakProfileAvatar(profile.id, profile.label, profile.current, !profile.ready)
                        Text(profile.label, color = CloakGlass.ink, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(profile.status, color = if (profile.current) CloakGlass.ready else CloakGlass.muted,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
