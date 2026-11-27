package com.lbunny.roomorganizer.ui.theme

// =============================================================================
// Theme.kt  —  combines colors, fonts, and shapes into the app's look
// =============================================================================
// `RoomOrganizerTheme { ... }` wraps the whole app (see MainActivity). Any UI
// inside it can then ask for:
//   MaterialTheme.colorScheme.primary      (colors, from Color.kt)
//   MaterialTheme.typography.titleMedium   (text styles, from Type.kt)
//   MaterialTheme.shapes.medium            (corner shapes, from Shapes.kt)
// and automatically gets the light or dark version.
// =============================================================================

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Light mode colors. Each "role" below is a job a color does in Material
 * Design; every built-in component (buttons, menus, cards) picks its colors
 * from these roles.
 *   primary            = main accent color      onPrimary = text/icons on it
 *   primaryContainer   = softer accent fill     onPrimaryContainer = text on it
 *   background/surface = page and panel fill    onSurface = normal text
 *   surfaceContainer*  = card fills, light→dark
 */
private val LightColorScheme = lightColorScheme(
    primary = DriveBlue,
    onPrimary = Color.White,
    primaryContainer = DriveBlueContainer,
    onPrimaryContainer = OnDriveBlueContainer,
    secondaryContainer = DriveBlueContainer,
    onSecondaryContainer = OnDriveBlueContainer,
    background = LightBackground,
    onBackground = LightText,
    surface = LightBackground,
    onSurface = LightText,
    onSurfaceVariant = LightSubtleText,
    surfaceContainerLow = LightCard,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightCardHigh,
    surfaceVariant = LightCardHigh,
)

/** Dark mode colors (same roles as above). */
private val DarkColorScheme = darkColorScheme(
    primary = DriveBlueDark,
    onPrimary = OnDriveBlueContainer,
    primaryContainer = DriveBlueContainerDark,
    onPrimaryContainer = OnDriveBlueContainerDark,
    secondaryContainer = DriveBlueContainerDark,
    onSecondaryContainer = OnDriveBlueContainerDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkBackground,
    onSurface = DarkText,
    onSurfaceVariant = DarkSubtleText,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkCardHigh,
    surfaceVariant = DarkCardHigh,
)

/**
 * @param darkTheme    follow the phone's dark-mode setting by default
 * @param dynamicColor CHANGED from the template's `true` to `false`.
 *                     "Dynamic color" (Android 12+) recolors the app to match
 *                     your wallpaper, which would hide our Drive-style blue.
 *                     Set it back to true if you prefer wallpaper colors.
 */
@Composable
fun RoomOrganizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,   // from Type.kt (unchanged template file)
        shapes = MaterialShapes,   // NEW: from Shapes.kt
        content = content,
    )
}
