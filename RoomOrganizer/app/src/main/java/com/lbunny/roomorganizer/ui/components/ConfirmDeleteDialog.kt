package com.lbunny.roomorganizer.ui.components

// =============================================================================
// ConfirmDeleteDialog.kt  —  the "Are you sure?" warning before deleting
// =============================================================================
// Deleting can't be undone, and deleting a folder also deletes EVERYTHING
// inside it (sub-folders, their items, and all of their photos). So we always
// ask first, and say clearly what will be lost.
// =============================================================================

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeType

@Composable
fun ConfirmDeleteDialog(
    node: Node,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message = if (node.type == NodeType.FOLDER) {
        "This folder and EVERYTHING inside it — all sub-folders, items, and photos — " +
            "will be permanently deleted. This can't be undone."
    } else {
        "This item and its photo will be permanently deleted. This can't be undone."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete \"${node.name}\"?") },
        text = { Text(message) },
        confirmButton = {
            // Red "Delete" button so it's clearly the dangerous choice.
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
