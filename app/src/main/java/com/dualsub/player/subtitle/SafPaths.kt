package com.dualsub.player.subtitle

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

/** Helpers for folders reached through the Storage Access Framework (the system folder picker). */
object SafPaths {

    /**
     * A video chosen from external storage has an id like "primary:Movies/Show/ep1.mkv".
     * The id of its folder is everything before the last "/". Returns null for ids we cannot read.
     */
    fun parentDocumentId(context: Context, videoUri: Uri): String? {
        if (!DocumentsContract.isDocumentUri(context, videoUri)) return null
        val docId = DocumentsContract.getDocumentId(videoUri)
        return when {
            docId.contains('/') -> docId.substringBeforeLast('/')
            docId.contains(':') -> docId.substringBefore(':') + ":" // the video is in the root of the storage
            else -> null
        }
    }

    /**
     * Resolves the video's parent folder within the user's selected tree. Prefer the video
     * document id when both URIs use the same provider. Otherwise use its real path (when the
     * provider exposes it) to map a MediaStore-picked video into the selected storage tree.
     */
    fun parentDocumentId(context: Context, videoUri: Uri, treeUri: Uri): String? {
        val rootId = treeRootId(treeUri)
        val path = videoFilePath(context, videoUri)
        if (path != null) {
            StoragePaths.parentDocumentIdWithinTree(rootId, path)?.let { return it }
        }
        if (videoUri.authority == treeUri.authority) {
            return parentDocumentId(context, videoUri)
        }
        return null
    }

    fun treeRootId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    /** Names and URIs of the files directly inside a folder of the tree. Empty when the folder is not reachable. */
    fun listChildren(context: Context, treeUri: Uri, parentDocId: String): List<Pair<String, Uri>> {
        return try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val result = ArrayList<Pair<String, Uri>>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    result.add(name to DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                }
            }
            result
        } catch (_: Exception) {
            emptyList() // not inside the chosen folder, or the provider says no
        }
    }

    /** Writes a file into a folder of the tree. An existing file with the same name is replaced. */
    fun write(context: Context, treeUri: Uri, parentDocId: String, name: String, bytes: ByteArray): Uri? {
        return try {
            val existing = listChildren(context, treeUri, parentDocId)
                .firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
            // "application/octet-stream" makes the provider keep our file name exactly.
            val target = existing ?: DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId),
                "application/octet-stream",
                name
            ) ?: return null
            val output = context.contentResolver.openOutputStream(target, "wt") ?: return null
            output.use { it.write(bytes) }
            target
        } catch (_: Exception) {
            null
        }
    }

    /** True when we kept write access to [treeUri] (it was chosen with the folder picker). */
    fun hasWritePermission(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }

    /**
     * Real file path of the video, when we can work it out: a file:// URI, a picked document
     * on the device storage or an SD card, or a MediaStore video. Null otherwise.
     * Whether the path can be read or written depends on "All files access".
     */
    fun videoFilePath(context: Context, uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.path
        "content" -> pathFromDocument(context, uri) ?: mediaStorePath(context, uri)
        else -> null
    }

    private fun pathFromDocument(context: Context, uri: Uri): String? {
        if (uri.authority != StoragePaths.EXTERNAL_STORAGE_AUTHORITY) return null
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        return StoragePaths.fromExternalStorageDocId(DocumentsContract.getDocumentId(uri))
    }

    /** Real file path of a MediaStore video, when the system still tells us. */
    @Suppress("DEPRECATION")
    fun mediaStorePath(context: Context, uri: Uri): String? = try {
        val queryUri = mediaStoreUri(context, uri)
        context.contentResolver.query(queryUri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** The system picker's "Videos" list gives document URIs. They are turned into MediaStore URIs. */
    private fun mediaStoreUri(context: Context, uri: Uri): Uri {
        if (uri.authority == StoragePaths.MEDIA_DOCUMENTS_AUTHORITY && DocumentsContract.isDocumentUri(context, uri)) {
            StoragePaths.mediaVideoId(DocumentsContract.getDocumentId(uri))?.let { id ->
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return uri
    }
}
