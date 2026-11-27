package com.lbunny.roomorganizer

// =============================================================================
// RoomOrganizerApp.kt  —  runs once when the app starts, before any screen
// =============================================================================
//
// An `Application` class is the app's "home base". Android creates exactly ONE
// of it when the app process starts and keeps it alive until the app is fully
// closed. That makes it the natural place to keep things that every screen
// shares — here, the repository (and through it, the database).
//
// It only takes effect once it's registered in AndroidManifest.xml with
//     android:name=".RoomOrganizerApp"
// (done in the next file).
//
// HOW SCREENS REACH IT:
//     (context.applicationContext as RoomOrganizerApp).repository
//
// When future versions add more shared services (e.g. v2's AI image
// recognizer), they get created here too, next to `repository`.
// =============================================================================

import android.app.Application
import com.lbunny.roomorganizer.data.AppDatabase
import com.lbunny.roomorganizer.data.NodeRepository

class RoomOrganizerApp : Application() {

    /**
     * The one shared repository for the whole app.
     *
     * `by lazy { ... }` = don't build it now; build it the FIRST time someone
     * uses it, then reuse that same object forever. This keeps app startup
     * fast, since the database file is only opened when first needed.
     */
    val repository: NodeRepository by lazy {
        NodeRepository(
            dao = AppDatabase.getInstance(this).nodeDao(),
            context = this,
        )
    }
}
