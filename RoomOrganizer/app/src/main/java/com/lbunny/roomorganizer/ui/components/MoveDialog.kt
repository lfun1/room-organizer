package com.lbunny.roomorganizer.ui.components

// =============================================================================
// MoveDialog.kt  —  "Move to…" with a nested folder tree
// =============================================================================
//
//   ┌──────────────────────────────┐
//   │ Move "Closet" to…            │
//   │  🏠 Home                      │
//   │  ▾ 📦 Room                    │
//   │      📁 Closet  (greyed out)  │  ← can't move into itself
//   │      📁 Large Drawer          │
//   │  ▸ 📦 Garage                  │  ← ▸ = collapsed; tap to expand
//   │                Cancel  Move  │
//   └──────────────────────────────┘
//
// Tap a row to select it, then tap "Move".
//
// HOW THE TREE IS BUILT
//   The database gives us a FLAT list of all folders. We group them by
//   parentId ("which folders are inside folder X?"), then walk the tree from
//   Home downward, writing out one row per folder with its depth (how far to
//   indent). Collapsed folders' children are skipped.
// =============================================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.ui.theme.AppShapes

/** A destination the user picked. [folderId] null = Home. */
private data class MoveTarget(val folderId: Long?, val name: String)

/** One visible row of the tree: a folder and how deep it is. */
private data class TreeRow(val folder: Node, val depth: Int, val hasChildren: Boolean)

/**
 * @param node        the folder/item being moved
 * @param folders     every folder in the database
 * @param disabledIds folders that can't be chosen: the node itself and
 *                    everything inside it (that would create a loop)
 * @param onMove      called with the chosen destination
 */
@Composable
fun MoveDialog(
    node: Node,
    folders: List<Node>,
    disabledIds: Set<Long>,
    onDismiss: () -> Unit,
    onMove: (newParentId: Long?, destinationName: String) -> Unit,
) {
    // Group folders by their parent: childrenOf[null] = folders on Home, etc.
    // `remember(folders)` = only recompute when the folder list changes.
    val childrenOf: Map<Long?, List<Node>> = remember(folders) { folders.groupBy { it.parentId } }

    // Which folders are expanded. Start with everything expanded.
    var expanded by remember(folders) { mutableStateOf(folders.map { it.id }.toSet()) }

    // The currently selected destination (null = nothing picked yet).
    var selected by remember { mutableStateOf<MoveTarget?>(null) }

    // Flatten the visible part of the tree into rows, top to bottom.
    val rows: List<TreeRow> = remember(childrenOf, expanded) {
        buildList {
            // A small recursive function: add this folder's children, and for
            // each expanded child, ITS children (one level deeper), and so on.
            fun addChildrenOf(parentId: Long?, depth: Int) {
                childrenOf[parentId].orEmpty().forEach { folder ->
                    val hasChildren = !childrenOf[folder.id].isNullOrEmpty()
                    add(TreeRow(folder, depth, hasChildren))
                    if (folder.id in expanded) addChildrenOf(folder.id, depth + 1)
                }
            }
            addChildrenOf(parentId = null, depth = 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${node.name}\" to…") },
        text = {
            // heightIn(max) + verticalScroll = scroll if there are many folders.
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- Home row ----
                TreeRowView(
                    depth = 0,
                    label = "Home",
                    leading = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    isSelected = selected?.folderId == null && selected != null,
                    isCurrent = node.parentId == null,
                    enabled = node.parentId != null,
                    expandState = null,
                    onToggleExpand = {},
                    onSelect = { selected = MoveTarget(null, "Home") },
                )
                // ---- Folder rows ----
                rows.forEach { row ->
                    val folder = row.folder
                    val isCurrent = folder.id == node.parentId
                    TreeRowView(
                        depth = row.depth,
                        label = folder.name,
                        leading = { NodeIcon(node = folder, size = 24.dp) },
                        isSelected = selected?.folderId == folder.id,
                        isCurrent = isCurrent,
                        enabled = folder.id !in disabledIds && !isCurrent,
                        expandState = if (row.hasChildren) folder.id in expanded else null,
                        onToggleExpand = {
                            expanded = if (folder.id in expanded) expanded - folder.id else expanded + folder.id
                        },
                        onSelect = { selected = MoveTarget(folder.id, folder.name) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onMove(it.folderId, it.name) } },
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One row in the tree.
 * @param expandState null = no children (no arrow); true = expanded ▾; false = collapsed ▸
 * @param isCurrent   the node's current location (labelled "current", not selectable)
 */
@Composable
private fun TreeRowView(
    depth: Int,
    label: String,
    leading: @Composable () -> Unit,
    isSelected: Boolean,
    isCurrent: Boolean,
    enabled: Boolean,
    expandState: Boolean?,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.row)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)  // transparent
            )
            .clickable(enabled = enabled, onClick = onSelect)
            .alpha(if (enabled) 1f else 0.4f)       // greyed out when disabled
            .padding(start = (depth * 16).dp, end = 8.dp),
    ) {
        // Expand/collapse arrow (or empty space of the same width, to line up).
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (expandState != null) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (expandState) Icons.Outlined.KeyboardArrowDown
                        else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (expandState) "Collapse" else "Expand",
                    )
                }
            }
        }
        leading()
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        if (isCurrent) {
            Text(
                "current",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
