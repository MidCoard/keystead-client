package top.focess.keystead.client.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The rail stays dark in both themes as a brand anchor.
val RailBackground = Color(0xFF101820)
val RailBackgroundDark = Color(0xFF0A0F14)
val RailContent = Color(0xFFE6EAEF)
val RailContentMuted = Color(0xFF9AA7B4)

// State accents readable on the dark rail in both themes.
val VaultOpenAccent = Color(0xFF4CD69A)
val VaultLockedAccent = Color(0xFFF0BE3C)

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF3D5AFE),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE3FF),
        onPrimaryContainer = Color(0xFF00105A),
        secondary = Color(0xFF2CB67D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD2F2E4),
        onSecondaryContainer = Color(0xFF0A3B27),
        tertiary = Color(0xFFE6A700),
        onTertiary = Color(0xFF3A2B00),
        tertiaryContainer = Color(0xFFFBEEC4),
        onTertiaryContainer = Color(0xFF5C4300),
        background = Color(0xFFF3F5F2),
        onBackground = Color(0xFF17202A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF17202A),
        surfaceVariant = Color(0xFFE8ECEF),
        onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFD7DDE5),
        outlineVariant = Color(0xFFE5EAF0),
        error = Color(0xFFD93025),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFDE7E5),
        onErrorContainer = Color(0xFF5C1008),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8FA2FF),
        onPrimary = Color(0xFF0A1540),
        primaryContainer = Color(0xFF2A3DA8),
        onPrimaryContainer = Color(0xFFDEE3FF),
        secondary = Color(0xFF4CD69A),
        onSecondary = Color(0xFF0A3B27),
        secondaryContainer = Color(0xFF1B5C40),
        onSecondaryContainer = Color(0xFFD2F2E4),
        tertiary = Color(0xFFF0BE3C),
        onTertiary = Color(0xFF3A2B00),
        tertiaryContainer = Color(0xFF6B5200),
        onTertiaryContainer = Color(0xFFFBEEC4),
        background = Color(0xFF0E141B),
        onBackground = Color(0xFFE6EAEF),
        surface = Color(0xFF161E27),
        onSurface = Color(0xFFE6EAEF),
        surfaceVariant = Color(0xFF1E2833),
        onSurfaceVariant = Color(0xFF9AA7B4),
        outline = Color(0xFF2A3642),
        outlineVariant = Color(0xFF232E3A),
        error = Color(0xFFF28B82),
        onError = Color(0xFF5C1008),
        errorContainer = Color(0xFF7A1B12),
        onErrorContainer = Color(0xFFFDE7E5),
    )

private val KeysteadShapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
    )

@Composable
fun KeysteadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = KeysteadShapes,
        content = content,
    )
}
