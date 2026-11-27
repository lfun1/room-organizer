package com.lbunny.roomorganizer.ui.navigation

// =============================================================================
// AppNavigation.kt  —  which screen is showing, and moving between them
// =============================================================================
//
// Navigation Compose works like a web browser's history:
//   - Each screen has a "route", like a web address:
//         browser?folderId=-1    → Home
//         browser?folderId=7     → the folder with id 7
//         details/3              → details of node 3 (swipeable, v1)
//         search?category=Tools  → search, pre-filtered (v1)
//   - navigate(route) PUSHES a new screen on top of the "back stack".
//   - Back (button or swipe gesture) POPS the top screen off, revealing the
//     previous one exactly as it was.
//
//   Example back stack after: Home → open Room → open Closet → tap Rain Jacket
//       [ Home, Room, Closet, details(Rain Jacket) ]   ← Back pops from the right
//
// Why -1 for Home? Route arguments of type Long can't be null, so we use -1
// (an id SQLite never assigns) as a stand-in for "no folder = Home".
// =============================================================================

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lbunny.roomorganizer.ui.browser.BrowserScreen
import com.lbunny.roomorganizer.ui.details.DetailsPagerScreen
import com.lbunny.roomorganizer.ui.search.SearchScreen

/** All route names and builders in one place, so they can't get mistyped. */
private object Routes {
    const val HOME_ID = -1L

    const val ARG_FOLDER_ID = "folderId"
    const val ARG_NODE_ID = "nodeId"
    const val ARG_CATEGORY = "category"   // NEW in v1

    // Route PATTERNS (with {placeholders}) that screens are registered under.
    const val BROWSER = "browser?$ARG_FOLDER_ID={$ARG_FOLDER_ID}"
    const val DETAILS = "details/{$ARG_NODE_ID}"
    // NEW in v1: search, optionally pre-filtered by a category.
    //   "search"                  → empty search
    //   "search?category=Clothing" → already filtered to Clothing
    const val SEARCH = "search?$ARG_CATEGORY={$ARG_CATEGORY}"

    // Route BUILDERS that fill the placeholders in with real values.
    fun browser(folderId: Long?) = "browser?$ARG_FOLDER_ID=${folderId ?: HOME_ID}"
    fun details(nodeId: Long) = "details/$nodeId"
    // Uri.encode makes any text safe inside a route, e.g. a category with
    // spaces or "&" ("Arts & Crafts" → "Arts%20%26%20Crafts").
    fun search(category: String? = null) =
        if (category == null) "search" else "search?$ARG_CATEGORY=${Uri.encode(category)}"
}

/**
 * The whole app's screen switcher. MainActivity shows this inside the theme.
 */
@Composable
fun AppNavigation() {
    // The controller remembers the back stack and performs navigation.
    val navController = rememberNavController()

    // Shared navigation actions, passed down to every screen.
    val openFolder: (Long) -> Unit = { id -> navController.navigate(Routes.browser(id)) }
    val openDetails: (Long) -> Unit = { id -> navController.navigate(Routes.details(id)) }
    val navigateTo: (Long?) -> Unit = { id -> navController.navigateToFolder(id) }
    val navigateUp: () -> Unit = { navController.navigateUp() }
    // NEW in v1
    val openSearch: () -> Unit = { navController.navigate(Routes.search()) }
    val searchCategory: (String) -> Unit = { category -> navController.navigate(Routes.search(category)) }

    // NavHost = the area that shows the current screen. startDestination =
    // the first screen (Home, since folderId defaults to -1).
    NavHost(navController = navController, startDestination = Routes.BROWSER) {

        // ---- Home / folder grid -------------------------------------------
        composable(
            route = Routes.BROWSER,
            arguments = listOf(
                navArgument(Routes.ARG_FOLDER_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.HOME_ID
                }
            ),
        ) { backStackEntry ->
            // Read the folder id from the route; turn -1 back into null (Home).
            val raw = backStackEntry.arguments?.getLong(Routes.ARG_FOLDER_ID) ?: Routes.HOME_ID
            val folderId: Long? = raw.takeIf { it != Routes.HOME_ID }
            BrowserScreen(
                folderId = folderId,
                onOpenFolder = openFolder,
                onOpenDetails = openDetails,
                onNavigateTo = navigateTo,
                onNavigateUp = navigateUp,
                onOpenSearch = openSearch,   // NEW in v1: the 🔍 button
            )
        }

        // ---- Details of one folder or item ----------------------------------
        // CHANGED in v1: shows the swipeable DetailsPagerScreen, which starts
        // on this node and lets you swipe to its neighbors in the same folder.
        composable(
            route = Routes.DETAILS,
            arguments = listOf(navArgument(Routes.ARG_NODE_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getLong(Routes.ARG_NODE_ID) ?: return@composable
            DetailsPagerScreen(
                startNodeId = nodeId,
                onOpenFolder = openFolder,
                onOpenDetails = openDetails,
                onNavigateTo = navigateTo,
                onNavigateUp = navigateUp,
                onSearchCategory = searchCategory,
                onOpenSearch = openSearch,   // 🔍 on details pages too
            )
        }

        // ---- NEW in v1: global search -----------------------------------------
        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument(Routes.ARG_CATEGORY) {
                    type = NavType.StringType
                    nullable = true        // no category = plain search
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            SearchScreen(
                initialCategory = backStackEntry.arguments?.getString(Routes.ARG_CATEGORY),
                onOpenDetails = openDetails,
                onOpenFolder = openFolder,
                onNavigateUp = navigateUp,
            )
        }
    }
}

/**
 * Jump to a folder from a path (breadcrumb or "Where is this?").
 *   Home   → clear the whole back stack and show a fresh Home screen, so Back
 *            then exits the app instead of walking through old screens.
 *   Folder → push that folder's screen.
 */
private fun NavHostController.navigateToFolder(folderId: Long?) {
    if (folderId == null) {
        navigate(Routes.browser(null)) {
            popUpTo(graph.id) { inclusive = true }  // remove everything first
        }
    } else {
        navigate(Routes.browser(folderId))
    }
}
