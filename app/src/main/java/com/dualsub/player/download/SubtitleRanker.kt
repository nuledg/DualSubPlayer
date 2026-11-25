package com.dualsub.player.download

import kotlin.math.min

data class RankedCandidate(val candidate: SubtitleCandidate, val score: Int, val seasonPack: Boolean)

/** Puts search results in order of how well they fit the video. */
object SubtitleRanker {

    /**
     * Rules:
     * - Series: season and episode must match. A full-season pack of the right season is kept with a small penalty.
     * - Movies: results that are clearly TV episodes are dropped.
     * - Score for matching resolution, codec, source and release group, plus small bonuses and penalties.
     * The list is sorted by score, best first.
     */
    fun rank(wanted: ReleaseInfo, candidates: List<SubtitleCandidate>): List<RankedCandidate> =
        candidates.mapNotNull { score(wanted, it) }.sortedByDescending { it.score }

    fun score(wanted: ReleaseInfo, c: SubtitleCandidate): RankedCandidate? {
        val releaseInfo = ReleaseNameParser.parse(c.releaseName)
        val fileInfo = c.downloadFileName?.let(ReleaseNameParser::parse)
        // SubDL's unpacked file name can carry the actual release tags when release_name is broad.
        val info = releaseInfo.copy(
            year = releaseInfo.year ?: fileInfo?.year,
            season = releaseInfo.season ?: fileInfo?.season,
            episode = releaseInfo.episode ?: fileInfo?.episode,
            resolution = releaseInfo.resolution ?: fileInfo?.resolution,
            codec = releaseInfo.codec ?: fileInfo?.codec,
            source = releaseInfo.source ?: fileInfo?.source,
            group = releaseInfo.group ?: fileInfo?.group,
            bitDepth = releaseInfo.bitDepth ?: fileInfo?.bitDepth
        )
        val season = c.season ?: info.season
        val episode = c.episode ?: info.episode

        var seasonPack = false
        var score = 0

        if (wanted.isSeries) {
            if (season != wanted.season) return null
            if (c.fullSeason) {
                // A pack is never treated as a single exact episode, even if the API reports
                // episode=1 as its first contained episode.
                seasonPack = true
                score -= 20
            } else if (episode != wanted.episode) {
                return null
            }
        } else {
            // A movie. Results for a single episode are for another title.
            if (season != null && episode != null) return null
        }

        if (wanted.resolution != null && wanted.resolution == info.resolution) score += 30
        if (wanted.codec != null && wanted.codec == info.codec) score += 20
        if (wanted.bitDepth != null && wanted.bitDepth == info.bitDepth) score += 12
        if (wanted.source != null && wanted.source == info.source) score += 25
        if (wanted.group != null && info.group != null && wanted.group.equals(info.group, ignoreCase = true)) score += 35

        // Same title? Count how many words of our title appear in the release name.
        val titleWords = words(wanted.title)
        if (titleWords.isNotEmpty()) {
            val inRelease = words("${c.releaseName} ${c.downloadFileName.orEmpty()}").toSet()
            val ratio = titleWords.count { it in inRelease }.toDouble() / titleWords.size
            score += (20 * ratio).toInt()
            if (ratio < 0.5) score -= 100 // most likely another title
        }

        val wantedYear = wanted.year
        val candidateYear = c.year ?: info.year
        if (wantedYear != null && candidateYear != null) {
            when (Math.abs(wantedYear - candidateYear)) {
                0 -> score += 10
                1 -> score -= 15
                else -> score -= 120 // another film with the same name (remake)
            }
        }

        c.downloads?.let { score += min(it / 500, 10) }
        if (c.hearingImpaired) score -= 5

        return RankedCandidate(c, score, seasonPack)
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("""[^a-z0-9\u0600-\u06FF]+""")).filter { it.isNotEmpty() }
}
