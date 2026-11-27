package com.lbunny.roomorganizer.data

// =============================================================================
// AppDatabase.kt  —  the actual SQLite database file on the phone
// =============================================================================
//
// This class ties everything together:
//   - lists which tables exist        (`entities = [Node::class]`)
//   - gives the database a VERSION number (`version = 1`)
//   - hands out the DAO               (`nodeDao()`)
//   - opens the file "room_organizer.db" exactly once for the whole app
//
// WHERE IS THE DATA STORED?
//   /data/data/com.lbunny.roomorganizer/databases/room_organizer.db
//   This is the app's PRIVATE storage. Other apps can't read it, and it
//   survives app restarts, phone reboots, and installing new versions of the
//   app (v1, v2, ...). It is erased only if the app is uninstalled or you
//   tap "Clear storage" in Settings > Apps.
//
// HOW FUTURE VERSIONS KEEP YOUR DATA (important for v1 and later):
//   When a later version changes the table layout (e.g. v1 adds category,
//   quantity, condition), we:
//     1. bump `version = 1` to `version = 2`
//     2. add an entry to `autoMigrations` (below) — Room compares the saved
//        schema snapshots app/schemas/.../1.json and 2.json and writes the
//        upgrade SQL for us
//   On the phone, Room sees the old file is version 1, runs the upgrade once,
//   and all existing folders/items are kept.
//
//   SAFETY NET: we deliberately do NOT use "fallbackToDestructiveMigration",
//   which would silently ERASE everything if an upgrade were missing. Without
//   it, a missing upgrade crashes the app during testing, so we notice and
//   fix it before any data is lost.
// =============================================================================

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// -----------------------------------------------------------------------------
// VERSION HISTORY (keep this list going: one line per database version)
//   1 = app v0: the `nodes` table (name, description, photo, tree structure)
//   2 = app v1: + categories, quantity, condition columns
// -----------------------------------------------------------------------------
@Database(
    entities = [Node::class],  // the tables in this database (just one for now)
    version = 2,               // CHANGED in v1: 1 → 2, because Node.kt gained 3 columns
    exportSchema = true,       // save the JSON snapshot to app/schemas/ (set up in build.gradle.kts)

    // NEW in v1: the upgrade from version 1 to 2.
    // Room compares app/schemas/.../1.json (v0's layout) with the new layout
    // and writes the upgrade SQL itself. Here it generates, roughly:
    //     ALTER TABLE nodes ADD COLUMN categories TEXT NOT NULL DEFAULT ''
    //     ALTER TABLE nodes ADD COLUMN quantity INTEGER
    //     ALTER TABLE nodes ADD COLUMN condition TEXT
    // "ALTER TABLE ... ADD COLUMN" only ADDS columns; every existing row and
    // value stays exactly as it was.
    //
    // On your phone: the first time v1 opens the database, Room sees the file
    // is still version 1, runs this upgrade once, and marks it version 2.
    //
    // Future versions add one more line each, e.g. AutoMigration(from = 2, to = 3).
    // Keep the old lines too: someone upgrading straight from v0 to v3 needs
    // 1→2 AND 2→3, applied in order.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
// NEW in v1: use Converters.kt to store List<String> (categories) as text.
@TypeConverters(Converters::class)
// `abstract` = Room writes the real implementation of this class for us.
abstract class AppDatabase : RoomDatabase() {

    /** Gives the rest of the app access to the database commands in NodeDao. */
    abstract fun nodeDao(): NodeDao

    // `companion object` = things that belong to the CLASS, not to one object.
    // Here it holds the single shared database instance (a "singleton").
    companion object {
        /** The file name of the database inside the app's private storage. */
        private const val DATABASE_NAME = "room_organizer.db"

        // `@Volatile` makes sure every background thread sees the latest
        // value of INSTANCE right away (avoids two threads disagreeing).
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns THE database, opening it the first time it's needed.
         *
         * Why only one? Opening a database is slow, and two open copies of the
         * same file could step on each other. So every screen shares this one.
         *
         * `synchronized(this)` = if two parts of the app ask at the exact same
         * moment, only one gets to build it; the other waits and reuses it.
         */
        fun getInstance(context: Context): AppDatabase {
            // `?:` means "if the left side is null, do the right side instead".
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    // applicationContext = the whole app (not one screen), so
                    // the database isn't tied to a screen that might close.
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    // Hand-written upgrades (for changes too complex for
                    // autoMigrations) would be added here with .addMigrations(...)
                    .build()
                    .also { INSTANCE = it }  // remember it for next time
            }
        }
    }
}
