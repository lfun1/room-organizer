package com.lbunny.roomorganizer.ui.components

// =============================================================================
// BreadcrumbBar.kt  —  the "Home › Room › Closet" strip under the top bar
// =============================================================================
// Like Google Drive's path bar:
//   - Tap a crumb to jump straight to that folder.
//   - DROP a dragged card onto a crumb to move it there. This is how you
//     drag something OUT of the current folder (e.g. back up to "Room" or
//     "Home"), since those folders aren't visible in the grid.
// The last crumb is the folder you're in now (bold, not tappable).
// =============================================================================

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.ui.theme.AppShapes

/**
 * @param path       folders from the outermost down to the current one
 * @param onNavigate open a folder (null = Home)
 * @param onDropNode a card was dropped on a crumb: (dragged id, target folder
 *                   id or null for Home, target name)
 */
@Composable
fun BreadcrumbBar(
    path: List<Node>,
    onNavigate: (folderId: Long?) -> Unit,
    onDropNode: (draggedId: Long, targetId: Long?, targetName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // horizontalScroll = if the path is too long for the screen, swipe sideways.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Crumb(label = "Home", isCurrent = path.isEmpty(), targetId = null, onNavigate, onDropNode)
        path.forEachIndexed { index, folder ->
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Crumb(
                label = folder.name,
                isCurrent = index == path.lastIndex,
                targetId = folder.id,
                onNavigate = onNavigate,
                onDropNode = onDropNode,
            )
        }
    }
}

/** One tappable, droppable piece of the path. */
@Composable
private fun Crumb(
    label: String,
    isCurrent: Boolean,
    targetId: Long?,
    onNavigate: (folderId: Long?) -> Unit,
    onDropNode: (draggedId: Long, targetId: Long?, targetName: String) -> Unit,
) {
    val dropTarget = rememberNodeDropTarget { draggedId -> onDropNode(draggedId, targetId, label) }
    val hovered = dropTarget.isHovered

    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(AppShapes.badge)
            .background(if (hovered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(
                if (hovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(0.dp, Color.Transparent),
                AppShapes.badge,
            )
            // The current folder isn't a drop target (the card is already here).
            .nodeDropTarget(dropTarget, enabled = !isCurrent)
            .clickable(enabled = !isCurrent) { onNavigate(targetId) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
