package com.lbunny.roomorganizer.ui.components

// =============================================================================
// NodeEditorDialog.kt  —  the "New folder / New item / Edit" form
// =============================================================================
//
//   ┌────────────────────────────────────┐
//   │ New item                           │
//   │ [ Name *                    ]      │
//   │ [ Description               ]      │
//   │ Categories                         │   ← NEW in v1
//   │ (Clothing ✕) (Winter ✕)            │     chips you've added
//   │ [ Add a category        ] [Add]    │
//   │ Suggestions: (+ Tools) (+ Shoes)   │     categories used elsewhere
//   │ [ − ] [ Qty: 2          ] [ + ]    │   ← NEW in v1: quantity
//   │ [ Good ▾ ]                         │   ← NEW in v1: condition
//   │ [        photo preview        ]    │
//   │ (🖼 Gallery) (📷 Camera)            │
//   │ Remove photo                       │
//   │                    Cancel   Save   │
//   └────────────────────────────────────┘
//
// The same form is used for creating AND editing: when editing, it starts
// pre-filled with the node's current values (the `initial` NodeFields).
//
// PICKING PHOTOS uses "activity result launchers": a standard way to open
// ANOTHER screen/app (the photo picker, the camera) and get its answer back.
//   rememberLauncherForActivityResult(contract) { result -> ... }
//     contract = what kind of request (pick an image / take a picture)
//     { result -> } = runs when the user comes back with an answer
//
// CHANGED in v1: categories, quantity, and condition fields; the form now
// starts from and returns a NodeFields bundle instead of separate values.
//
// KEYBOARD (fix after v1 testing):
//   Name, Category, Qty → the keyboard shows "Done", which closes it.
//   Description         → keeps the Enter key for new lines.
//   Tapping any empty space in the form closes the keyboard from ANY box
//   (see KeyboardHelpers.kt).
// =============================================================================

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lbunny.roomorganizer.data.ItemCondition
import com.lbunny.roomorganizer.data.NodeFields
import com.lbunny.roomorganizer.ui.theme.AppShapes
import java.io.File

/** The largest quantity the form accepts (6 digits), to keep the number sane. */
private const val MAX_QUANTITY = 999_999

/**
 * @param title               e.g. "New folder", "Edit item"
 * @param confirmLabel        e.g. "Create", "Save"
 * @param initial             CHANGED in v1: the starting values of ALL fields
 *                            (empty when creating, current values when editing)
 * @param existingPhotoPath   the node's current photo file (editing only)
 * @param categorySuggestions NEW in v1: categories already used elsewhere
 * @param newCameraPhotoUri   gives us a file address for the camera to save into
 * @param onSave              called with the final values when the user taps the
 *                            confirm button:
 *                              fields      = name, description, categories, quantity, condition
 *                              newPhotoUri = a newly picked photo (or null)
 *                              removePhoto = true if they tapped "Remove photo"
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeEditorDialog(
    title: String,
    confirmLabel: String,
    initial: NodeFields,
    existingPhotoPath: String?,
    categorySuggestions: List<String>,
    newCameraPhotoUri: () -> Uri,
    onDismiss: () -> Unit,
    onSave: (fields: NodeFields, newPhotoUri: Uri?, removePhoto: Boolean) -> Unit,
) {
    // ---- Form state ---------------------------------------------------------
    // `rememberSaveable` is like `remember`, but ALSO survives Android
    // temporarily closing the app in the background (e.g. while the camera app
    // is open), so what the user typed isn't lost while they take a photo.
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var description by rememberSaveable { mutableStateOf(initial.description) }

    // NEW in v1 ---------------------------------------------------------------
    // Categories are kept as an ArrayList because that's a type Android knows
    // how to save (rememberSaveable can't save every kind of list).
    var categories by rememberSaveable { mutableStateOf(ArrayList(initial.categories)) }
    var newCategoryText by rememberSaveable { mutableStateOf("") }
    // Quantity is edited as TEXT (what's in the box) and converted to a number
    // on save. "" = not counted.
    var quantityText by rememberSaveable { mutableStateOf(initial.quantity?.toString() ?: "") }
    var condition by rememberSaveable { mutableStateOf(initial.condition) }
    // -------------------------------------------------------------------------

    var newPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var removePhoto by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    // Hides the keyboard (see KeyboardHelpers.kt). Used by the Done keys below.
    val dismissKeyboard = rememberDismissKeyboard()

    /** Adds a category chip (ignoring blanks and duplicates, any upper/lower case). */
    fun addCategory(raw: String) {
        val category = raw.replace("|", "").trim()
        if (category.isEmpty()) return
        if (categories.none { it.equals(category, ignoreCase = true) }) {
            // Make a NEW list (instead of changing the old one) so Compose
            // notices the change and redraws the chips.
            categories = ArrayList(categories + category)
        }
        newCategoryText = ""
    }

    // ---- Photo launchers ----------------------------------------------------
    // Gallery: Android's Photo Picker. Returns the chosen photo's Uri, or null
    // if the user backed out.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            newPhotoUri = uri
            removePhoto = false
        }
    }

    // Camera: we give the camera app a file address; it returns true if it
    // saved a picture there.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved) {
            newPhotoUri = pendingCameraUri
            removePhoto = false
        }
    }

    // What to show in the preview: a newly picked photo wins; otherwise the
    // existing photo (unless the user removed it); otherwise nothing.
    val previewModel: Any? = newPhotoUri
        ?: existingPhotoPath?.takeIf { !removePhoto }?.let { File(it) }

    // ---- The dialog ---------------------------------------------------------
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // verticalScroll = if the keyboard makes the space small, the form
            // scrolls instead of being cut off.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    // FIX: tap any empty space in the form to hide the keyboard.
                    .dismissKeyboardOnTap()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        // Auto-capitalize the first letter of each word: "Rain Jacket"
                        capitalization = KeyboardCapitalization.Words,
                        // The keyboard key says "Done" and closes the keyboard.
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    // A multi-line box: the keyboard keeps its Enter key, which
                    // starts a new line. To close the keyboard here, tap any
                    // empty space in the form (dismissKeyboardOnTap above).
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- NEW in v1: categories -------------------------------------
                CategoriesEditor(
                    categories = categories,
                    newCategoryText = newCategoryText,
                    onNewCategoryTextChange = { text ->
                        // Typing a comma adds what came before it, so you can
                        // type "Clothing, Winter," quickly.
                        if (text.contains(',')) {
                            text.split(',').dropLast(1).forEach { addCategory(it) }
                            newCategoryText = text.substringAfterLast(',')
                        } else {
                            newCategoryText = text
                        }
                    },
                    onAdd = { addCategory(newCategoryText) },
                    // FIX: the keyboard's Done key adds what's typed AND closes
                    // the keyboard. (Before, our custom Done action replaced
                    // Android's default "close the keyboard" behavior.)
                    onDone = { addCategory(newCategoryText); dismissKeyboard() },
                    onRemove = { removed -> categories = ArrayList(categories - removed) },
                    suggestions = categorySuggestions,
                )

                // ---- NEW in v1: quantity, then condition -----------------------
                // (Each on its own row: side by side is too cramped on a phone.)
                QuantityField(
                    text = quantityText,
                    onTextChange = { quantityText = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ConditionPicker(
                    condition = condition,
                    onConditionChange = { condition = it },
                )

                // ---- Photo -------------------------------------------------------
                if (previewModel != null) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = "Photo preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(AppShapes.photo),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery")
                    }
                    OutlinedButton(
                        onClick = {
                            // Make a fresh file for the camera, remember it, launch.
                            val uri = newCameraPhotoUri()
                            pendingCameraUri = uri
                            try {
                                cameraLauncher.launch(uri)
                            } catch (e: ActivityNotFoundException) {
                                // No camera app installed (rare; e.g. some emulators).
                                Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Camera")
                    }
                }

                if (previewModel != null) {
                    TextButton(onClick = { newPhotoUri = null; removePhoto = true }) {
                        Text("Remove photo")
                    }
                }
            }
        },
        confirmButton = {
            // Disabled until a name is typed (name is required).
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    // A category typed but not yet "Added" is included too,
                    // so it isn't silently lost.
                    val finalCategories = (categories + newCategoryText)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSave(
                        NodeFields(
                            name = name,
                            description = description,
                            categories = finalCategories,
                            // "" or anything not a number → null (not counted)
                            quantity = quantityText.toIntOrNull(),
                            condition = condition,
                        ),
                        newPhotoUri,
                        removePhoto,
                    )
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// =============================================================================
// NEW in v1: the pieces of the form for the new fields
// =============================================================================

/**
 * Categories: the chips already added (tap ✕ to remove), a text box + Add
 * button, and suggestions from categories used elsewhere (tap to add).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesEditor(
    categories: List<String>,
    newCategoryText: String,
    onNewCategoryTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    onRemove: (String) -> Unit,
    suggestions: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Categories", style = MaterialTheme.typography.labelLarge)

        // Chips already on this node. FlowRow = wrap onto new lines as needed.
        if (categories.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { category ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(category) },
                        label = { Text(category) },
                        trailingIcon = {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove $category", modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }

        // Text box to type a new one. The ＋ button adds it and keeps the
        // keyboard open (to add more); the keyboard's Done key adds it AND
        // closes the keyboard.
        OutlinedTextField(
            value = newCategoryText,
            onValueChange = onNewCategoryTextChange,
            placeholder = { Text("Add a category") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailingIcon = {
                IconButton(onClick = onAdd, enabled = newCategoryText.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add category")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Suggestions: existing categories not on this node yet. While typing,
        // only those containing the typed text. At most 8, to keep it short.
        val shown = suggestions
            .filter { s -> categories.none { it.equals(s, ignoreCase = true) } }
            .filter { s -> newCategoryText.isBlank() || s.contains(newCategoryText.trim(), ignoreCase = true) }
            .take(8)
        if (shown.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onNewCategoryTextChange("$suggestion,") },  // the comma adds it
                        label = { Text("+ $suggestion") },
                    )
                }
            }
        }
    }
}

/**
 * Quantity: a number-only text box with − and + buttons on either side.
 * Empty = "not counted".
 */
@Composable
private fun QuantityField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = text.toIntOrNull()
    val dismissKeyboard = rememberDismissKeyboard()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconButton(
            // Going below 1 clears the field (back to "not counted").
            onClick = { onTextChange(if (current == null || current <= 1) "" else "${current - 1}") },
            enabled = current != null,
        ) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease quantity") }

        OutlinedTextField(
            value = text,
            // Keep only digits, at most 6 of them.
            onValueChange = { typed -> onTextChange(typed.filter { it.isDigit() }.take(6)) },
            label = { Text("Qty") },
            singleLine = true,
            // Number keypad, with a Done key that closes it.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = { onTextChange("${((current ?: 0) + 1).coerceAtMost(MAX_QUANTITY)}") },
        ) { Icon(Icons.Outlined.Add, contentDescription = "Increase quantity") }
    }
}

/** Condition: a button showing the current choice; tap for a menu of all of them. */
@Composable
private fun ConditionPicker(
    condition: ItemCondition?,
    onConditionChange: (ItemCondition?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(condition?.label ?: "Condition")
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Not set") },
                onClick = { open = false; onConditionChange(null) },
            )
            // ItemCondition.entries = every condition, in the order listed in Node.kt
            ItemCondition.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { open = false; onConditionChange(option) },
                )
            }
        }
    }
}
