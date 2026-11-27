package com.lbunny.roomorganizer.ui.theme

// =============================================================================
// Color.kt  —  the app's color palette (Google Drive style)
// =============================================================================
// Colors are written as 0xAARRGGBB hex numbers:
//   AA = opacity (FF = fully solid), RR = red, GG = green, BB = blue.
//
// These named colors are combined into full light/dark "color schemes" in
// Theme.kt. Screens should normally use MaterialTheme.colorScheme.xxx
// (e.g. colorScheme.primary) instead of these names directly, so light and
// dark mode both work automatically. The exception is the three "kind"
// colors at the bottom, which mean the same thing in both modes.
// =============================================================================

import androidx.compose.ui.graphics.Color

// ---- Light mode --------------------------------------------------------------
val DriveBlue = Color(0xFF0B57D0)             // main accent: buttons, highlights
val DriveBlueContainer = Color(0xFFD3E3FD)    // soft blue behind selected things
val OnDriveBlueContainer = Color(0xFF041E49)  // text drawn ON the soft blue
val LightBackground = Color(0xFFF8FAFD)       // the page background
val LightCard = Color(0xFFF0F4F9)             // folder/item card background
val LightCardHigh = Color(0xFFE9EEF6)         // slightly darker, e.g. count badges
val LightText = Color(0xFF1F1F1F)             // main text
val LightSubtleText = Color(0xFF444746)       // secondary text (labels, hints)

// ---- Dark mode ---------------------------------------------------------------
val DriveBlueDark = Color(0xFFA8C7FA)
val DriveBlueContainerDark = Color(0xFF0842A0)
val OnDriveBlueContainerDark = Color(0xFFD3E3FD)
val DarkBackground = Color(0xFF1B1B1B)
val DarkCard = Color(0xFF282A2C)
val DarkCardHigh = Color(0xFF333537)
val DarkText = Color(0xFFE3E3E3)
val DarkSubtleText = Color(0xFFC4C7C5)

// ---- "Kind" colors: tell boxes, folders, and items apart at a glance ---------
// Used for the icon tiles on each card. The tile background is this same color
// at 15% opacity, so it looks right on both light and dark backgrounds.
val BoxColor = Color(0xFFE8710A)     // orange: top-level containers on Home ("boxes")
val FolderColor = Color(0xFF5F6368)  // grey:   folders nested inside other folders
val ItemColor = Color(0xFF188038)    // green:  items
