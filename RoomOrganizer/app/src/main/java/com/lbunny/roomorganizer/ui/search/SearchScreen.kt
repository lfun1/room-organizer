package com.lbunny.roomorganizer.ui.search

// =============================================================================
// SearchScreen.kt  —  NEW in v1: global search with filters
// =============================================================================
//
//   ┌─────────────────────────────────────┐
//   │ ←  [ 🔍 rain jack            ✕ ]   │  search box (keyboard opens automatically)
//   │ (Type ▾) (Category ▾) (Condition ▾) │  filter chips (swipe sideways for more)
//   │ (Quantity ▾) (Clear)                │
//   │ 2 results                           │
//   │ 🏷 **Rain** **Jack**et               │  matched words in bold
//   │    in Home › Room › Closet          │  where it is
//   │    [×1] [Good] [Clothing]           │  its tags
//   │ 🏷 Kids **Rain** **Jack**et       ...│
//   └─────────────────────────────────────┘
//
// Searches EVERY folder and item at any depth. Tap a result for its details;
// folders also have an "open" button to jump straight into them.
// The keyword rules live in NodeRepository.search().
// =============================================================================

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lbunny.roomorganizer.RoomOrganizerApp
import com.lbunny.roomorganizer.data.ItemCondition
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.data.NodeWithCounts
import com.lbunny.roomorganizer.data.SearchFilters
import com.lbunny.roomorganizer.ui.components.NodeAttributeTags
import com.lbunny.roomorganizer.ui.components.NodeIcon
import com.lbunny.roomorganizer.ui.components.dismissKeyboardOnTap
import com.lbunny.roomorganizer.ui.components.rememberDismissKeyboard
import androidx.compose.foundation.text.KeyboardActions
import com.lbunny.roomorganizer.ui.components.hasAttributes
import com.lbunny.roomorganizer.ui.components.kind
import com.lbunny.roomorganizer.ui.theme.AppShapes

/**
 * @param initialCategory start already filtered by this category (or null)
 * @param onOpenDetails   open a result's details page
 * @param onOpenFolder    jump straight into a folder result
 * @param onNavigateUp    leave search
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialCategory: String?,
    onOpenDetails: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as RoomOrganizerApp
    val viewModel: SearchViewModel = viewModel { SearchViewModel(app.repository, initialCategory) }

    val filters by viewModel.filters.collectAsState()
    val results by viewModel.results.collectAsState()
    val folderPaths by viewModel.folderPaths.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()

    // The keywords split into words, for bolding matches in the results.
    val words = remember(filters.query) {
        filters.query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    Scaffold(
        topBar = {
            SearchBarRow(
                query = filters.query,
                onQueryChange = viewModel::setQuery,
                onBack = onNavigateUp,
            )
        },
    ) { innerPadding ->
        // dismissKeyboardOnTap: tap empty space (e.g. below the results) to
        // hide the keyboard (see KeyboardHelpers.kt).
        Column(Modifier.padding(innerPadding).fillMaxSize().dismissKeyboardOnTap()) {
            FilterChipsRow(
                filters = filters,
                allCategories = allCategories,
                onTypeChange = viewModel::setType,
                onCategoryChange = viewModel::setCategory,
                onConditionChange = viewModel::setCondition,
                onQuantityChange = viewModel::setQuantityRange,
                onClear = viewModel::clearFilters,
            )

            // `val current = results` takes a snapshot so Kotlin knows it
            // can't change between the checks below.
            when (val current = results) {
                null -> CenteredMessage(
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(56.dp)) },
                    title = "Search everything",
                    body = "Type a name, description, or category, or tap a filter above.\n" +
                        "Every word must match; partial words count (\"jack\" finds \"Jacket\").",
                )
                emptyList<NodeWithCounts>() -> CenteredMessage(
                    icon = { Icon(Icons.Outlined.SearchOff, contentDescription = null, modifier = Modifier.size(56.dp)) },
                    title = "No matches",
                    body = "Try fewer words or remove a filter.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            if (current.size == 1) "1 result" else "${current.size} results",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(current, key = { it.node.id }) { entry ->
                        SearchResultRow(
                            entry = entry,
                            location = viewModel.locationOf(entry.node, folderPaths),
                            words = words,
                            onClick = { onOpenDetails(entry.node.id) },
                            onOpenFolder = { onOpenFolder(entry.node.id) },
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Pieces of the screen
// =============================================================================

/** ← back arrow + the search text box (with a ✕ to clear it). */
@Composable
private fun SearchBarRow(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    // FocusRequester = a handle to put the typing cursor into the box. We use
    // it to open the keyboard automatically when the screen appears.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val dismissKeyboard = rememberDismissKeyboard()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // The app draws behind the phone's status bar (edge-to-edge), and
            // this custom Row isn't a TopAppBar, so add the status bar's
            // height ourselves to keep the search box below the clock/battery.
            .statusBarsPadding()
            .padding(top = 8.dp, end = 12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search folders & items") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,   // pill-shaped, like Drive's search bar
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // The keyboard's 🔍 key closes the keyboard so you can see all
            // the results (they already update as you type).
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
    }
}

/**
 * The row of filter chips. A chip is "selected" (filled in) when its filter
 * is active, and its label shows the current choice, e.g. "Condition: Good".
 * The row scrolls sideways if it doesn't fit.
 */
@Composable
private fun FilterChipsRow(
    filters: SearchFilters,
    allCategories: List<String>,
    onTypeChange: (NodeType?) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onConditionChange: (ItemCondition?) -> Unit,
    onQuantityChange: (Int?, Int?) -> Unit,
    onClear: () -> Unit,
) {
    var showQuantityDialog by remember { mutableStateOf(false) }
    val anyFilter = filters.type != null || filters.category != null || filters.condition != null ||
        filters.minQuantity != null || filters.maxQuantity != null

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // ---- Type ----
        ChipWithMenu(
            label = when (filters.type) {
                null -> "Type"
                NodeType.FOLDER -> "Folders"
                NodeType.ITEM -> "Items"
            },
            selected = filters.type != null,
            options = listOf<Pair<String, NodeType?>>("Any type" to null, "Folders" to NodeType.FOLDER, "Items" to NodeType.ITEM),
            onSelect = onTypeChange,
        )

        // ---- Category (menu built from categories in use) ----
        ChipWithMenu(
            label = filters.category ?: "Category",
            selected = filters.category != null,
            options = listOf<Pair<String, String?>>("Any category" to null) + allCategories.map { it to it },
            onSelect = onCategoryChange,
            emptyHint = if (allCategories.isEmpty()) "No categories yet. Add some via ⋮ → Edit." else null,
        )

        // ---- Condition ----
        ChipWithMenu(
            label = filters.condition?.label ?: "Condition",
            selected = filters.condition != null,
            options = listOf<Pair<String, ItemCondition?>>("Any condition" to null) +
                ItemCondition.entries.map { it.label to it },
            onSelect = onConditionChange,
        )

        // ---- Quantity (opens a small dialog with min / max boxes) ----
        FilterChip(
            selected = filters.minQuantity != null || filters.maxQuantity != null,
            onClick = { showQuantityDialog = true },
            label = { Text(quantityLabel(filters.minQuantity, filters.maxQuantity)) },
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )

        // ---- Clear (only when a filter is on) ----
        if (anyFilter) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }

    if (showQuantityDialog) {
        QuantityRangeDialog(
            initialMin = filters.minQuantity,
            initialMax = filters.maxQuantity,
            onDismiss = { showQuantityDialog = false },
            onApply = { min, max -> onQuantityChange(min, max); showQuantityDialog = false },
        )
    }
}

/** "Quantity", "Qty ≥ 2", "Qty ≤ 5", or "Qty 2–5". */
private fun quantityLabel(min: Int?, max: Int?): String = when {
    min != null && max != null -> "Qty $min–$max"
    min != null -> "Qty ≥ $min"
    max != null -> "Qty ≤ $max"
    else -> "Quantity"
}

/**
 * A filter chip that opens a dropdown of [options] (label → value).
 * `<T>` = works for any value type (NodeType?, String?, ItemCondition?...).
 */
@Composable
private fun <T> ChipWithMenu(
    label: String,
    selected: Boolean,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    emptyHint: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (optionLabel, value) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = { open = false; onSelect(value) },
                )
            }
            if (emptyHint != null) {
                Text(
                    emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Min / max quantity boxes. Leave a box empty for "no limit". */
@Composable
private fun QuantityRangeDialog(
    initialMin: Int?,
    initialMax: Int?,
    onDismiss: () -> Unit,
    onApply: (Int?, Int?) -> Unit,
) {
    var minText by remember { mutableStateOf(initialMin?.toString() ?: "") }
    var maxText by remember { mutableStateOf(initialMax?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quantity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Leave a box empty for no limit. Things without a quantity are hidden while this filter is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it.filter(Char::isDigit).take(6) },
                        label = { Text("At least") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it.filter(Char::isDigit).take(6) },
                        label = { Text("At most") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(minText.toIntOrNull(), maxText.toIntOrNull()) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(null, null) }) { Text("Remove filter") }
        },
    )
}

/**
 * One search result:
 *   [icon]  Name (matched words bold)              [📂 open] (folders)
 *           in Home › Room › Closet
 *           [×1] [Good] [Clothing]
 */
@Composable
private fun SearchResultRow(
    entry: NodeWithCounts,
    location: String,
    words: List<String>,
    onClick: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val node = entry.node
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        NodeIcon(node = node, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlightMatches(node.name, words, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${node.kind().label} · in $location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.hasAttributes()) {
                NodeAttributeTags(node = node, maxCategories = 3, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (node.type == NodeType.FOLDER) {
            IconButton(onClick = onOpenFolder) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Open ${node.name}")
            }
        }
    }
}

/**
 * Returns [text] with every occurrence of every search word drawn bold and
 * in [color]. "Rain Jacket" + ["jack"] → "Rain **Jack**et".
 *
 * An AnnotatedString is text where different ranges can have different
 * styles. We mark which characters matched, then write the text out piece by
 * piece, switching style on and off.
 */
private fun highlightMatches(
    text: String,
    words: List<String>,
    color: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (words.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val matched = BooleanArray(text.length)   // one true/false per character
    words.forEach { word ->
        var start = lower.indexOf(word)
        while (start >= 0) {
            for (i in start until start + word.length) matched[i] = true
            start = lower.indexOf(word, start + 1)
        }
    }
    return buildAnnotatedString {
        text.forEachIndexed { i, ch ->
            if (matched[i]) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(ch) }
            else append(ch)
        }
    }
}

/** A big icon + title + explanation, centered (used for "no results" etc.). */
@Composable
private fun CenteredMessage(icon: @Composable () -> Unit, title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
