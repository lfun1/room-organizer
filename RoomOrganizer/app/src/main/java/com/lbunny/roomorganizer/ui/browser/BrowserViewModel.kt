package com.lbunny.roomorganizer.ui.browser

// =============================================================================
// BrowserViewModel.kt  —  data for the Home / folder grid screen
// =============================================================================
// One BrowserViewModel exists per open folder screen. Navigating Home → Room →
// Closet creates three, each remembering its own folder; pressing Back
// returns to the previous one with its data still loaded.
//
// Everything here is a live StateFlow from the database, so the grid updates
// on its own after any create / edit / move / delete.
// Shared actions (create, edit, move, delete) come from NodeActionsViewModel.
// =============================================================================

import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeRepository
import com.lbunny.roomorganizer.data.NodeWithCounts
import com.lbunny.roomorganizer.ui.NodeActionsViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * @param folderId the folder this screen shows; null = the Home screen
 */
class BrowserViewModel(
    val folderId: Long?,
    repository: NodeRepository,
) : NodeActionsViewModel(repository) {

    /** The grid: everything directly inside this folder, with counts. */
    val children: StateFlow<List<NodeWithCounts>> = repository.observeChildren(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This folder itself (for the title + ⋮ menu). Always null on Home. */
    val folder: StateFlow<Node?> =
        (if (folderId == null) flowOf(null) else repository.observeNode(folderId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Home → ... → this folder, for the breadcrumb bar. Empty on Home. */
    val path: StateFlow<List<Node>> =
        (if (folderId == null) flowOf(emptyList()) else repository.observePath(folderId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
