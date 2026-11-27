package com.lbunny.roomorganizer.ui

// =============================================================================
// NodeActionsViewModel.kt  —  the actions BOTH screens share
// =============================================================================
//
// WHAT IS A VIEWMODEL?
//   A ViewModel holds a screen's data and does its work (talking to the
//   repository). The screen itself only DRAWS what the ViewModel has and
//   reports taps back to it. Two benefits:
//     1. Survives rotation: when you rotate the phone, Android rebuilds the
//        screen, but the ViewModel (and its data) stays alive.
//     2. Separation: drawing code and data code don't get tangled together.
//
// WHY A SHARED "BASE" CLASS?
//   The grid screen (BrowserViewModel) and the details screen
//   (DetailsViewModel) both need create / edit / move / delete. Writing them
//   once here and having both screens' ViewModels "extend" this class
//   (`: NodeActionsViewModel(...)`) avoids copy-pasting. New shared actions in
//   later versions (e.g. v2 "autofill from photo") can be added here once.
//
//   `open class` = other classes are allowed to extend it (Kotlin classes are
//   closed by default).
// =============================================================================

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lbunny.roomorganizer.data.BackupLocation
import com.lbunny.roomorganizer.data.Node
import com.lbunny.roomorganizer.data.NodeFields
import com.lbunny.roomorganizer.data.PhotoBackupResult
import com.lbunny.roomorganizer.data.NodeRepository
import com.lbunny.roomorganizer.data.NodeType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which pop-up dialog is currently open, if any.
 *
 * `sealed interface` = a fixed list of possibilities. A `when (dialog)` can
 * check every case, and Kotlin warns us if we forget one.
 */
sealed interface NodeDialog {
    /** "New folder" / "New item" form. [parentId] = where to create it (null = Home). */
    data class Create(val parentId: Long?, val type: NodeType) : NodeDialog

    /** Edit an existing node's fields (name, description, categories, quantity, condition) and photo. */
    data class Edit(val node: Node) : NodeDialog

    /** "Move to…" folder tree picker. */
    data class Move(val node: Node) : NodeDialog

    /** "Are you sure?" delete warning. */
    data class Delete(val node: Node) : NodeDialog
}

/**
 * @param repository the shared data layer. `protected` = visible to
 *                   subclasses (Browser/Details ViewModels) but not to screens.
 */
open class NodeActionsViewModel(
    protected val repository: NodeRepository,
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Shared live data
    // -------------------------------------------------------------------------

    /**
     * Every folder, for the "Move to…" tree.
     *
     * `.stateIn(...)` turns the database Flow into a StateFlow: a Flow that
     * always remembers its latest value, so a screen can read it instantly.
     *   viewModelScope          = keep it running while this ViewModel lives
     *   WhileSubscribed(5_000)  = pause the database query 5 seconds after the
     *                             screen stops watching (saves battery), but
     *                             not during a quick rotation
     *   emptyList()             = the value to show before the first result
     */
    val allFolders: StateFlow<List<Node>> = repository.observeAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * NEW in v1: every category already in use, A→Z, e.g. [Clothing, Tools].
     * The create/edit form shows these as tap-to-add suggestions, so you
     * reuse "Clothing" instead of accidentally making "Clothes" too.
     */
    val allCategories: StateFlow<List<String>> = repository.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------------------------------------------------------------
    // One-time messages ("Moved Closet to Room") shown in a snackbar
    // -------------------------------------------------------------------------
    // A Channel is a queue: each message is delivered to the screen exactly
    // once (so rotating the phone doesn't re-show an old message).
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** The screen listens to this and shows each message at the bottom. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    protected fun showMessage(text: String) {
        _messages.trySend(text)
    }

    // -------------------------------------------------------------------------
    // Actions. Each one starts a background job with `viewModelScope.launch`
    // (database/file work must not run on the main drawing thread). The job is
    // automatically cancelled if the ViewModel is destroyed.
    // -------------------------------------------------------------------------

    // CHANGED in v1: create() and edit() take one NodeFields bundle (name,
    // description, categories, quantity, condition) instead of separate
    // name/description values. See NodeFields in NodeRepository.kt.

    fun create(parentId: Long?, type: NodeType, fields: NodeFields, photoUri: Uri?) {
        viewModelScope.launch {
            // runCatching = "try this; if it throws an error, don't crash —
            // hand me the error instead" (e.g. a photo that can't be read).
            runCatching { repository.create(parentId, type, fields, photoUri, ::reportPhotoBackup) }
                .onFailure { showMessage("Couldn't save: ${it.message}") }
        }
    }

    fun edit(original: Node, fields: NodeFields, newPhotoUri: Uri?, removePhoto: Boolean) {
        viewModelScope.launch {
            runCatching { repository.edit(original, fields, newPhotoUri, removePhoto, ::reportPhotoBackup) }
                .onFailure { showMessage("Couldn't save: ${it.message}") }
        }
    }

    /**
     * NEW: after saving a CAMERA photo, say where its Gallery backup went
     * (see PhotoBackup.kt). Shown in the snackbar at the bottom.
     */
    private fun reportPhotoBackup(result: PhotoBackupResult) {
        when (result) {
            is PhotoBackupResult.Saved -> showMessage(
                when (result.location) {
                    BackupLocation.SD_CARD -> "Photo backed up to SD card › Pictures › RoomOrganizer"
                    BackupLocation.PHONE_STORAGE ->
                        "No SD card found, so the photo was backed up to phone storage › Pictures › RoomOrganizer"
                }
            )
            is PhotoBackupResult.Failed -> showMessage("Saved, but couldn't back up the photo to the Gallery: ${result.reason}")
            PhotoBackupResult.NotSupported -> Unit  // very old Android: nothing to say
        }
    }

    /**
     * Moves [node] into the folder [newParentId] (null = Home).
     * @param destinationName only used for the confirmation message.
     */
    fun move(node: Node, newParentId: Long?, destinationName: String) {
        if (node.parentId == newParentId) return  // already there; nothing to do
        viewModelScope.launch {
            val moved = repository.move(node.id, newParentId)
            showMessage(
                if (moved) "Moved \"${node.name}\" to $destinationName"
                else "Can't move \"${node.name}\" into $destinationName"
            )
        }
    }

    fun delete(node: Node) {
        viewModelScope.launch {
            repository.delete(node)
            showMessage("Deleted \"${node.name}\"")
        }
    }

    /** Ids of a node + everything inside it (illegal Move destinations). */
    suspend fun subtreeIds(nodeId: Long): Set<Long> = repository.subtreeIds(nodeId)

    /** A fresh file address for the camera to save a new photo into. */
    fun newCameraPhotoUri(): Uri = repository.newCameraPhotoUri()
}
