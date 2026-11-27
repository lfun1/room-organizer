package com.lbunny.roomorganizer.ui.browser

// =============================================================================
// BrowserScreen.kt  —  the Home screen AND every folder screen
// =============================================================================
//
//   ┌─────────────────────────────────────┐
//   │ ←  Closet              🔍 [＋]  ⋮  │  top bar (search v1, ＋ box, folder menu)
//   │ Home › Room › Closet                │  breadcrumb bar (drop targets)
//   │ ┌──────────────┐ ┌──────────────┐   │
//   │ │ 📁 Shoes   ⋮ │ │ 🏷 Rain    ⋮ │   │  grid of NodeCards
//   │ │ 🏷3  📁0     │ │   Jacket     │   │
//   │ └──────────────┘ └──────────────┘   │
//   └─────────────────────────────────────┘
//
// Home is just this same screen with folderId = null: no back arrow, no
// folder ⋮ menu, no breadcrumb bar, and title "Home".
// =============================================================================

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lbunny.roomorganizer.RoomOrganizerApp
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.ui.NodeDialog
import com.lbunny.roomorganizer.ui.components.BreadcrumbBar
import com.lbunny.roomorganizer.ui.components.CreateButton
import com.lbunny.roomorganizer.ui.components.NodeCard
import com.lbunny.roomorganizer.ui.components.NodeDialogHost
import com.lbunny.roomorganizer.ui.components.NodeOverflowMenu
import com.lbunny.roomorganizer.ui.components.nodeDragMonitor
import com.lbunny.roomorganizer.ui.components.rememberDragMonitor

/**
 * @param folderId      which folder to show (null = Home)
 * @param onOpenFolder  go INTO a folder
 * @param onOpenDetails open the details screen of a folder or item
 * @param onNavigateTo  jump to any folder from a path (null = Home)
 * @param onNavigateUp  go back one screen
 * @param onOpenSearch  NEW in v1: open the global search screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    folderId: Long?,
    onOpenFolder: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onNavigateTo: (Long?) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    // ---- Get this screen's ViewModel ----------------------------------------
    // `viewModel { ... }` creates it the first time and returns the SAME one
    // on every redraw/rotation. The { } block says how to build it: give it
    // this folder's id and the shared repository from RoomOrganizerApp.
    val app = LocalContext.current.applicationContext as RoomOrganizerApp
    val viewModel: BrowserViewModel = viewModel { BrowserViewModel(folderId, app.repository) }

    // `collectAsState()` turns a StateFlow into a value Compose watches: when
    // the database changes, these update and the screen redraws.
    val children by viewModel.children.collectAsState()
    val folder by viewModel.folder.collectAsState()
    val path by viewModel.path.collectAsState()

    // Which dialog is open (null = none). See NodeDialogHost.
    var dialog by remember { mutableStateOf<NodeDialog?>(null) }

    // Snackbar = the short message bar at the bottom ("Moved Closet to Room").
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Remembers which card (if any) is being dragged right now.
    val dragMonitor = rememberDragMonitor()

    /** Move a dragged card (it's always one in this grid) into a folder. */
    fun moveDragged(draggedId: Long, targetId: Long?, targetName: String) {
        if (draggedId == targetId) return  // dropped onto itself: ignore
        val dragged = children.find { it.node.id == draggedId }?.node ?: return
        viewModel.move(dragged, targetId, targetName)
    }

    // Scaffold = the standard page layout: top bar + content + snackbar area.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        folder?.name ?: if (folderId == null) "Home" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (folderId != null) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // NEW in v1: 🔍 global search (searches EVERYTHING, not just this folder)
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    // ＋ box — on every screen
                    CreateButton(onCreate = { type -> dialog = NodeDialog.Create(folderId, type) })
                    // ⋮ for the folder we're inside (not on Home)
                    folder?.let { current ->
                        NodeOverflowMenu(
                            node = current,
                            path = path,
                            onEdit = { dialog = NodeDialog.Edit(current) },
                            onMove = { dialog = NodeDialog.Move(current) },
                            onDelete = { dialog = NodeDialog.Delete(current) },
                            onNavigate = onNavigateTo,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // innerPadding = space taken by the top bar etc., so content isn't hidden under it.
        // nodeDragMonitor = watch drags anywhere in this area so the dragged
        // card can fade out (see DragAndDrop.kt, part 3).
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .nodeDragMonitor(dragMonitor)
        ) {
            if (folderId != null) {
                BreadcrumbBar(
                    path = path,
                    onNavigate = onNavigateTo,
                    onDropNode = ::moveDragged,
                )
            }

            if (children.isEmpty()) {
                EmptyFolderMessage(isHome = folderId == null)
            } else {
                // A grid that fits as many ~170dp-wide columns as the screen
                // allows (2 on most phones), and only builds the cards that
                // are on screen ("Lazy") so huge folders stay fast.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // key = node id: lets Compose animate/track each card correctly.
                    items(children, key = { it.node.id }) { entry ->
                        val node = entry.node
                        val isFolder = node.type == NodeType.FOLDER
                        NodeCard(
                            entry = entry,
                            path = path + node,
                            isBeingDragged = dragMonitor.draggingNodeId == node.id,
                            onClick = { onOpenDetails(node.id) },
                            onDoubleClick = { if (isFolder) onOpenFolder(node.id) else onOpenDetails(node.id) },
                            onEdit = { dialog = NodeDialog.Edit(node) },
                            onMove = { dialog = NodeDialog.Move(node) },
                            onDelete = { dialog = NodeDialog.Delete(node) },
                            onNavigate = onNavigateTo,
                            onDropNode = if (isFolder) { draggedId -> moveDragged(draggedId, node.id, node.name) } else null,
                        )
                    }
                }
            }
        }
    }

    NodeDialogHost(
        dialog = dialog,
        onDismiss = { dialog = null },
        viewModel = viewModel,
        // If the folder we're INSIDE gets deleted, leave this screen.
        onDeleted = { deleted -> if (deleted.id == folderId) onNavigateUp() },
    )
}

/** Shown when a folder (or Home) has nothing in it yet. */
@Composable
private fun EmptyFolderMessage(isHome: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isHome) "No boxes yet" else "This folder is empty",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap ＋ in the top right to add a folder or item.\n" +
                    "Tap a card for details · double-tap a folder to open it · " +
                    "long-press to drag it into another folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
