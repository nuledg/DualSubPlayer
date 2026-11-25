package com.dualsub.player.download

import java.io.IOException

/** What we ask a provider for. [languages] are two-letter codes such as "en" and "fa". */
data class SearchQuery(val release: ReleaseInfo, val languages: List<String>)

/** One search result. [ref] is whatever the provider needs to download it. */
data class SubtitleCandidate(
    val providerId: String,
    val ref: String,
    val language: String,
    val releaseName: String,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    val downloads: Int?,
    val hearingImpaired: Boolean,
    val fullSeason: Boolean,
    /** Optional actual subtitle file name, used when a provider returns unpacked season-pack files. */
    val downloadFileName: String? = null
)

class DownloadedFile(val fileName: String, val bytes: ByteArray)

/**
 * A place that offers subtitles. To add another source, implement this
 * and register it in SubtitleDownloadController.providerFor().
 * Both functions block, so call them on a background thread.
 */
interface SubtitleProvider {
    val id: String
    val displayName: String

    @Throws(IOException::class)
    fun search(query: SearchQuery): List<SubtitleCandidate>

    @Throws(IOException::class)
    fun download(candidate: SubtitleCandidate): DownloadedFile
}
