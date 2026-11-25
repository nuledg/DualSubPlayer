package com.dualsub.player.subtitle

import java.io.File

/**
 * Turns the document id of the system file picker into a real file path.
 * Pure text work, so it can be tested on a normal JVM.
 *
 * "primary:Movies/Show/ep1.mkv"   -> "/storage/emulated/0/Movies/Show/ep1.mkv"
 * "1A2B-3C4D:Videos/film.mkv"     -> "/storage/1A2B-3C4D/Videos/film.mkv"
 *
 * Reading such a path only works when the user gave "All files access" (see Settings).
 */
object StoragePaths {

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"

    fun fromExternalStorageDocId(docId: String, primaryRoot: String = "/storage/emulated/0"): String? {
        val colon = docId.indexOf(':')
        if (colon <= 0) return null
        val volume = docId.substring(0, colon)
        val relative = docId.substring(colon + 1).trim('/')
        val root = if (volume.equals("primary", ignoreCase = true)) primaryRoot else "/storage/$volume"
        return if (relative.isEmpty()) root else "$root/$relative"
    }

    /**
     * Converts a video's real path into the document id of its parent folder inside a selected
     * external-storage tree. This also supports videos returned by the MediaStore picker, whose
     * document ids (for example "video:1234") do not contain a parent directory.
     *
     * Returns null when the video is outside the selected tree or its path cannot be resolved.
     */
    fun parentDocumentIdWithinTree(treeRootDocumentId: String, videoPath: String): String? {
        val colon = treeRootDocumentId.indexOf(':')
        if (colon <= 0) return null

        return try {
            val rootPath = fromExternalStorageDocId(treeRootDocumentId)?.let { File(it).canonicalFile }
                ?: return null
            val parentPath = File(videoPath).canonicalFile.parentFile ?: return null
            val root = rootPath.path.trimEnd(File.separatorChar)
            val parent = parentPath.path
            val relative = when {
                parent == root -> ""
                parent.startsWith(root + File.separator) -> parent.removePrefix(root + File.separator)
                else -> return null
            }.replace(File.separatorChar, '/')

            val volume = treeRootDocumentId.substring(0, colon)
            val rootRelative = treeRootDocumentId.substring(colon + 1).trim('/')
            val fullRelative = listOf(rootRelative, relative).filter { it.isNotEmpty() }.joinToString("/")
            "$volume:$fullRelative"
        } catch (_: Exception) {
            null
        }
    }

    /** "video:1234" -> 1234. Null for other kinds of ids. */
    fun mediaVideoId(docId: String): Long? {
        val parts = docId.split(':')
        return if (parts.size == 2 && parts[0] == "video") parts[1].toLongOrNull() else null
    }
}
