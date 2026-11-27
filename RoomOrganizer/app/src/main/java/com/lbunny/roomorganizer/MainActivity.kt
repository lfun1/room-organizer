package com.lbunny.roomorganizer

// =============================================================================
// MainActivity.kt  —  the app's single screen container
// =============================================================================
// Android opens this Activity when you tap the app icon (AndroidManifest.xml
// says so). All it does is draw the Compose UI:
//
//     RoomOrganizerTheme      colors, fonts, smooth shapes (ui/theme/)
//       └─ Surface            fills the window with the background color
//            └─ AppNavigation  shows Home / folder / details screens (ui/navigation/)
//
// CHANGED from the template: the "Hello Android!" Greeting and its outer
// Scaffold were removed. Each of our screens (BrowserScreen, DetailsScreen)
// now has its own Scaffold with its own top bar.
// =============================================================================

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lbunny.roomorganizer.ui.navigation.AppNavigation
import com.lbunny.roomorganizer.ui.theme.RoomOrganizerTheme

class MainActivity : ComponentActivity() {

    /** Runs when the app opens (and again after e.g. rotating the phone). */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status bar / gesture bar for a modern full-screen
        // look. Each screen's Scaffold adds padding so nothing is hidden.
        enableEdgeToEdge()

        // setContent { } = "from now on, this Activity shows this Compose UI".
        setContent {
            RoomOrganizerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
