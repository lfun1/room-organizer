package com.lbunny.roomorganizer.ui.details

// =============================================================================
// DetailsPagerScreen.kt  —  NEW in v1: swipe between neighbors' details
// =============================================================================
//
// Open "Rain Jacket" from a folder, then swipe left/right to flip through
// every OTHER folder and item in the same folder, like photos in a gallery:
//
//     ◀ swipe ▶
//   ┌──────────┐┌──────────┐┌──────────┐
//   │  Boots   ││  Rain    ││  Scarf   │   ← all inside "Closet"
//   │ 1 of 3   ││  Jacket  ││ 3 of 3   │
//   │          ││ 2 of 3   ││          │
//   └──────────┘└──────────┘└──────────┘
//
// The pages are in the SAME order as the folder's grid (folders first, then
// A→Z), and each page is a full DetailsScreen with its own top bar, ⋮ menu,
// and ViewModel.
//
// HOW IT WORKS
//   1. DetailsPagerViewModel looks up the node you tapped, notes which folder
//      it's in (its parentId), and then keeps a LIVE list of everything in
//      that folder: the "siblings".
//   2. HorizontalPager (from Compose) shows one page per sibling, starting on
//      the one you tapped, and handles the swipe animation.
//   3. Because the list is live, creating/deleting/moving a sibling while
//      you're here adds or removes its page automatically.
// =============================================================================

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lbunny.roomorganizer.RoomOrganizerApp
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// =============================================================================
// ViewModel (small enough to live in the same file as its screen)
// =============================================================================

/**
 * Keeps the live list of siblings: everything in the same folder as the node
 * you opened (including that node itself).
 *
 * @param startNodeId the node you tapped to get here
 */
class DetailsPagerViewModel(
    startNodeId: Long,
    repository: NodeRepository,
) : ViewModel() {

    /**
     *   null       = still loading
     *   emptyList  = the start node doesn't exist anymore → close the screen
     *   otherwise  = the pages, in grid order
     *
     * `flow { ... }` = build a Flow step by step:
     *   - `.first()` waits for ONE value from the node's live query (we only
     *     need to know which folder it's in, once)
     *   - `emitAll(...)` then forwards that folder's live contents forever
     * We fix the folder at the start on purpose: if you MOVE the current item
     * away, you stay paging through the folder you came from.
     */
    val siblings: StateFlow<List<Node>?> = flow {
        val start = repository.observeNode(startNodeId).first()
        if (start == null) {
            emit(emptyList())
        } else {
            emitAll(
                repository.observeChildren(start.parentId)
                    .map { entries -> entries.map { it.node } }  // drop the counts; just the nodes
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

// =============================================================================
// Screen
// =============================================================================

/**
 * Same parameters as DetailsScreen; [startNodeId] = the page to start on.
 */
@Composable
fun DetailsPagerScreen(
    startNodeId: Long,
    onOpenFolder: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onNavigateTo: (Long?) -> Unit,
    onNavigateUp: () -> Unit,
    onSearchCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as RoomOrganizerApp
    val viewModel: DetailsPagerViewModel = viewModel { DetailsPagerViewModel(startNodeId, app.repository) }
    val siblings by viewModel.siblings.collectAsState()

    // Still loading: draw nothing for that split second.
    // (`?: return` = "if null, stop here"; Compose redraws when it loads.)
    val pages = siblings ?: return

    // Nothing to show (the node was deleted): leave.
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onNavigateUp() }
        return
    }

    // Which page to start on: the node you tapped. Computed ONCE (remember),
    // so later list changes don't yank you back to it.
    val startPage = remember { pages.indexOfFirst { it.id == startNodeId }.coerceAtLeast(0) }

    // The pager's state: current page + how many pages. `{ pages.size }` is
    // re-read whenever the list changes.
    val pagerState = rememberPagerState(initialPage = startPage) { pages.size }

    HorizontalPager(
        state = pagerState,
        // A stable key per node: if a page before you is deleted, the pager
        // keeps you on the SAME node instead of the same position number.
        key = { index -> pages.getOrNull(index)?.id ?: index },
    ) { pageIndex ->
        val node = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        DetailsScreen(
            nodeId = node.id,
            onOpenFolder = onOpenFolder,
            onOpenDetails = onOpenDetails,
            onNavigateTo = onNavigateTo,
            onNavigateUp = onNavigateUp,
            // Only show the hint when there's something to swipe to.
            positionLabel = if (pages.size > 1) "${pageIndex + 1} of ${pages.size} · swipe ‹ ›" else null,
            onSearchCategory = onSearchCategory,
            onOpenSearch = onOpenSearch,
        )
    }
}
