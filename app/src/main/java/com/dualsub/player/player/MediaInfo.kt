package com.dualsub.player.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class VideoMeta(val name: String, val size: Long)

/** Reads the file name and size from any kind of URI (content, file, http, https). */
object MediaInfo {

    fun query(context: Context, uri: Uri): VideoMeta = when (uri.scheme) {
        "content" -> queryContent(context, uri)
        "file" -> {
            val file = File(uri.path ?: "")
            VideoMeta(
                name = file.name.ifEmpty { "video" },
                size = if (file.exists()) file.length() else -1L
            )
        }
        // Network streams: the size is unknown, so we use -1.
        else -> VideoMeta(Uri.decode(uri.lastPathSegment ?: "video"), -1L)
    }

    private fun queryContent(context: Context, uri: Uri): VideoMeta {
        var name: String? = null
        var size = -1L
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
            // Some providers refuse the query. We fall back to the URI itself.
        }
        return VideoMeta(name ?: uri.lastPathSegment ?: "video", size)
    }
}
