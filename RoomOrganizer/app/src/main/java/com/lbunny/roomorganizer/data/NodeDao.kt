package com.lbunny.roomorganizer.data

// =============================================================================
// NodeDao.kt  —  every database command the app uses, in one place
// =============================================================================
//
// DAO = "Data Access Object". It's an `interface`: a list of function
// signatures with NO bodies. We describe WHAT each function does (usually with
// a SQL query in an annotation), and Room's code generator (KSP) writes the
// actual code behind it at build time.
//
// Two kinds of functions appear below:
//
//   1. `suspend fun ...`  — a one-time action (insert, update, delete, look
//      something up once). `suspend` means it runs in the BACKGROUND, so the
//      screen never freezes while the database works.
//
//   2. `fun ...: Flow<...>` — a LIVE query. A Flow is like a subscription:
//      the screen gets the current result now, and Room automatically sends a
//      NEW result every time the `nodes` table changes. This is why the UI
//      updates by itself after you create, move, or delete something — we
//      never have to manually "refresh" a screen.
//
// SQL quick reference for the queries below:
//   SELECT ... FROM nodes WHERE ...   = read rows that match a condition
//   UPDATE nodes SET ... WHERE ...    = change columns in matching rows
//   :name                             = fills in the Kotlin parameter `name`
//   `a IS b`                          = like `a = b`, but ALSO true when both
//                                       are null (plain `=` never matches null).
//                                       We need this because Home = parentId null.
// =============================================================================

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A [Node] plus how many things are DIRECTLY inside it.
 * Used to draw the small count badges on each folder card ("3 items, 1 folder").
 *
 * `@Embedded` = "all of Node's columns are in this result too" — so this one
 * object holds the whole node AND the two extra numbers. For items (which
 * can't contain anything) both counts are simply 0.
 */
data class NodeWithCounts(
    @Embedded val node: Node,
    val itemCount: Int,
    val folderCount: Int,
)

@Dao
interface NodeDao {

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    /**
     * Saves a brand-new node and returns the `id` SQLite assigned to it.
     * (Pass a Node with id = 0; SQLite picks the real id.)
     */
    @Insert
    suspend fun insert(node: Node): Long

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    /**
     * LIVE list of everything directly inside one folder — this is what each
     * grid screen shows. Pass `parentId = null` to get the Home screen.
     *
     * The two `(SELECT COUNT(*) ...)` parts are "sub-queries": for each row
     * `n`, they count how many items / folders have `n` as their parent.
     *
     * ORDER BY: folders first (like Google Drive), then A→Z by name.
     *   `(n.type = 'FOLDER') DESC` → true(1) sorts before false(0)
     *   `COLLATE NOCASE`           → "apple" and "Apple" sort together
     */
    @Query(
        """
        SELECT n.*,
               (SELECT COUNT(*) FROM nodes c WHERE c.parentId = n.id AND c.type = 'ITEM')   AS itemCount,
               (SELECT COUNT(*) FROM nodes c WHERE c.parentId = n.id AND c.type = 'FOLDER') AS folderCount
        FROM nodes n
        WHERE n.parentId IS :parentId
        ORDER BY (n.type = 'FOLDER') DESC, n.name COLLATE NOCASE
        """
    )
    fun observeChildren(parentId: Long?): Flow<List<NodeWithCounts>>

    /**
     * LIVE single node by id (for the details screen and the top bar title).
     * Emits `null` if that node gets deleted, so the screen can close itself.
     */
    @Query("SELECT * FROM nodes WHERE id = :id")
    fun observeNode(id: Long): Flow<Node?>

    /** One-time lookup of a single node (used when editing / deleting). */
    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getNode(id: Long): Node?

    /**
     * LIVE "breadcrumb path" from Home down to this node, e.g.
     *     [Room, Closet, Rain Jacket]
     * Used by the "Where is this?" dropdown.
     *
     * HOW IT WORKS — a "recursive CTE" (WITH RECURSIVE):
     *   Step 1: start with the node itself, at depth 0.
     *   Step 2: repeatedly add the PARENT of the last row found, depth + 1.
     *   Stops automatically when a parentId is null (reached Home).
     * Then we sort by depth, biggest first, so the outermost folder comes first.
     */
    @Query(
        """
        WITH RECURSIVE ancestors(id, parentId, depth) AS (
            SELECT id, parentId, 0 FROM nodes WHERE id = :id
            UNION ALL
            SELECT n.id, n.parentId, a.depth + 1
            FROM nodes n JOIN ancestors a ON n.id = a.parentId
        )
        SELECT nodes.* FROM nodes JOIN ancestors ON nodes.id = ancestors.id
        ORDER BY ancestors.depth DESC
        """
    )
    fun observePath(id: Long): Flow<List<Node>>

    /**
     * LIVE list of every folder in the database, A→Z.
     * The "Move to…" dialog uses this to draw the full folder tree.
     */
    @Query("SELECT * FROM nodes WHERE type = 'FOLDER' ORDER BY name COLLATE NOCASE")
    fun observeAllFolders(): Flow<List<Node>>

    // -------------------------------------------------------------------------
    // SEARCH (NEW in v1)
    // -------------------------------------------------------------------------

    /**
     * LIVE search across EVERY folder and item, at any depth, narrowed by
     * the optional filters. Pass null for a filter to mean "any".
     *
     * KEYWORDS are NOT matched here: NodeRepository.search() does that in
     * Kotlin afterwards (matching each word in the name/description/
     * categories and putting name matches first). That's because one SQL
     * query can't easily accept "any number of words". This query does the
     * exact, structured filters, which SQL is great at.
     *
     * How each filter line works — the pattern `(:x IS NULL OR <check>)`
     * means "if no filter was given, let every row through; otherwise apply
     * the check":
     *
     * @param type            "FOLDER" or "ITEM", or null for both
     * @param categoryPattern e.g. "%|Clothing|%". `LIKE` compares text with
     *                        wildcards: `%` = "anything (or nothing) here".
     *                        So this finds rows whose categories text
     *                        contains exactly "|Clothing|" (see Converters.kt).
     *                        LIKE ignores upper/lower case for English letters.
     * @param condition       e.g. "GOOD", or null for any condition
     * @param minQuantity     quantity must be at least this (null = no minimum)
     * @param maxQuantity     quantity must be at most this (null = no maximum)
     *                        Note: rows with NO quantity are excluded whenever
     *                        a min or max is set (they weren't counted).
     *
     * Results include the same item/folder counts as the grid, so search
     * results can show count badges too.
     */
    @Query(
        """
        SELECT n.*,
               (SELECT COUNT(*) FROM nodes c WHERE c.parentId = n.id AND c.type = 'ITEM')   AS itemCount,
               (SELECT COUNT(*) FROM nodes c WHERE c.parentId = n.id AND c.type = 'FOLDER') AS folderCount
        FROM nodes n
        WHERE (:type IS NULL OR n.type = :type)
          AND (:categoryPattern IS NULL OR n.categories LIKE :categoryPattern)
          AND (:condition IS NULL OR n.condition = :condition)
          AND (:minQuantity IS NULL OR n.quantity >= :minQuantity)
          AND (:maxQuantity IS NULL OR n.quantity <= :maxQuantity)
        ORDER BY n.name COLLATE NOCASE
        """
    )
    fun searchNodes(
        type: String?,
        categoryPattern: String?,
        condition: String?,
        minQuantity: Int?,
        maxQuantity: Int?,
    ): Flow<List<NodeWithCounts>>

    /**
     * LIVE list of the raw stored category text of every node that has any,
     * e.g. ["|Clothing|Winter|", "|Clothing|", "|Tools|"].
     * NodeRepository splits and de-duplicates these into the menu of
     * categories shown in search filters and as suggestions in the editor.
     */
    @Query("SELECT categories FROM nodes WHERE categories != ''")
    fun observeAllCategoryText(): Flow<List<String>>

    /**
     * The ids of a node AND everything nested inside it (children,
     * grandchildren, ...). Another recursive query, this time walking DOWN.
     *
     * Used to stop impossible moves: you can't move "Room" into "Closet"
     * when Closet is inside Room — that would make a loop.
     */
    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT :id
            UNION ALL
            SELECT n.id FROM nodes n JOIN subtree s ON n.parentId = s.id
        )
        SELECT id FROM subtree
        """
    )
    suspend fun getSubtreeIds(id: Long): List<Long>

    /**
     * The photo file paths of a node and everything inside it.
     * Before deleting a folder we use this to also delete those photo files
     * from storage, so deleted items don't leave pictures behind taking space.
     */
    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT :id
            UNION ALL
            SELECT n.id FROM nodes n JOIN subtree s ON n.parentId = s.id
        )
        SELECT photoPath FROM nodes
        WHERE id IN (SELECT id FROM subtree) AND photoPath IS NOT NULL
        """
    )
    suspend fun getSubtreePhotoPaths(id: Long): List<String>

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    /**
     * Saves changes to an existing node (matched by its `id`), e.g. after
     * editing its name, description, or photo.
     */
    @Update
    suspend fun update(node: Node)

    /**
     * MOVE = change just the parentId. Everything inside the moved node
     * comes along automatically because their parentId still points at it.
     * `newParentId = null` moves it to the Home screen.
     * `now` = the current time, saved as the node's new `updatedAt`.
     */
    @Query("UPDATE nodes SET parentId = :newParentId, updatedAt = :now WHERE id = :id")
    suspend fun move(id: Long, newParentId: Long?, now: Long)

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    /**
     * Deletes one node. Because of the CASCADE rule in Node.kt, SQLite also
     * deletes everything nested inside it, all the way down.
     */
    @Delete
    suspend fun delete(node: Node)
}
