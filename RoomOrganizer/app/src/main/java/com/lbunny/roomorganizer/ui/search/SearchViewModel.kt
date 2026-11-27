package com.lbunny.roomorganizer.ui.search

// =============================================================================
// SearchViewModel.kt  —  NEW in v1: data for the global search screen
// =============================================================================
//
// HOW DATA FLOWS ON THIS SCREEN
//
//   you type / tap a filter chip
//          │
//          ▼
//   _filters  (a MutableStateFlow<SearchFilters>: "what we're searching for")
//          │  every change...
//          ▼
//   repository.search(filters)   ← SQL filters + keyword matching & ranking
//          │  live results
//          ▼
//   results  →  the screen draws the list
//
// `flatMapLatest` is the key piece: whenever the filters change, it STOPS
// the previous search and starts a new one, so typing "jac" → "jack" →
// "jacke" never shows stale results from an earlier letter.
//
// Extends NodeActionsViewModel, so it also has allFolders / allCategories /
// messages like the other screens.
// =============================================================================

import androidx.lifecycle.viewModelScope
import com.lbunny.roomorganizer.data.ItemCondition
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeRepository
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.data.NodeWithCounts
import com.lbunny.roomorganizer.data.SearchFilters
import com.lbunny.roomorganizer.ui.NodeActionsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * @param initialCategory open search already filtered by this category
 *                        (e.g. after tapping a category chip on a details
 *                        page). null = start with no filters.
 */
class SearchViewModel(
    repository: NodeRepository,
    initialCategory: String? = null,
) : NodeActionsViewModel(repository) {

    // -------------------------------------------------------------------------
    // What we're searching for
    // -------------------------------------------------------------------------

    // `_filters` (private, changeable) / `filters` (public, read-only) is a
    // common pattern: only this ViewModel can change the filters, while the
    // screen can only read them.
    private val _filters = MutableStateFlow(SearchFilters(category = initialCategory))
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    // Each function below changes ONE part of the filters.
    // `update { it.copy(...) }` = "take the current filters, change one field".

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }
    fun setType(type: NodeType?) = _filters.update { it.copy(type = type) }
    fun setCategory(category: String?) = _filters.update { it.copy(category = category) }
    fun setCondition(condition: ItemCondition?) = _filters.update { it.copy(condition = condition) }
    fun setQuantityRange(min: Int?, max: Int?) =
        _filters.update { it.copy(minQuantity = min, maxQuantity = max) }

    /** Removes all filters but keeps the typed keywords. */
    fun clearFilters() = _filters.update { SearchFilters(query = it.query) }

    // -------------------------------------------------------------------------
    // Results
    // -------------------------------------------------------------------------

    /**
     * The live search results, best first.
     *   null       = nothing typed or chosen yet → the screen shows a hint
     *   emptyList  = searched, but no matches     → "No matches"
     *
     * `@OptIn(ExperimentalCoroutinesApi::class)` is needed for flatMapLatest,
     * which Kotlin still labels "experimental" (it's widely used and stable
     * in practice).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<NodeWithCounts>?> = _filters
        .flatMapLatest { current ->
            if (current.isEmpty) flowOf(null) else repository.search(current)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Where each folder lives, written out in full, keyed by folder id:
     *     3 → "Home › Room"
     *     7 → "Home › Room › Closet"
     * Each search result shows "in <its parent's path>", so you know where
     * to find the thing in real life.
     *
     * Built from the list of all folders (already loaded for "Move to…"), so
     * there's no extra database query per result.
     */
    val folderPaths: StateFlow<Map<Long, String>> = allFolders
        .map { folders -> buildFolderPaths(folders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The "in …" text for a node: its parent folder's full path, or just "Home". */
    fun locationOf(node: Node, paths: Map<Long, String>): String =
        node.parentId?.let { paths[it] } ?: "Home"
}

/**
 * Builds the id → "Home › A › B" map for every folder.
 *
 * For each folder, walk UP through parentIds collecting names until reaching
 * Home (parentId null), then reverse the list so the outermost comes first.
 * The step limit is a safety net: the move rules prevent loops, but if one
 * ever existed this stops instead of looping forever.
 */
private fun buildFolderPaths(folders: List<Node>): Map<Long, String> {
    val byId = folders.associateBy { it.id }   // id → folder, for quick lookups
    return folders.associate { folder ->
        val names = mutableListOf<String>()
        var current: Node? = folder
        var steps = 0
        while (current != null && steps < 100) {
            names += current.name
            current = current.parentId?.let { byId[it] }
            steps++
        }
        folder.id to (listOf("Home") + names.reversed()).joinToString(" › ")
    }
}
