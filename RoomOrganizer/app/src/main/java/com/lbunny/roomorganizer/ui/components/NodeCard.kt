package com.lbunny.roomorganizer.ui.components

// =============================================================================
// NodeCard.kt  —  how a folder or item looks in the grid
// =============================================================================
//
//   NodeIcon    the smooth-cornered square picture: the photo if there is
//               one, otherwise a colored symbol
//                 📦 orange box   = top-level container on Home
//                 📁 grey folder  = folder inside another folder
//                 🏷 green tag    = item
//   CountBadge  a tiny pill like "🏷 3" (3 items inside)
//   NodeCard    the whole card: icon + name + kind + badges + ⋮ menu
//   NodeAttributeTags  NEW in v1: tiny tags [×3] [Good] [Clothing] under the card
//
// GESTURES on a card (per the spec):
//   single tap   → details
//   double tap   → open the folder (items: details)
//   long-press   → start dragging; drop onto a folder card or a path crumb
// =============================================================================

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.lbunny.roomorganizer.data.ItemCondition
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.data.NodeWithCounts
import com.lbunny.roomorganizer.ui.theme.AppShapes
import com.lbunny.roomorganizer.ui.theme.BoxColor
import com.lbunny.roomorganizer.ui.theme.FolderColor
import com.lbunny.roomorganizer.ui.theme.ItemColor
import java.io.File

// -----------------------------------------------------------------------------
// Node "kind": decides the icon, color, and label of a node
// -----------------------------------------------------------------------------

/** What the user sees a node as. A folder on Home is shown as a "Box". */
data class NodeKind(val label: String, val icon: ImageVector, val color: Color)

fun Node.kind(): NodeKind = when {
    type == NodeType.ITEM -> NodeKind("Item", Icons.Outlined.Category, ItemColor)
    parentId == null -> NodeKind("Box", Icons.Outlined.Inventory2, BoxColor)
    else -> NodeKind("Folder", Icons.Outlined.Folder, FolderColor)
}

// -----------------------------------------------------------------------------
// NodeIcon
// -----------------------------------------------------------------------------

/**
 * A smooth-cornered square showing the node's photo, or its kind's symbol.
 * Used on cards, in menus, and on the details screen (at different sizes).
 */
@Composable
fun NodeIcon(node: Node, size: Dp, modifier: Modifier = Modifier) {
    val kind = node.kind()
    Box(
        modifier = modifier
            .size(size)
            .clip(AppShapes.iconTile(size))          // the smoothed corners
            .background(kind.color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (node.photoPath != null) {
            // Coil's AsyncImage loads the photo file in the background and
            // shows it when ready. ContentScale.Crop fills the square,
            // trimming the edges instead of squashing the picture.
            AsyncImage(
                model = File(node.photoPath),
                contentDescription = "Photo of ${node.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = kind.icon,
                contentDescription = kind.label,
                tint = kind.color,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// CountBadge
// -----------------------------------------------------------------------------

/** A tiny pill: an icon and a number, e.g. "🏷 3". */
@Composable
fun CountBadge(icon: ImageVector, count: Int, description: String) {
    Row(
        modifier = Modifier
            .clip(AppShapes.badge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = description,  // read aloud by screen readers
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// -----------------------------------------------------------------------------
// NodeCard
// -----------------------------------------------------------------------------

/**
 * One card in the grid.
 *
 * @param entry          the node + how many items/folders are directly inside it
 * @param path           Home→node path (for "Where is this?")
 * @param isBeingDragged true while this card is "picked up" → drawn faded
 * @param onDropNode     what to do when another card is dropped on this one;
 *                       null = this card doesn't accept drops (items)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodeCard(
    entry: NodeWithCounts,
    path: List<Node>,
    isBeingDragged: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onNavigate: (folderId: Long?) -> Unit,
    onDropNode: ((draggedId: Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val node = entry.node
    val kind = node.kind()
    val dropTarget = rememberNodeDropTarget { draggedId -> onDropNode?.invoke(draggedId) }
    val startDrag = rememberStartNodeDrag()

    Card(
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            // Highlight in soft blue while something is dragged over this card.
            containerColor = if (dropTarget.isHovered) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (dropTarget.isHovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        // Modifier ORDER matters: each one wraps the next.
        modifier = modifier
            .alpha(if (isBeingDragged) 0.35f else 1f)                   // fade while "picked up"
            .clip(AppShapes.card)                                       // keep the tap ripple inside the corners
            .nodeDropTarget(dropTarget, enabled = onDropNode != null)   // accept drops (folders only)
            // ONE handler for all three gestures (see DragAndDrop.kt for why):
            .combinedClickable(
                onClick = onClick,                                      // tap        → details
                onDoubleClick = onDoubleClick,                          // double tap → open
                onLongClick = { startDrag(node.id, node.name) },        // long-press → start dragging
            ),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NodeIcon(node = node, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                // `weight(1f)` = take all leftover width, pushing ⋮ to the right edge.
                Column(Modifier.weight(1f)) {
                    Text(
                        node.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,   // "Very long na…"
                    )
                    Text(
                        kind.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NodeOverflowMenu(
                    node = node,
                    path = path,
                    onEdit = onEdit,
                    onMove = onMove,
                    onDelete = onDelete,
                    onNavigate = onNavigate,
                )
            }

            // Folders: small count badges. Items: first lines of the description.
            if (node.type == NodeType.FOLDER) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    CountBadge(Icons.Outlined.Category, entry.itemCount, "${entry.itemCount} items")
                    CountBadge(Icons.Outlined.Folder, entry.folderCount, "${entry.folderCount} folders")
                }
            } else if (node.description.isNotBlank()) {
                Text(
                    node.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
                )
            }

            // NEW in v1: quantity / condition / categories (only if any are set).
            if (node.hasAttributes()) {
                NodeAttributeTags(
                    node = node,
                    maxCategories = 2,  // cards are small: show 2, then "+N"
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                )
            }
        }
    }
}

// =============================================================================
// NEW in v1: small tags for quantity, condition, and categories
// =============================================================================

/** True if the node has a quantity, a condition, or any category to show. */
fun Node.hasAttributes(): Boolean =
    quantity != null || condition != null || categories.isNotEmpty()

/**
 * A color hint for each condition: green = good shape, orange = so-so,
 * red = worn out. Makes "what can I throw away?" (v3) easy to eyeball.
 */
@Composable
fun conditionColor(condition: ItemCondition): Color = when (condition) {
    ItemCondition.NEW, ItemCondition.LIKE_NEW, ItemCondition.GOOD -> ItemColor
    ItemCondition.FAIR -> BoxColor
    ItemCondition.POOR, ItemCondition.BROKEN -> MaterialTheme.colorScheme.error
}

/**
 * A wrapping row of tiny tags, e.g.  [×3] [Good] [Clothing] [Winter] [+1]
 * Used on grid cards and in search results.
 *
 * @param maxCategories how many category tags to show before "+N more"
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeAttributeTags(node: Node, maxCategories: Int, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        node.quantity?.let { Tag(text = "×$it", description = "Quantity $it") }
        node.condition?.let { condition ->
            Tag(text = condition.label, color = conditionColor(condition), description = "Condition ${condition.label}")
        }
        node.categories.take(maxCategories).forEach { Tag(text = it, description = "Category $it") }
        val hidden = node.categories.size - maxCategories
        if (hidden > 0) Tag(text = "+$hidden", description = "$hidden more categories")
    }
}

/**
 * One tiny tag. [color] tints the text (and a faint background of the same
 * color); null = neutral grey.
 */
@Composable
private fun Tag(text: String, description: String, color: Color? = null) {
    val textColor = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val background = color?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(AppShapes.badge)
            .background(background)
            .semantics { contentDescription = description }  // read aloud by screen readers
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
