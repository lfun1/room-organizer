package com.lbunny.roomorganizer.data

// =============================================================================
// Node.kt  —  ONE ROW in the database = ONE folder OR ONE item
// =============================================================================
//
// BIG IDEA: folders and items are stored in the SAME table, called "nodes".
// Each row has a `parentId` that points to the folder it lives inside.
// That one column is all we need to build an unlimited nesting tree:
//
//      id | parentId | type   | name
//     ----+----------+--------+--------------
//      1  |  null    | FOLDER | Room          <- null parent = on the Home screen
//      2  |   1      | FOLDER | Closet        <- inside "Room"
//      3  |   2      | ITEM   | Rain Jacket   <- inside "Closet"
//      4  |   1      | FOLDER | Large Drawer  <- inside "Room"
//
//   - "Open a folder"  = find all rows whose parentId = that folder's id
//   - "Move"           = change one row's parentId (its contents come along
//                        automatically, because THEIR parentId still points
//                        at the moved folder)
//   - "Where is this?" = follow parentId up, step by step, until null (Home)
//
// WHY ONE TABLE instead of separate "folders" and "items" tables?
//   It keeps every feature (move, delete, search, path) written ONCE instead
//   of twice. Future fields (v1: category, quantity, condition) are added as
//   new columns; for folders they simply stay empty.
// =============================================================================

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Whether a [Node] is a folder (can contain other nodes) or an item (a leaf;
 * cannot contain anything).
 *
 * Room stores an enum as TEXT using its name ("FOLDER" / "ITEM"), so the
 * database stays readable. Using an enum instead of a true/false "isFolder"
 * column means we could add new kinds later (e.g. a "LOCATION") without
 * changing the table's layout.
 */
enum class NodeType { FOLDER, ITEM }

/**
 * NEW in v1: how worn an item (or folder) is.
 *
 * Like NodeType, Room stores this as TEXT using the name ("GOOD", "POOR"...).
 * IMPORTANT for future versions: never RENAME or DELETE an entry here, because
 * rows already saved with the old name would no longer be readable. Adding
 * NEW entries is always safe.
 *
 * @param label the friendly text shown in the app.
 * The order here is the order shown in menus: best → worst.
 */
enum class ItemCondition(val label: String) {
    NEW("New"),
    LIKE_NEW("Like new"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
    BROKEN("Broken"),
}

/**
 * `@Entity` tells Room "make a SQLite table from this class".
 * Each property below becomes one COLUMN in that table.
 *
 * `data class` = a Kotlin class whose main job is to hold values. Kotlin
 * auto-writes helpers for it, such as `copy(name = "New name")`, which makes
 * a changed copy (handy for editing).
 */
@Entity(
    tableName = "nodes",

    // ---- Foreign key: "parentId must be the id of another real node" -------
    // This is a rule SQLite itself enforces, so the tree can never point at a
    // folder that doesn't exist.
    foreignKeys = [
        ForeignKey(
            entity = Node::class,        // points at this same table (a tree!)
            parentColumns = ["id"],      // ...the parent's `id`
            childColumns = ["parentId"], // ...from this row's `parentId`

            // CASCADE = when a folder is deleted, SQLite automatically deletes
            // everything whose parentId points at it, then everything inside
            // THOSE, and so on all the way down. This is exactly example step 7:
            // deleting "Closet" also deletes "Rain Jacket".
            onDelete = ForeignKey.CASCADE,
        )
    ],

    // ---- Index on parentId ---------------------------------------------------
    // Like a book's index: lets SQLite instantly find "all children of folder
    // X" without scanning every row. We ask that question on every screen.
    indices = [Index("parentId")],
)
data class Node(
    /**
     * The unique ID number of this row.
     * `autoGenerate = true` → SQLite picks the next number (1, 2, 3, ...)
     * when we insert; we pass 0 to mean "not saved yet, please assign one".
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The id of the folder this node is inside.
     * `Long?` (the `?` means it may be null) — null means "on the Home screen".
     */
    val parentId: Long? = null,

    /** FOLDER or ITEM (see [NodeType] above). */
    val type: NodeType,

    /** Display name, e.g. "Closet" or "Rain Jacket". Required. */
    val name: String,

    /** Optional longer text. Empty string "" when the user leaves it blank. */
    @ColumnInfo(defaultValue = "")
    val description: String = "",

    /**
     * Where the photo is saved, or null if there is no photo.
     *
     * We store a FILE PATH, not the picture itself. When the user picks a
     * photo, the app copies it into its own private storage folder, and this
     * column remembers that copy's location. Copying (instead of pointing to
     * the gallery) means the photo still works even if the original is
     * deleted from the gallery, and it stays with the app across updates.
     */
    val photoPath: String? = null,

    // ---- NEW in v1: fields for filtering / searching ------------------------
    // All three are OPTIONAL, for both folders and items. Each new column has
    // a default, so when the database upgrades from v0 (AppDatabase version
    // 1 → 2), every EXISTING row automatically gets: no categories, no
    // quantity, no condition. Nothing already saved is lost or changed.

    /**
     * Zero or more categories, e.g. ["Clothing", "Winter"].
     * Stored as ONE text column ("|Clothing|Winter|") by Converters.kt.
     * `defaultValue = ""` = existing rows start with no categories.
     */
    @ColumnInfo(defaultValue = "")
    val categories: List<String> = emptyList(),

    /**
     * How many of this thing there are, e.g. 3 (pairs of socks).
     * `Int?` = may be null, meaning "not counted" (different from 0 = "none left").
     */
    val quantity: Int? = null,

    /** How worn it is (see [ItemCondition]), or null if not set. */
    val condition: ItemCondition? = null,

    /**
     * When this row was created / last changed, in milliseconds since
     * 1 Jan 1970 (the standard way computers store a moment in time).
     * Not shown in v0, but useful for v3's dashboard (e.g. "items you haven't
     * touched in a year") and costs nothing to start recording now.
     */
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
