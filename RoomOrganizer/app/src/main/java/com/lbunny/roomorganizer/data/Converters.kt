package com.lbunny.roomorganizer.data

// =============================================================================
// Converters.kt  —  NEW in v1: store a LIST of categories in ONE column
// =============================================================================
//
// THE PROBLEM
//   SQLite columns hold simple values: numbers, text, or nothing (NULL).
//   But a node can have SEVERAL categories, e.g. ["Clothing", "Winter"].
//   A Kotlin List<String> can't go into a column directly.
//
// THE FIX: a "TypeConverter"
//   Room calls these two functions automatically:
//     - when SAVING   a Node: List<String> → one text value  (fromCategories)
//     - when READING  a Node: one text value → List<String>  (toCategories)
//   They're switched on for the whole database in AppDatabase.kt with
//   @TypeConverters(Converters::class).
//
// THE TEXT FORMAT: each category wrapped in "|" bars
//     []                       →  ""
//     ["Clothing"]             →  "|Clothing|"
//     ["Clothing", "Winter"]   →  "|Clothing|Winter|"
//
//   Why the bars on BOTH sides? So a search can match a WHOLE category
//   exactly. The filter "Winter" looks for the text "|Winter|", which can't
//   accidentally match a category like "Winterwear" ("|Winterwear|").
//   (See searchNodes in NodeDao.kt.)
//
// WHY NOT A SEPARATE "categories" TABLE?
//   That's the textbook approach for many-to-many data, but it means
//   joins in every query and more code. A personal inventory has a handful
//   of categories per item, so one text column is simpler and fast enough.
//   If v3's dashboard ever needs heavy category statistics, we can migrate
//   to a table then; Room migrations make that safe.
// =============================================================================

import androidx.room.TypeConverter

/** The character that separates categories in the stored text. */
internal const val CATEGORY_SEPARATOR = "|"

class Converters {

    /**
     * List → stored text.
     * Also tidies the list: trims spaces, drops blanks and duplicates
     * (ignoring upper/lower case, so "winter" and "Winter" count as one),
     * and removes any "|" the user typed, since that's our separator.
     */
    @TypeConverter
    fun fromCategories(categories: List<String>): String {
        val cleaned = categories
            .map { it.replace(CATEGORY_SEPARATOR, "").trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        // An empty list is stored as "" (not "||") so "has no categories"
        // is easy to check in SQL: categories = ''
        return if (cleaned.isEmpty()) ""
        else cleaned.joinToString(
            separator = CATEGORY_SEPARATOR,
            prefix = CATEGORY_SEPARATOR,
            postfix = CATEGORY_SEPARATOR,
        )
    }

    /** Stored text → List. "|Clothing|Winter|" → ["Clothing", "Winter"] */
    @TypeConverter
    fun toCategories(stored: String): List<String> =
        stored.split(CATEGORY_SEPARATOR).filter { it.isNotBlank() }
}
