package com.lbunny.roomorganizer.ui.components

// =============================================================================
// KeyboardHelpers.kt  —  make the on-screen keyboard go away when expected
// =============================================================================
//
// HOW THE KEYBOARD WORKS IN COMPOSE
//   The keyboard is shown while a text box has "focus" (the blinking cursor).
//   To hide it, we take focus away from the text box (`clearFocus()`) and,
//   to be safe, also ask the keyboard directly to hide (`hide()`).
//
//   LocalFocusManager.current         = controls which UI element has focus
//   LocalSoftwareKeyboardController   = shows/hides the on-screen keyboard
//
// Two helpers:
//   rememberDismissKeyboard()  → a function to call, e.g. from a "Done" key
//   Modifier.dismissKeyboardOnTap()  → tap any EMPTY space in this area to
//                                       hide the keyboard
// =============================================================================

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Returns a function that hides the keyboard and removes the text cursor.
 *
 *   val dismissKeyboard = rememberDismissKeyboard()
 *   keyboardActions = KeyboardActions(onDone = { dismissKeyboard() })
 */
@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current  // may be null on some devices, hence `?.`
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
}

/**
 * Tapping an EMPTY spot inside this area hides the keyboard.
 *
 * Why only empty spots: buttons, chips, and text boxes inside the area handle
 * (and "consume") their own taps first, so this only sees taps that nothing
 * else wanted. That means tapping into ANOTHER text box still moves the cursor
 * there instead of closing the keyboard.
 */
@Composable
fun Modifier.dismissKeyboardOnTap(): Modifier {
    val dismissKeyboard = rememberDismissKeyboard()
    return this.pointerInput(dismissKeyboard) {
        detectTapGestures(onTap = { dismissKeyboard() })
    }
}
