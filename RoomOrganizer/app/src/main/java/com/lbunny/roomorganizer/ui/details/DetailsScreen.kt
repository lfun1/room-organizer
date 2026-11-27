package com.lbunny.roomorganizer.ui.details

// =============================================================================
// DetailsScreen.kt  —  everything about one folder or item
// =============================================================================
//
//   ┌─────────────────────────────────────┐
//   │ ←  Closet                  [＋]  ⋮  │
//   │ ┌─────────────────────────────────┐ │
//   │ │            photo                │ │  (or a large icon if no photo)
//   │ └─────────────────────────────────┘ │
//   │ Closet                              │  name
//   │ Folder                              │  kind
//   │ (📍 Home › Room ▾)                   │  location chip → "Where is this?"
//   │ Description                         │
//   │ Winter clothes and shoes            │
//   │ Categories  (Clothing) (Winter)     │  NEW in v1
//   │ Quantity    3                       │  NEW in v1
//   │ Condition   Good                    │  NEW in v1
//   │ [ Open folder ]                     │  (folders only)
//   │ Inside (3)                          │  (folders only)
//   │ [🏷] [🏷] [📁]                        │  small icons of the contents
//   │ Rain  Boots Shoes                   │
//   └─────────────────────────────────────┘
//
// This matches the spec's example steps 5 & 6: tap "Rain Jacket" → name,
// description, photo; tap "Closet" → its info, then small icons of what's
// inside it.
// =============================================================================

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SuggestionChip
import com.lbunny.roomorganizer.ui.components.conditionColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lbunny.roomorganizer.RoomOrganizerApp
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.data.NodeWithCounts
import com.lbunny.roomorganizer.ui.NodeDialog
import com.lbunny.roomorganizer.ui.components.CreateButton
import com.lbunny.roomorganizer.ui.components.NodeDialogHost
import com.lbunny.roomorganizer.ui.components.NodeIcon
import com.lbunny.roomorganizer.ui.components.NodeOverflowMenu
import com.lbunny.roomorganizer.ui.components.PathDropdownMenu
import com.lbunny.roomorganizer.ui.components.kind
import com.lbunny.roomorganizer.ui.theme.AppShapes
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * @param nodeId        which folder/item to show
 * @param onOpenFolder  go INTO a folder's grid
 * @param onOpenDetails open the details of something inside this folder
 * @param onNavigateTo  jump to a folder from the path menu (null = Home)
 * @param onNavigateUp  go back one screen
 * @param positionLabel NEW in v1: e.g. "2 of 5", shown under the title when
 *                      this page is inside the swipeable pager (null = hide)
 * @param onSearchCategory NEW in v1: tapping a category chip opens search
 *                      filtered by that category
 * @param onOpenSearch  NEW in v1: the 🔍 button (search on every page)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    nodeId: Long,
    onOpenFolder: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onNavigateTo: (Long?) -> Unit,
    onNavigateUp: () -> Unit,
    positionLabel: String? = null,
    onSearchCategory: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as RoomOrganizerApp
    // CHANGED in v1: `key = "details-$nodeId"`. The swipe pager shows several
    // DetailsScreens on ONE navigation screen. Without a unique key they'd all
    // share the first page's ViewModel (and show the same node!). With a key,
    // each node gets its own ViewModel.
    val viewModel: DetailsViewModel = viewModel(key = "details-$nodeId") {
        DetailsViewModel(nodeId, app.repository)
    }

    val state by viewModel.state.collectAsState()
    val path by viewModel.path.collectAsState()
    val children by viewModel.children.collectAsState()

    var dialog by remember { mutableStateOf<NodeDialog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // If the node was deleted, close this screen. LaunchedEffect runs its
    // block once whenever `state` becomes Gone.
    if (state is DetailsState.Gone) {
        LaunchedEffect(Unit) { onNavigateUp() }
    }

    // `as? Loaded` = "if it's Loaded, give me it; otherwise null".
    val node: Node? = (state as? DetailsState.Loaded)?.node

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(node?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // NEW in v1: "2 of 5 · swipe ‹ ›" hints that swiping works.
                        if (positionLabel != null) {
                            Text(
                                positionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // NEW in v1: 🔍 global search, same as on Home / folder screens
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    if (node != null) {
                        // ＋ box: create inside this folder, or (for an item)
                        // next to it in the same folder.
                        val createIn = if (node.type == NodeType.FOLDER) node.id else node.parentId
                        CreateButton(onCreate = { type -> dialog = NodeDialog.Create(createIn, type) })
                        NodeOverflowMenu(
                            node = node,
                            path = path,
                            onEdit = { dialog = NodeDialog.Edit(node) },
                            onMove = { dialog = NodeDialog.Move(node) },
                            onDelete = { dialog = NodeDialog.Delete(node) },
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
        if (node != null) {
            DetailsContent(
                node = node,
                path = path,
                children = children,
                onOpenFolder = onOpenFolder,
                onOpenDetails = onOpenDetails,
                onNavigateTo = onNavigateTo,
                onSearchCategory = onSearchCategory,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    NodeDialogHost(dialog = dialog, onDismiss = { dialog = null }, viewModel = viewModel)
}

/** The scrollable body of the details screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(
    node: Node,
    path: List<Node>,
    children: List<NodeWithCounts>,
    onOpenFolder: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onNavigateTo: (Long?) -> Unit,
    onSearchCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- Photo (or big icon) --------------------------------------------
        if (node.photoPath != null) {
            AsyncImage(
                model = File(node.photoPath),
                contentDescription = "Photo of ${node.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(AppShapes.photo),
            )
        } else {
            NodeIcon(node = node, size = 96.dp)
        }

        // ---- Name + kind ------------------------------------------------------
        Column {
            Text(node.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                node.kind().label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Location chip: "📍 Home › Room ▾" → opens the path dropdown ------
        LocationChip(path = path, onNavigateTo = onNavigateTo)

        // ---- Description --------------------------------------------------------
        Column {
            Text("Description", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                node.description.ifBlank { "No description" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (node.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }

        // ---- NEW in v1: categories, quantity, condition -----------------------
        AttributesSection(node, onSearchCategory)

        // ---- Folders only: open button + small icons of the contents -----------
        if (node.type == NodeType.FOLDER) {
            FilledTonalButton(onClick = { onOpenFolder(node.id) }, shape = AppShapes.button) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open folder")
            }

            Text("Inside (${children.size})", style = MaterialTheme.typography.titleSmall)
            if (children.isEmpty()) {
                Text(
                    "Nothing inside yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // FlowRow = lay items left→right, wrapping onto new lines.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    children.forEach { child ->
                        SmallChildIcon(child.node, onClick = { onOpenDetails(child.node.id) })
                    }
                }
            }
        }

        // ---- Dates --------------------------------------------------------------
        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        Text(
            "Created ${dateFormat.format(Date(node.createdAt))} · " +
                "Updated ${dateFormat.format(Date(node.updatedAt))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * NEW in v1: shows the three filterable fields.
 *
 *   Categories   (Clothing) (Winter)
 *   Quantity     3
 *   Condition    Good          ← colored: green / orange / red
 *
 * Unset fields say "Not set" so you can see at a glance what to fill in
 * (tap ⋮ → Edit to add them).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributesSection(node: Node, onSearchCategory: (String) -> Unit) {
    val notSetColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Categories: one chip each, wrapping onto new lines.
        AttributeRow(label = "Categories") {
            if (node.categories.isEmpty()) {
                Text("Not set", color = notSetColor)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    node.categories.forEach { category ->
                        // Tap → search everything in this category (v1).
                        SuggestionChip(onClick = { onSearchCategory(category) }, label = { Text(category) })
                    }
                }
            }
        }
        AttributeRow(label = "Quantity") {
            Text(node.quantity?.toString() ?: "Not set", color = if (node.quantity == null) notSetColor else Color.Unspecified)
        }
        AttributeRow(label = "Condition") {
            val condition = node.condition
            if (condition == null) Text("Not set", color = notSetColor)
            else Text(condition.label, color = conditionColor(condition), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One "Label   value" line, with the labels lined up in a column. */
@Composable
private fun AttributeRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(100.dp),  // fixed width → values line up
        )
        Box(Modifier.weight(1f)) { value() }
    }
}

/**
 * A chip showing where this node lives, e.g. "Home › Room". Tapping it
 * opens the full "Where is this?" dropdown.
 */
@Composable
private fun LocationChip(path: List<Node>, onNavigateTo: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // The path includes the node itself as its last entry; the LOCATION is
    // everything before it.
    val location = (listOf("Home") + path.dropLast(1).map { it.name }).joinToString(" › ")

    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(location, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = "Location", modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = AppShapes.button,
        )
        PathDropdownMenu(
            expanded = open,
            onDismiss = { open = false },
            path = path,
            onNavigate = onNavigateTo,
        )
    }
}

/** A small icon with the name underneath, for the "Inside" section. */
@Composable
private fun SmallChildIcon(node: Node, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(AppShapes.row)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        NodeIcon(node = node, size = 56.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            node.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
