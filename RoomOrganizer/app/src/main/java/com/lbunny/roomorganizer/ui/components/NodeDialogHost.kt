package com.lbunny.roomorganizer.ui.components

// =============================================================================
// NodeDialogHost.kt  —  shows whichever dialog is currently open
// =============================================================================
// Each screen keeps ONE variable saying which dialog is open
// (`NodeDialog?` — see NodeActionsViewModel.kt) and puts this host at the
// bottom of its layout. The host looks at that variable and draws the right
// dialog, wired to the right ViewModel action. Both screens share this, so
// the create/edit/move/delete behavior is identical everywhere.
// =============================================================================

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeFields
import com.lbunny.roomorganizer.data.NodeType
import com.lbunny.roomorganizer.ui.NodeActionsViewModel
import com.lbunny.roomorganizer.ui.NodeDialog

/**
 * @param dialog    which dialog to show; null = none
 * @param onDismiss close the dialog (sets the screen's variable back to null)
 * @param onDeleted called after a delete, so a screen showing the deleted
 *                  node can close itself
 */
@Composable
fun NodeDialogHost(
    dialog: NodeDialog?,
    onDismiss: () -> Unit,
    viewModel: NodeActionsViewModel,
    onDeleted: (Node) -> Unit = {},
) {
    // NEW in v1: existing categories, offered as suggestions in the editor.
    val allCategories by viewModel.allCategories.collectAsState()

    // `when` picks the branch matching the dialog's type.
    when (dialog) {
        null -> Unit  // nothing open

        // CHANGED in v1: the editor now starts from a NodeFields bundle
        // (`initial = ...`), returns one (`fields`), and gets the list of
        // existing categories to suggest.

        is NodeDialog.Create -> NodeEditorDialog(
            title = if (dialog.type == NodeType.FOLDER) "New folder" else "New item",
            confirmLabel = "Create",
            initial = NodeFields(name = ""),                   // empty form
            existingPhotoPath = null,
            categorySuggestions = allCategories,
            newCameraPhotoUri = viewModel::newCameraPhotoUri,  // `::` = pass the function itself
            onDismiss = onDismiss,
            onSave = { fields, photoUri, _ ->
                viewModel.create(dialog.parentId, dialog.type, fields, photoUri)
                onDismiss()
            },
        )

        is NodeDialog.Edit -> NodeEditorDialog(
            title = if (dialog.node.type == NodeType.FOLDER) "Edit folder" else "Edit item",
            confirmLabel = "Save",
            initial = NodeFields.from(dialog.node),             // pre-filled with current values
            existingPhotoPath = dialog.node.photoPath,
            categorySuggestions = allCategories,
            newCameraPhotoUri = viewModel::newCameraPhotoUri,
            onDismiss = onDismiss,
            onSave = { fields, newPhotoUri, removePhoto ->
                viewModel.edit(dialog.node, fields, newPhotoUri, removePhoto)
                onDismiss()
            },
        )

        is NodeDialog.Move -> {
            val folders by viewModel.allFolders.collectAsState()

            // Look up (once, in the background) which folders are illegal
            // destinations. Until that finishes, `disabled` is null and we
            // don't show the dialog yet (it takes a split second).
            var disabled by remember(dialog.node.id) { mutableStateOf<Set<Long>?>(null) }
            LaunchedEffect(dialog.node.id) {
                disabled = viewModel.subtreeIds(dialog.node.id)
            }

            disabled?.let { disabledIds ->
                MoveDialog(
                    node = dialog.node,
                    folders = folders,
                    disabledIds = disabledIds,
                    onDismiss = onDismiss,
                    onMove = { newParentId, destinationName ->
                        viewModel.move(dialog.node, newParentId, destinationName)
                        onDismiss()
                    },
                )
            }
        }

        is NodeDialog.Delete -> ConfirmDeleteDialog(
            node = dialog.node,
            onDismiss = onDismiss,
            onConfirm = {
                viewModel.delete(dialog.node)
                onDismiss()
                onDeleted(dialog.node)
            },
        )
    }
}
