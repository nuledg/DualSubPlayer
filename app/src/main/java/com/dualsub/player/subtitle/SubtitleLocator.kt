package com.dualsub.player.subtitle

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Finds subtitle files next to a video: movie.mkv -> movie.en.srt, movie.fa.srt.
 * Files with the same series or movie name (and the same episode or year) count too, even when
 * the release tags differ: Show.S01E02.1080p.mkv -> Show.S01E02.WEB.en.srt (see SubtitleNameMatcher).
 *
 * Android does not let an app list the folder of a content:// video freely, so we try:
 * 1) a real file path (works for file:// and for picked videos when "All files access" is on)
 * 2) the folder the user chose once (Storage Access Framework)
 * 3) the folder for downloaded subtitles (its files are checked as well)
 * 4) the user adds a file by hand (done in the UI, not here)
 */
object SubtitleLocator {

    private val EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")

    fun find(
        context: Context,
        videoUri: Uri,
        videoName: String,
        treeUri: Uri?,
        downloadTreeUri: Uri? = null
    ): List<SubtitleSource.External> {
        val base = videoName.substringBeforeLast('.', videoName)

        val candidates = ArrayList<Pair<String, Uri>>()
        candidates += listByFilePath(context, videoUri)
        if (treeUri != null) {
            SafPaths.parentDocumentId(context, videoUri, treeUri)?.let { parent ->
                candidates += SafPaths.listChildren(context, treeUri, parent)
            }
        }
        if (downloadTreeUri != null) {
            candidates += SafPaths.listChildren(context, downloadTreeUri, SafPaths.treeRootId(downloadTreeUri))
        }

        // A subtitle belongs to the video when it starts with the video name, or when it has the
        // same series/movie name (and the same episode or year). Best match first.
        return candidates
            .filter { (name, _) -> name.substringAfterLast('.', "").lowercase() in EXTENSIONS }
            .distinctBy { it.first.lowercase() }
            .mapNotNull { (name, uri) ->
                val score = SubtitleNameMatcher.score(videoName, name) ?: return@mapNotNull null
                val language = if (score == SubtitleNameMatcher.EXACT_SCORE) {
                    languageFromName(name.substring(base.length + 1))
                } else {
                    // The name still holds a title, so only look at the last few words.
                    LanguageCodes.fromFileName(name, maxTokensFromEnd = 3)
                }
                Triple(score, name, SubtitleSource.External(name, uri, language))
            }
            .sortedWith(compareByDescending<Triple<Int, String, SubtitleSource.External>> { it.first }
                .thenBy { it.second.lowercase() })
            .map { it.third }
    }

    /** "en.srt" or "Movie.2020.fa.srt" -> "en" / "fa" / null. */
    fun languageFromName(name: String): String? = LanguageCodes.fromFileName(name)

    private fun listByFilePath(context: Context, videoUri: Uri): List<Pair<String, Uri>> {
        val videoPath: String? = SafPaths.videoFilePath(context, videoUri)
        val dir = videoPath?.let { File(it).parentFile } ?: return emptyList()
        val files = try {
            dir.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()
        return files.filter { it.isFile }.map { it.name to Uri.fromFile(it) }
    }
}
