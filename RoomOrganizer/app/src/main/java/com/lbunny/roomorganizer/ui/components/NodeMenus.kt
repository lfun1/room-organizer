package com.lbunny.roomorganizer.ui.components

// =============================================================================
// NodeMenus.kt  —  the dropdown menus
// =============================================================================
//   CreateButton      the ＋ box (top-right of every screen): New folder / New item
//   NodeOverflowMenu  the ⋮ button: Edit / Move to… / Where is this? / Delete
//   PathDropdownMenu  "Where is this?": Home › Room › Closet › Rain Jacket
//
// A `DropdownMenu` is a small pop-up list anchored to whatever it's placed
// next to. It shows while `expanded = true` and calls `onDismissRequest` when
// the user taps outside it.
// =============================================================================

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.ui.theme.AppShapes

// -----------------------------------------------------------------------------
// ＋ box
// -----------------------------------------------------------------------------

/**
 * The "plus sign box". Tapping it opens a small menu to choose what to create.
 * @param onCreate called with FOLDER or ITEM after the user picks one.
 */
@Composable
fun CreateButton(onCreate: (NodeType) -> Unit) {
    // `remember { mutableStateOf(false) }` = a variable that survives redraws
    // and triggers a redraw when changed. `by` lets us read/write it directly.
    var expanded by remember { mutableStateOf(false) }

    // A Box lets the DropdownMenu appear anchored right below the button.
    Box {
        FilledTonalIconButton(
            onClick = { expanded = true },
            shape = AppShapes.button,  // the smooth-cornered "box"
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Create new")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("New folder") },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                onClick = { expanded = false; onCreate(NodeType.FOLDER) },
            )
            DropdownMenuItem(
                text = { Text("New item") },
                leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                onClick = { expanded = false; onCreate(NodeType.ITEM) },
            )
        }
    }
}

// -----------------------------------------------------------------------------
// ⋮ menu
// -----------------------------------------------------------------------------

/**
 * The ⋮ ("more options") button with Edit / Move / Where is this? / Delete.
 * Used on every card AND in the top-right of the folder & details screens.
 *
 * @param path        Home→node path, for the "Where is this?" submenu
 * @param onNavigate  open a folder from the path menu (null = Home)
 */
@Composable
fun NodeOverflowMenu(
    node: Node,
    path: List<Node>,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onNavigate: (folderId: Long?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pathOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${node.name}")
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Move to…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
                onClick = { menuOpen = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text("Where is this?") },
                leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                // Close this menu and open the path menu in the same spot.
                onClick = { menuOpen = false; pathOpen = true },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuOpen = false; onDelete() },
            )
        }

        PathDropdownMenu(
            expanded = pathOpen,
            onDismiss = { pathOpen = false },
            path = path,
            onNavigate = onNavigate,
        )
    }
}

// -----------------------------------------------------------------------------
// "Where is this?" path menu
// -----------------------------------------------------------------------------

/**
 * Shows the location of a node as an indented list, outermost first:
 *
 *     Location
 *     🏠 Home
 *        📦 Room
 *           📁 Closet
 *              🏷 Rain Jacket   (bold = the node itself)
 *
 * Tapping a folder opens it. Items are shown but can't be "opened".
 *
 * @param path the nodes from the outermost folder down to the node itself
 *             (Home is added automatically at the top).
 */
@Composable
fun PathDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    path: List<Node>,
    onNavigate: (folderId: Long?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            "Location",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        DropdownMenuItem(
            text = { Text("Home") },
            leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            onClick = { onDismiss(); onNavigate(null) },
        )
        path.forEachIndexed { index, node ->
            val isLast = index == path.lastIndex
            DropdownMenuItem(
                text = {
                    Text(node.name, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)
                },
                leadingIcon = { NodeIcon(node = node, size = 24.dp) },
                // Indent one step deeper for each level.
                modifier = Modifier.padding(start = (16 * (index + 1)).dp),
                enabled = node.type == NodeType.FOLDER,
                onClick = { onDismiss(); onNavigate(node.id) },
            )
        }
    }
}
