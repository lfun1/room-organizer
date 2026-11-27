package com.lbunny.roomorganizer.data

// =============================================================================
// PhotoBackup.kt  —  copy new CAMERA photos into the phone's Gallery
// =============================================================================
//
// WHAT IT DOES
//   Each time you take a photo with the app's Camera button, the app keeps its
//   own private copy (files/photos/, see NodeRepository) AND, thanks to this
//   file, saves a second copy where the Gallery can see it:
//
//       SD card › Pictures › RoomOrganizer › Rain Jacket.jpg
//
//   - Named after the folder/item. If the name is taken, Android adds a
//     number automatically: "Rain Jacket (1).jpg".
//   - If NO SD card is inserted, it saves to phone storage ›
//     Pictures › RoomOrganizer instead, so the backup isn't lost, and the
//     app shows a message saying so.
//   - Photos picked FROM the Gallery are not copied (they're already there).
//
// HOW: "MediaStore"
//   MediaStore is Android's shared library of photos, videos, and music. The
//   Gallery shows what's in it. Since Android 10, any app may ADD its own
//   pictures to it without asking for a permission. The steps are:
//     1. insert()        : "reserve" a new photo entry (name, folder, type)
//                          marked IS_PENDING = 1 ("still being written, hide it")
//     2. openOutputStream: write the picture's bytes into that entry
//     3. update()        : set IS_PENDING = 0 ("done, show it in the Gallery")
//   If anything fails partway, we delete the half-written entry.
//
//   Each storage device is a separate "volume" in MediaStore:
//     "external_primary" = the phone's built-in storage
//     "3732-3534"         = your SD card (its ID number)
//   We look for a volume that isn't the built-in one; that's the SD card.
//
// ANDROID VERSION
//   These MediaStore features need Android 10 (API 29) or newer. On older
//   phones the backup is simply skipped. (Your phone is far newer.)
// =============================================================================

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Where a backup ended up. */
enum class BackupLocation { SD_CARD, PHONE_STORAGE }

/** The outcome of one backup attempt, so the screen can tell the user. */
sealed interface PhotoBackupResult {
    data class Saved(val location: BackupLocation) : PhotoBackupResult
    data class Failed(val reason: String) : PhotoBackupResult
    /** Android older than 10: backups not possible, nothing to report. */
    data object NotSupported : PhotoBackupResult
}

class PhotoBackup(private val context: Context) {

    /** The Gallery folder (album) the backups go into. */
    private val galleryFolder = "Pictures/RoomOrganizer"

    /**
     * Copies [photoFile] into the Gallery, named after [title].
     * Runs file work, so call it from a background thread (the repository
     * does, inside withContext(Dispatchers.IO)).
     */
    fun backup(photoFile: File, title: String): PhotoBackupResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PhotoBackupResult.NotSupported

        // ---- Pick the SD card if there is one ----------------------------------
        val volumes: Set<String> = MediaStore.getExternalVolumeNames(context)
        val sdCardVolume = volumes.firstOrNull {
            it != MediaStore.VOLUME_EXTERNAL_PRIMARY && it != MediaStore.VOLUME_EXTERNAL
        }
        val volume = sdCardVolume ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
        val location = if (sdCardVolume != null) BackupLocation.SD_CARD else BackupLocation.PHONE_STORAGE

        // The "address" of that volume's photo collection.
        val collection = MediaStore.Images.Media.getContentUri(volume)
        val resolver = context.contentResolver

        // ---- Step 1: reserve the entry ------------------------------------------
        // ContentValues = a set of "column name → value" pairs describing the photo.
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safeFileName(title)}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")  // camera photos are JPEGs
            put(MediaStore.Images.Media.RELATIVE_PATH, galleryFolder)
            put(MediaStore.Images.Media.IS_PENDING, 1)            // hidden until finished
        }
        val entry = resolver.insert(collection, details)
            ?: return PhotoBackupResult.Failed("Android couldn't create the Gallery file")

        return try {
            // ---- Step 2: write the picture ---------------------------------------
            // `use { }` closes each stream automatically when done.
            val out = resolver.openOutputStream(entry)
                ?: return PhotoBackupResult.Failed("Couldn't open the Gallery file")
            out.use { output -> photoFile.inputStream().use { input -> input.copyTo(output) } }

            // ---- Step 3: mark it finished so the Gallery shows it ----------------
            resolver.update(
                entry,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            PhotoBackupResult.Saved(location)
        } catch (e: Exception) {
            // Something went wrong (e.g. SD card full or removed mid-copy):
            // remove the half-written entry so no broken picture is left behind.
            runCatching { resolver.delete(entry, null, null) }
            PhotoBackupResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Makes a name safe for a file: removes characters that aren't allowed in
     * file names (like / or ?), trims spaces, and limits the length.
     * "Shoes / Boots?" → "Shoes _ Boots_"
     */
    private fun safeFileName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifEmpty { "RoomOrganizer photo" }
}
