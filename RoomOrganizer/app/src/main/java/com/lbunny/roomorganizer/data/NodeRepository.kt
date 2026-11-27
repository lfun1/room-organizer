package com.lbunny.roomorganizer.data

// =============================================================================
// NodeRepository.kt  —  the ONE place screens go to read/change data
// =============================================================================
//
// LAYERS OF THE APP (top talks only to the layer right below it):
//
//      Screens (UI)          what you see and tap
//          ↓
//      ViewModels            hold each screen's data
//          ↓
//      NodeRepository  ← this file: the app's "rules" + multi-step jobs
//          ↓
//      NodeDao / AppDatabase the raw SQLite commands
//
// WHY HAVE THIS LAYER instead of letting screens call the DAO directly?
//   Some actions are more than one database command. For example "delete a
//   folder" also means "delete the photo FILES of everything inside it", and
//   "move" must first check the move is legal. Putting those rules here means
//   every screen gets them for free, and future features (v2's AI autofill,
//   v1's search) have one obvious place to plug in.
//
// PHOTOS
//   Photos come in from the gallery or camera as a `Uri` — an address like
//   "content://media/.../1234" that points at a picture owned by ANOTHER app.
//   That address can stop working later (e.g. the user deletes the photo from
//   the gallery). So we COPY the picture into our app's private folder
//       /data/data/com.lbunny.roomorganizer/files/photos/
//   and save the copy's path in the database (Node.photoPath).
// =============================================================================

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * NEW in v1: everything the user can type/choose in the create/edit form,
 * bundled into one object.
 *
 * WHY BUNDLE? Before v1, create() and edit() took `name, description`
 * separately. Each new field would have meant changing every function along
 * the way (dialog → ViewModel → repository). With one bundle, a future field
 * (e.g. v2's AI autofill fills in this same object!) is added HERE once and
 * simply travels along.
 */
data class NodeFields(
    val name: String,
    val description: String = "",
    val categories: List<String> = emptyList(),
    val quantity: Int? = null,
    val condition: ItemCondition? = null,
) {
    companion object {
        /** The current values of an existing node, for pre-filling the edit form. */
        fun from(node: Node) = NodeFields(
            name = node.name,
            description = node.description,
            categories = node.categories,
            quantity = node.quantity,
            condition = node.condition,
        )
    }
}

/**
 * NEW in v1: what the user is searching for. Every part is optional;
 * the defaults mean "no filter" (an empty SearchFilters() matches everything).
 *
 * @param query       keywords typed in the search box, e.g. "rain jacket"
 * @param type        only FOLDERs or only ITEMs (null = both)
 * @param category    one category to require, e.g. "Clothing" (null = any)
 * @param condition   one condition to require (null = any)
 * @param minQuantity / maxQuantity  quantity range (null = no limit)
 */
data class SearchFilters(
    val query: String = "",
    val type: NodeType? = null,
    val category: String? = null,
    val condition: ItemCondition? = null,
    val minQuantity: Int? = null,
    val maxQuantity: Int? = null,
) {
    /** True when NOTHING was typed or chosen yet (the screen shows a hint instead of results). */
    val isEmpty: Boolean
        get() = query.isBlank() && type == null && category == null &&
            condition == null && minQuantity == null && maxQuantity == null
}

/**
 * @param dao     the database commands (from AppDatabase.nodeDao())
 * @param context the app, needed to reach its private storage folders and to
 *                read photos that other apps (gallery/camera) hand us
 */
class NodeRepository(
    private val dao: NodeDao,
    private val context: Context,
) {

    /** The folder where our private photo copies live. Created if missing. */
    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    // =========================================================================
    // READ — these just pass the DAO's live Flows through to the screens
    // =========================================================================

    /** Live contents of a folder (null = Home), with item/folder counts. */
    fun observeChildren(parentId: Long?): Flow<List<NodeWithCounts>> =
        dao.observeChildren(parentId)

    /** Live single node (null once it's deleted). */
    fun observeNode(id: Long): Flow<Node?> = dao.observeNode(id)

    /** Live path from Home down to this node, e.g. [Room, Closet, Rain Jacket]. */
    fun observePath(id: Long): Flow<List<Node>> = dao.observePath(id)

    /** Live list of all folders, for the "Move to…" tree. */
    fun observeAllFolders(): Flow<List<Node>> = dao.observeAllFolders()

    // =========================================================================
    // SEARCH (NEW in v1)
    // =========================================================================

    /** Used to turn stored category text back into lists. */
    private val converters = Converters()

    /**
     * LIVE search results for [filters], best matches first.
     *
     * TWO STAGES:
     *   1. SQL (NodeDao.searchNodes) applies the exact filters: type,
     *      category, condition, quantity range.
     *   2. Kotlin (below) applies the KEYWORDS and sorts by relevance.
     *
     * KEYWORD RULES (stage 2):
     *   - The query is split into words: "rain jacket" → ["rain", "jacket"]
     *   - Each word may appear ANYWHERE, in any order, and partial words
     *     count: "jack" matches "Jacket".
     *   - Upper/lower case is ignored.
     *   - EVERY word must be found in the name, the description, OR a category.
     *     ("rain boots" does NOT match "Rain Jacket": "boots" is missing.)
     *   - RANKING: results whose NAME contains more of the words come first.
     *     Searching "winter" lists a folder named "Winter Clothes" above an
     *     item that only says "winter" in its description. Ties stay A→Z.
     *
     * Because it's a Flow, results update live: if you edit an item while
     * its search result is showing, the result list updates too.
     */
    fun search(filters: SearchFilters): Flow<List<NodeWithCounts>> {
        // Prepare the keywords once, lower-cased.
        // Regex("\\s+") = "one or more spaces/tabs", so extra spaces are ignored.
        val words = filters.query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

        return dao.searchNodes(
            type = filters.type?.name,
            // Wrap in bars + wildcards: "Clothing" → "%|Clothing|%" (see Converters.kt)
            categoryPattern = filters.category?.let { "%$CATEGORY_SEPARATOR$it$CATEGORY_SEPARATOR%" },
            condition = filters.condition?.name,
            minQuantity = filters.minQuantity,
            maxQuantity = filters.maxQuantity,
        ).map { rows ->
            // `map` runs this every time the database sends new rows.
            if (words.isEmpty()) return@map rows  // no keywords → filters only

            rows
                // Keep a row only if EVERY word appears somewhere in it.
                .filter { row ->
                    val name = row.node.name.lowercase()
                    val description = row.node.description.lowercase()
                    val categories = row.node.categories.joinToString(" ").lowercase()
                    words.all { word ->
                        word in name || word in description || word in categories
                    }
                }
                // Best first: how many of the words are in the NAME.
                // sortedByDescending is "stable", so ties keep the A→Z order
                // that the SQL query already produced.
                .sortedByDescending { row ->
                    val name = row.node.name.lowercase()
                    words.count { word -> word in name }
                }
        }
    }

    /**
     * LIVE alphabetical list of every category in use, without duplicates,
     * e.g. ["Clothing", "Tools", "Winter"]. Fills the search filter menu and
     * the editor's suggestions.
     */
    fun observeAllCategories(): Flow<List<String>> =
        dao.observeAllCategoryText().map { storedTexts ->
            storedTexts
                .flatMap { converters.toCategories(it) }  // "|A|B|" → [A, B], all joined into one list
                .distinctBy { it.lowercase() }            // "winter" and "Winter" count once
                .sortedBy { it.lowercase() }              // A→Z, ignoring case
        }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Creates a new folder or item and returns its new id.
     *
     * @param parentId the folder to create it in (null = Home)
     * @param fields   CHANGED in v1: name, description, categories, quantity,
     *                 condition, bundled together (see [NodeFields])
     * @param photoUri a photo picked from the gallery/camera, or null for none
     * @param onPhotoBackup NEW: told how the Gallery backup of a CAMERA photo
     *                 went (see [backUpIfCameraPhoto]). Not called otherwise.
     */
    suspend fun create(
        parentId: Long?,
        type: NodeType,
        fields: NodeFields,
        photoUri: Uri?,
        onPhotoBackup: (PhotoBackupResult) -> Unit = {},
    ): Long {
        // `?.let { ... }` = only run the block if photoUri is not null.
        val photoPath = photoUri?.let { copyPhotoIntoApp(it) }
        val newId = dao.insert(
            Node(
                parentId = parentId,
                type = type,
                name = fields.name.trim(),          // trim() removes stray spaces at the ends
                description = fields.description.trim(),
                photoPath = photoPath,
                categories = fields.categories,     // tidied by Converters when saved
                quantity = fields.quantity,
                condition = fields.condition,
            )
        )
        // The item is saved FIRST; only then do the (optional) Gallery backup,
        // so a backup problem can never stop the item from being saved.
        if (photoUri != null && photoPath != null) {
            backUpIfCameraPhoto(photoUri, photoPath, fields.name.trim(), onPhotoBackup)
        }
        return newId
    }

    // =========================================================================
    // UPDATE (edit)
    // =========================================================================

    /**
     * Saves edits to a node's fields and/or photo.
     *
     * @param original    the node as it was before editing
     * @param fields      CHANGED in v1: the edited values (see [NodeFields])
     * @param newPhotoUri a NEWLY picked photo, or null to leave the photo alone
     * @param removePhoto true if the user tapped "Remove photo"
     * @param onPhotoBackup NEW: told how the Gallery backup of a new CAMERA
     *                    photo went. Not called otherwise.
     */
    suspend fun edit(
        original: Node,
        fields: NodeFields,
        newPhotoUri: Uri?,
        removePhoto: Boolean,
        onPhotoBackup: (PhotoBackupResult) -> Unit = {},
    ) {
        // Work out the photo path the node should end up with.
        val photoPath: String? = when {
            newPhotoUri != null -> copyPhotoIntoApp(newPhotoUri)  // replaced
            removePhoto -> null                                   // removed
            else -> original.photoPath                            // unchanged
        }

        // `copy(...)` makes a new Node with only these fields changed.
        dao.update(
            original.copy(
                name = fields.name.trim(),
                description = fields.description.trim(),
                categories = fields.categories,
                quantity = fields.quantity,
                condition = fields.condition,
                photoPath = photoPath,
                updatedAt = System.currentTimeMillis(),
            )
        )

        // If the old photo was replaced or removed, delete its file so it
        // doesn't waste storage space.
        if (original.photoPath != null && original.photoPath != photoPath) {
            deletePhotoFile(original.photoPath)
        }

        // NEW: back up a newly taken camera photo to the Gallery (after saving).
        if (newPhotoUri != null && photoPath != null) {
            backUpIfCameraPhoto(newPhotoUri, photoPath, fields.name.trim(), onPhotoBackup)
        }
    }

    // =========================================================================
    // MOVE
    // =========================================================================

    /**
     * Moves a node (and everything inside it) into another folder.
     *
     * @param newParentId the destination folder's id, or null for Home
     * @return true if moved; false if the move was not allowed
     */
    suspend fun move(nodeId: Long, newParentId: Long?): Boolean {
        if (newParentId != null) {
            // Rule 1: the destination must be a FOLDER (items can't hold things).
            val destination = dao.getNode(newParentId) ?: return false
            if (destination.type != NodeType.FOLDER) return false

            // Rule 2: no loops. You can't put a folder inside itself or inside
            // one of its own sub-folders (e.g. "Room" into "Room › Closet").
            if (newParentId in dao.getSubtreeIds(nodeId)) return false
        }
        dao.move(nodeId, newParentId, now = System.currentTimeMillis())
        return true
    }

    /**
     * The ids of a node and everything nested inside it. The Move dialog uses
     * this to grey out folders that would be illegal destinations.
     */
    suspend fun subtreeIds(nodeId: Long): Set<Long> = dao.getSubtreeIds(nodeId).toSet()

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * Deletes a node and EVERYTHING inside it (SQLite's CASCADE rule handles
     * the database rows), plus all of their photo files.
     */
    suspend fun delete(node: Node) {
        // Collect photo paths FIRST — after the delete, those rows are gone.
        val photoPaths = dao.getSubtreePhotoPaths(node.id)
        dao.delete(node)
        photoPaths.forEach { deletePhotoFile(it) }
    }

    // =========================================================================
    // PHOTO HELPERS
    // =========================================================================

    /**
     * Makes an empty file + a shareable address (Uri) for the CAMERA app to
     * save a new picture into. After the camera finishes, that Uri is passed
     * to create()/edit() like any gallery photo, which copies it into
     * photosDir.
     *
     * FileProvider is Android's safe way to let another app (the camera) write
     * to one specific file of ours. It's registered in AndroidManifest.xml
     * (next step), under the name "<our package>.fileprovider".
     */
    fun newCameraPhotoUri(): Uri {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(cameraDir, "camera_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /**
     * Copies the picture at [uri] into our private photos folder and returns
     * the copy's full file path (to store in Node.photoPath).
     *
     * `withContext(Dispatchers.IO)` runs this on a background thread meant for
     * file work, so copying a big photo never freezes the screen.
     */
    private suspend fun copyPhotoIntoApp(uri: Uri): String = withContext(Dispatchers.IO) {
        // A random unique name (UUID) so two photos never overwrite each other.
        val destination = File(photosDir, "${UUID.randomUUID()}.jpg")

        // `use { }` automatically closes the file/stream when done, even if an
        // error happens partway through.
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open the selected photo")

        destination.absolutePath
    }

    // -------------------------------------------------------------------------
    // NEW: Gallery backup of camera photos (see PhotoBackup.kt)
    // -------------------------------------------------------------------------

    private val photoBackup = PhotoBackup(context)

    /**
     * True if [uri] came from OUR camera button. Camera photos are handed to
     * us through our own FileProvider (see newCameraPhotoUri), so their
     * address starts with "content://com.lbunny.roomorganizer.fileprovider/".
     * Gallery picks come from Android's photo picker instead.
     */
    private fun isCameraPhoto(uri: Uri): Boolean =
        uri.authority == "${context.packageName}.fileprovider"

    /**
     * For CAMERA photos only:
     *   1. copy the app's saved photo ([photoPath]) into the Gallery
     *      (SD card › Pictures › RoomOrganizer), named [title]
     *   2. delete the temporary file the camera wrote in cache/camera/ —
     *      the app already has its own copy, so it's no longer needed
     *      (before this, those temp files piled up and doubled the space used)
     * Gallery picks are left alone: they're already in the Gallery.
     *
     * Never throws: a failed backup is reported through [onResult] instead.
     */
    private suspend fun backUpIfCameraPhoto(
        uri: Uri,
        photoPath: String,
        title: String,
        onResult: (PhotoBackupResult) -> Unit,
    ) {
        if (!isCameraPhoto(uri)) return
        val result = withContext(Dispatchers.IO) {
            val backup = runCatching { photoBackup.backup(File(photoPath), title) }
                .getOrElse { PhotoBackupResult.Failed(it.message ?: "Unknown error") }
            // Clean up the camera's temp file. FileProvider supports delete().
            runCatching { context.contentResolver.delete(uri, null, null) }
            backup
        }
        onResult(result)
    }

    /** Deletes a photo file, but only if it's inside our own photos folder. */
    private suspend fun deletePhotoFile(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        // Safety check: never delete anything outside our photos folder.
        if (file.parentFile?.canonicalPath == photosDir.canonicalPath) {
            file.delete()
        }
    }
}
