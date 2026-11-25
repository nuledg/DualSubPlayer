package com.dualsub.player.subtitle

import com.dualsub.player.download.ReleaseNameParser

/**
 * Decides whether a subtitle file in the video's folder belongs to the video, by name.
 * No Android classes here, so it is easy to test.
 *
 * Two kinds of match:
 * 1) Exact: the subtitle starts with the full video name.   movie.mkv -> movie.en.srt
 * 2) Same title: the series or movie name (and season and episode, or year) is the same, even
 *    when the release tags differ.   Show.S01E02.1080p.BluRay-AAA.mkv -> Show.S01E02.WEB.en.srt
 *
 * A subtitle of another episode, another movie, or a sequel never matches.
 */
object SubtitleNameMatcher {

    /** Score of an exact match. Always above any same-title match, so it is listed first. */
    const val EXACT_SCORE = 1000

    private const val SAME_TITLE_SCORE = 500

    /** Words after the title that only describe the subtitle file (not another film or episode). */
    private val TAG_WORDS = setOf(
        "forced", "sdh", "cc", "hi", "default", "full", "sub", "subs", "subtitle", "subtitles",
        "hearing", "impaired", "closed", "caption", "captions", "srt", "ass", "vtt", "ssa"
    )

    /** Null when [subtitleName] does not belong to [videoName]. Otherwise a score: higher is better. */
    fun score(videoName: String, subtitleName: String): Int? {
        val videoBase = videoName.substringBeforeLast('.', videoName)
        val prefix = videoBase.lowercase() + "."
        if (subtitleName.lowercase().startsWith(prefix) && !startsAnotherTitle(subtitleName.substring(prefix.length))) {
            return EXACT_SCORE
        }

        val video = ReleaseNameParser.parse(videoName)
        val sub = ReleaseNameParser.parse(subtitleName)

        val videoTitle = tokens(video.title)
        if (videoTitle.isEmpty()) return null

        // Series: the same season and episode. Movie: the file must not name an episode.
        if (video.isSeries) {
            if (sub.season != video.season || sub.episode != video.episode) return null
        } else if (sub.isSeries) {
            return null
        }

        // Movies: two different years are two different films (remakes). Series years are
        // often written differently ("The Office US" / "The Office 2005"), so they are ignored.
        if (!video.isSeries && video.year != null && sub.year != null && video.year != sub.year) return null

        // Prefer a clean title at the start of the subtitle name. Also accept a title/episode
        // identity embedded later in the filename (for example "Subs.Slow.Horses.S03E03.fa.srt").
        // The fallback remains strict after the title so sequels and other episodes cannot match.
        val subTitle = tokens(sub.title)
        val normalTitleMatch = when {
            subTitle.isEmpty() -> video.isSeries // "S01E02.en.srt" names only the episode.
            subTitle.size < videoTitle.size -> false
            subTitle.subList(0, videoTitle.size) != videoTitle -> false
            else -> subTitle.subList(videoTitle.size, subTitle.size).all { isNoise(it) }
        }
        if (!normalTitleMatch) {
            val embeddedIdentityMatch = if (video.isSeries) {
                containsSeriesIdentity(subtitleName, videoTitle, video.season!!, video.episode!!)
            } else {
                containsMovieIdentity(
                    subtitleName,
                    videoTitle,
                    video.year,
                    sub.group?.takeIf { LanguageCodes.knownOrNull(it) == null }
                )
            }
            if (!embeddedIdentityMatch) return null
        }

        // Several files can match: prefer the one made from the same release.
        var score = SAME_TITLE_SCORE
        if (video.year != null && video.year == sub.year) score += 10
        if (video.resolution != null && video.resolution == sub.resolution) score += 20
        if (video.source != null && video.source == sub.source) score += 10
        if (video.codec != null && video.codec == sub.codec) score += 5
        val subGroup = sub.group?.takeIf { LanguageCodes.knownOrNull(it) == null }
        if (video.group != null && video.group.equals(subGroup, ignoreCase = true)) score += 40
        return score
    }

    /**
     * "Toy.Story.mkv" must not take "Toy.Story.2.en.srt". A number or an episode marker right after
     * the video name means the subtitle names another film or episode, so it is not an exact match.
     */
    private fun startsAnotherTitle(afterVideoName: String): Boolean {
        val first = afterVideoName.split('.', '_', '-', ' ', '(').firstOrNull().orEmpty()
        return first.isNotEmpty() && (first.all { it.isDigit() } || EPISODE_MARKER.matches(first))
    }

    private val EPISODE_MARKER = Regex("(?i)s\\d{1,2}e\\d{1,3}|\\d{1,2}x\\d{2,3}")

    private fun isNoise(token: String): Boolean =
        token in TAG_WORDS || LanguageCodes.knownOrNull(token) != null

    /**
     * Accept a subtitle whose name contains the full series title followed by the exact episode
     * marker, even if a release/source prefix appears before the title. The marker must occur
     * after the title, and only subtitle/release tags may sit between them.
     */
    private fun containsSeriesIdentity(
        subtitleName: String,
        videoTitle: List<String>,
        season: Int,
        episode: Int
    ): Boolean {
        val nameTokens = tokens(subtitleName.substringBeforeLast('.', subtitleName))
        val episodeIndex = nameTokens.indexOfFirst { isEpisodeToken(it, season, episode) }
        if (episodeIndex < videoTitle.size) return false

        val titleIndex = nameTokens
            .take(episodeIndex)
            .windowedOrEmpty(videoTitle.size)
            .indexOfFirst { it == videoTitle }
        if (titleIndex < 0) return false

        val titleEnd = titleIndex + videoTitle.size
        return nameTokens.subList(titleEnd, episodeIndex).all { isNoise(it) }
    }

    private fun isEpisodeToken(token: String, season: Int, episode: Int): Boolean {
        val s = season.toString()
        val e = episode.toString()
        return Regex("(?i)^s0*${Regex.escape(s)}e0*${Regex.escape(e)}$").matches(token) ||
            Regex("(?i)^0*${Regex.escape(s)}x0*${Regex.escape(e)}$").matches(token)
    }

    /**
     * Movie fallback for prefixed filenames. Everything after the complete movie title must be
     * recognizable metadata (language, matching year, resolution, codec, source, or subtitle tag).
     * This prevents a sequel such as "Toy Story 2" from matching "Toy Story".
     */
    private fun containsMovieIdentity(
        subtitleName: String,
        videoTitle: List<String>,
        videoYear: Int?,
        releaseGroup: String?
    ): Boolean {
        val nameTokens = tokens(subtitleName.substringBeforeLast('.', subtitleName))
        if (nameTokens.size < videoTitle.size) return false
        val start = nameTokens.windowedOrEmpty(videoTitle.size).indexOfFirst { it == videoTitle }
        if (start < 0) return false
        return nameTokens.drop(start + videoTitle.size).all { token ->
            isNoise(token) || isReleaseMetadata(token) ||
                token.equals(releaseGroup, ignoreCase = true) ||
                (token.toIntOrNull()?.let { year -> year in 1900..2099 && (videoYear == null || year == videoYear) } == true)
        }
    }

    private fun isReleaseMetadata(token: String): Boolean {
        val compact = token.lowercase().filter { it.isLetterOrDigit() }
        return compact in setOf(
            "480p", "576p", "720p", "1080p", "1440p", "2160p", "4k", "uhd",
            "8bit", "10bit", "12bit", "web", "webdl", "webrip", "bluray", "bdrip",
            "brrip", "hdtv", "dvdrip", "hdrip", "remux", "x264", "h264", "x265",
            "h265", "hevc", "avc", "xvid", "divx", "av1", "aac", "ac3", "dts",
            "2ch", "5", "1", "web", "dl", "rip", "ray", "psa", "hdr", "hdr10",
            "dv", "proper", "repack"
        )
    }

    private fun <T> List<T>.windowedOrEmpty(size: Int): List<List<T>> =
        if (size <= 0 || this.size < size) emptyList() else windowed(size)

    /** "Grey's Anatomy" -> [greys, anatomy]. Letters of any alphabet are kept. */
    internal fun tokens(title: String): List<String> =
        title.lowercase()
            .replace("'", "")
            .replace("\u2019", "")
            .replace("&", " and ")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
}
