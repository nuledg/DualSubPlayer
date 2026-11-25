package com.dualsub.player.download

import android.content.Context
import android.net.Uri
import com.dualsub.player.data.Prefs
import com.dualsub.player.subtitle.SafPaths
import java.io.File

/**
 * Saves a downloaded subtitle as <videoBaseName>.<lang>.srt.
 *
 * Option 1 ("folder"): the folder the user chose.
 * Option 2 ("next"): next to the video. If that is not possible (for example a stream,
 * or a video we have no write access beside), we fall back to option 1 and say so.
 */
class SubtitleSaver(private val context: Context) {

    sealed class Result {
        class Saved(val uri: Uri, val fileName: String, val note: String?) : Result()

        /** The user must choose a folder first. [reason] is shown on screen. */
        class NeedFolder(val reason: String) : Result()

        class Failed(val message: String) : Result()
    }

    fun save(
        videoUri: Uri,
        videoName: String,
        language: String,
        content: ByteArray,
        mode: String,
        videoFolderTree: Uri?,
        downloadFolderTree: Uri?
    ): Result {
        val fileName = videoName.substringBeforeLast('.', videoName) + ".$language.srt"

        if (mode == Prefs.MODE_NEXT) {
            saveBesideVideo(videoUri, fileName, content, videoFolderTree)?.let {
                return Result.Saved(it, fileName, null)
            }
            // Fall back to the chosen folder.
            if (downloadFolderTree == null) {
                return Result.NeedFolder(
                    "This video cannot be written to directly. Choose a folder for your subtitles."
                )
            }
            val uri = saveInFolder(downloadFolderTree, fileName, content)
                ?: return Result.Failed("Could not save the subtitle in your folder.")
            return Result.Saved(uri, fileName, " (could not save next to the video, so it went to your folder)")
        }

        if (downloadFolderTree == null) {
            return Result.NeedFolder("Choose the folder where subtitles should be saved.")
        }
        val uri = saveInFolder(downloadFolderTree, fileName, content)
            ?: return Result.Failed("Could not save the subtitle in your folder.")
        return Result.Saved(uri, fileName, null)
    }

    private fun saveInFolder(tree: Uri, fileName: String, content: ByteArray): Uri? =
        SafPaths.write(context, tree, SafPaths.treeRootId(tree), fileName, content)

    private fun saveBesideVideo(videoUri: Uri, fileName: String, content: ByteArray, videoFolderTree: Uri?): Uri? {
        // 1) A real folder path, when the system allows writing there.
        val path = SafPaths.videoFilePath(context, videoUri)
        path?.let { File(it).parentFile }?.let { dir ->
            try {
                val file = File(dir, fileName)
                file.writeBytes(content)
                return Uri.fromFile(file)
            } catch (_: Exception) {
                // no permission, read-only storage, and so on: try the next way
            }
        }

        // 2) Through the folder the user chose once, if we have write access to it.
        if (videoFolderTree != null &&
            videoUri.authority == videoFolderTree.authority &&
            SafPaths.hasWritePermission(context, videoFolderTree)
        ) {
            val parent = SafPaths.parentDocumentId(context, videoUri) ?: return null
            return SafPaths.write(context, videoFolderTree, parent, fileName, content)
        }
        return null
    }
}
