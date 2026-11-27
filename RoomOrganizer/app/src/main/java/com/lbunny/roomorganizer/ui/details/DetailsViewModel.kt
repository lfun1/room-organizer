package com.lbunny.roomorganizer.ui.details

// =============================================================================
// DetailsViewModel.kt  —  data for the details screen of one folder or item
// =============================================================================

import androidx.lifecycle.viewModelScope
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeRepository
import com.lbunny.roomorganizer.data.NodeWithCounts
import com.lbunny.roomorganizer.ui.NodeActionsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The three situations the details screen can be in.
 *   Loading = still reading the database (a split second)
 *   Loaded  = here's the node
 *   Gone    = it doesn't exist (it was just deleted) → the screen closes
 *
 * Keeping "Loading" separate from "Gone" matters: otherwise the screen would
 * see "no node yet" at startup and wrongly close itself.
 */
sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val node: Node) : DetailsState
    data object Gone : DetailsState
}

class DetailsViewModel(
    nodeId: Long,
    repository: NodeRepository,
) : NodeActionsViewModel(repository) {

    /** The node being shown, kept live (edits appear immediately). */
    val state: StateFlow<DetailsState> = repository.observeNode(nodeId)
        // `map` converts each database result into a DetailsState.
        .map { node -> if (node == null) DetailsState.Gone else DetailsState.Loaded(node) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState.Loading)

    /** Home → ... → this node, for "Where is this?" and the location line. */
    val path: StateFlow<List<Node>> = repository.observePath(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What's directly inside (folders only; always empty for items). */
    val children: StateFlow<List<NodeWithCounts>> = repository.observeChildren(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
