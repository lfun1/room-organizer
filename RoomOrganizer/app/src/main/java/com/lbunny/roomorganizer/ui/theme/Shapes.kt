package com.lbunny.roomorganizer.ui.theme

// =============================================================================
// Shapes.kt  —  "smoothed corner" shapes for icons, cards, and photos
// =============================================================================
//
// The spec asks for icons with SMOOTHED corners. A normal rounded corner is a
// quarter-circle glued onto a straight edge; you can see a slight "kink"
// where the straight line suddenly starts curving. A SMOOTH corner (sometimes
// called a "squircle" — the shape of iPhone app icons) eases into the curve
// gradually, so there's no visible kink.
//
//    normal rounded corner         smooth corner
//         ________                   ________
//        /                         _/
//       |   <- curve starts         |  <- curve starts earlier and
//       |      abruptly             |     bends gently
//
// HOW WE DRAW IT: each corner is a "cubic Bézier curve" (a standard smooth
// curve defined by 2 end points + 2 control points). We put BOTH control
// points exactly on the sharp corner. Math detail (optional): that makes the
// curve's bendiness start at zero where it meets each straight edge, which is
// precisely what removes the kink.
// =============================================================================

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A rectangle with smooth ("squircle") corners.
 *
 * @param cornerRadius roughly how round the corners look, like the radius of a
 *                     normal rounded corner.
 *
 * Implements Compose's [Shape] interface, so it works anywhere a shape is
 * accepted: Modifier.clip(...), Card(shape = ...), background(color, shape)...
 */
class SmoothCornerShape(private val cornerRadius: Dp) : Shape {

    /** Called by Compose with the real pixel size whenever it draws the shape. */
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // Convert dp (screen-independent units) to actual pixels.
        val radiusPx = with(density) { cornerRadius.toPx() }

        // How far along each edge the curve begins. A smooth corner needs a
        // longer run than a circle (~2.3x the radius) to look equally round.
        // Never more than half the side, or opposite corners would overlap.
        val s = minOf(radiusPx * 2.3f, size.minDimension / 2f)
        val w = size.width
        val h = size.height

        // Trace the outline clockwise, starting just right of the top-left corner.
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)                       // top edge
            cubicTo(w, 0f, w, 0f, w, s)             // top-right corner
            lineTo(w, h - s)                        // right edge
            cubicTo(w, h, w, h, w - s, h)           // bottom-right corner
            lineTo(s, h)                            // bottom edge
            cubicTo(0f, h, 0f, h, 0f, h - s)        // bottom-left corner
            lineTo(0f, s)                           // left edge
            cubicTo(0f, 0f, 0f, 0f, s, 0f)          // top-left corner
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * The shapes used throughout the app, in one place so they stay consistent.
 * Change a number here and every card/icon updates.
 */
object AppShapes {
    val card = SmoothCornerShape(16.dp)       // folder/item cards in the grid
    val photo = SmoothCornerShape(20.dp)      // large photos on the details screen
    val button = SmoothCornerShape(12.dp)     // the ＋ box and similar buttons
    val badge = SmoothCornerShape(8.dp)       // small count badges
    val row = SmoothCornerShape(12.dp)        // selectable rows (e.g. Move dialog)

    /** Icon tiles scale their corners with their size (22% of the width). */
    fun iconTile(size: Dp) = SmoothCornerShape(size * 0.22f)
}

/**
 * Shapes for Material's built-in components (dialogs, menus, text fields).
 * Material requires standard rounded corners here (it can't take custom
 * shapes), so these are regular, generously rounded corners.
 */
val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
