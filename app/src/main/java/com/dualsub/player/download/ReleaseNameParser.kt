package com.dualsub.player.download

/** What we can read from a video file name such as "Show.Name.S02E05.1080p.WEB-DL.x264-GROUP.mkv". */
data class ReleaseInfo(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    /** 480, 576, 720, 1080, 1440 or 2160. */
    val resolution: Int?,
    /** "h264", "hevc", "xvid" or "av1". */
    val codec: String?,
    /** "bluray", "webdl", "webrip", "hdtv", "dvd" or "hdrip". */
    val source: String?,
    val group: String?,
    /** Detected video bit depth, for example 8, 10 or 12. */
    val bitDepth: Int? = null,
    /** Original file name, retained so providers can search the exact release. */
    val originalFileName: String = ""
) {
    val isSeries: Boolean get() = season != null && episode != null

    /** "Breaking Bad  S02E05", "Inception (2010)", or the file name when no title was found. */
    val displayName: String
        get() {
            if (title.isBlank()) return originalFileName
            // A file named in all lowercase ("show_name_s01e10") is shown with capital letters.
            val shown = if (title == title.lowercase()) {
                title.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            } else {
                title
            }
            return when {
                isSeries -> "$shown  S%02dE%02d".format(season, episode)
                year != null -> "$shown ($year)"
                else -> shown
            }
        }
}

object ReleaseNameParser {

    private val EXTENSION = Regex(
        """\.(mkv|mp4|m4v|avi|mov|wmv|flv|webm|ts|m2ts|srt|ass|ssa|vtt|sub|zip)$""",
        RegexOption.IGNORE_CASE
    )

    // A release group may be separated from the release by a dash, dot or underscore.
    private val GROUP = Regex("""[-._]([A-Za-z0-9]{2,15})(?:\[[^\]]*])?$""")
    private val NOT_A_GROUP = setOf(
        "dl", "rip", "ray", "hd", "hdr", "dts", "ma", "web", "webdl", "webrip",
        "bluray", "bdrip", "brrip", "hdtv", "dvd", "dvdrip", "hdrip", "remux",
        "x264", "h264", "x265", "h265", "hevc", "avc", "xvid", "divx", "av1"
    )

    private val SEASON_EPISODE = Regex(
        """(?i)(?<![a-z0-9])s[^a-z0-9]*(\d{1,2})[^a-z0-9]*e[^a-z0-9]*(\d{1,3})(?![0-9])"""
    )
    private val X_STYLE = Regex("""(?i)(?<![0-9a-z])(\d{1,2})\s*x\s*(\d{2,3})(?![0-9a-z])""")
    private val RESOLUTION = Regex(
        """(?i)(?<![0-9a-z])(?:(2160|1440|1080|720|576|480)[pi]?|(4k|uhd))(?![0-9a-z])"""
    )

    /** Match common width x height tags, allowing punctuation/spacing around the x. */
    private val DIMENSION_RESOLUTION = Regex(
        """(?i)(?<![0-9])\d{3,4}\s*x\s*(480|576|720|1080|1440|2160)(?![0-9])"""
    )
    private val YEAR = Regex("""(?<![0-9])((?:19|20)\d{2})(?![0-9])""")
    /** Accept 10bit, 10-bit, 10 bit and the same forms for 8/12-bit video. */
    private val BIT_DEPTH = Regex("""(?i)(?<![a-z0-9])(8|10|12)\s*bit(?![a-z0-9])""")

    /** Accept x265, x.265, x-265, x_265, h264, H.264, and similar variants. */
    private val CODEC = Regex(
        """(?i)(?<![a-z0-9])(?:[xh][^a-z0-9]*26[45]|avc|hevc|xvid|divx|av1)(?![a-z0-9])"""
    )
    private val SOURCE = Regex(
        """(?i)(?<![a-z0-9])(?:blu[^a-z0-9]*ray|bd[^a-z0-9]*rip|br[^a-z0-9]*rip|web[^a-z0-9]*dl|web[^a-z0-9]*rip|hdtv|dvd[^a-z0-9]*rip|hdrip|remux|web)(?![a-z0-9])"""
    )

    fun parse(fileName: String): ReleaseInfo {
        val originalName = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val base = originalName.replace(EXTENSION, "")

        // Strip a likely release-group suffix only when it is not a technical token.
        val groupMatch = GROUP.find(base)
        val rawGroup = groupMatch?.groupValues?.get(1)
        val groupPrefix = groupMatch?.let { base.substring(0, it.range.first) }.orEmpty()
        val groupPrefixText = normalizeSeparators(groupPrefix)
        val hasTechnicalMarkerBeforeGroup = groupMatch != null && listOf(
            SEASON_EPISODE, X_STYLE, RESOLUTION, DIMENSION_RESOLUTION, BIT_DEPTH, CODEC, SOURCE
        ).any { it.containsMatchIn(groupPrefixText) }
        val group = rawGroup?.takeIf { !isTechnicalToken(it) && hasTechnicalMarkerBeforeGroup }

        // Keep a length-preserving normalized copy so regex offsets still refer to the same characters.
        // All punctuation is treated as a separator. This covers dots, dashes, underscores, brackets,
        // plus signs and unusual release-name separators instead of assuming one naming convention.
        val beforeGroup = if (group != null) groupPrefix else base
        val body = normalizeSeparators(beforeGroup)

        var season: Int? = null
        var episode: Int? = null
        var markerStart = Int.MAX_VALUE
        (SEASON_EPISODE.find(body) ?: X_STYLE.find(body))?.let { match ->
            season = match.groupValues[1].toInt()
            episode = match.groupValues[2].toInt()
            markerStart = match.range.first
        }

        val dimensionsMatch = DIMENSION_RESOLUTION.find(body)
        val resolutionMatch = RESOLUTION.find(body)
        val resolution = when {
            dimensionsMatch != null -> dimensionsMatch.groupValues[1].toInt()
            resolutionMatch != null && resolutionMatch.groupValues[2].isNotEmpty() -> 2160
            resolutionMatch != null -> resolutionMatch.groupValues[1].toInt()
            else -> null
        }
        val resolutionRange = dimensionsMatch?.range ?: resolutionMatch?.range
        val codecMatch = CODEC.find(body)
        val sourceMatch = SOURCE.find(body)
        val bitDepthMatch = BIT_DEPTH.find(body)

        val techStart = listOfNotNull(
            resolutionRange?.first, bitDepthMatch?.range?.first,
            codecMatch?.range?.first, sourceMatch?.range?.first
        ).minOrNull() ?: Int.MAX_VALUE
        val limit = minOf(markerStart, techStart)

        // The last year before the episode marker and technical tags is the release year.
        // This keeps a title such as "Blade Runner 2049 2017" intact.
        val yearMatches = YEAR.findAll(body).toList()
        val yearMatch = yearMatches.lastOrNull { it.range.first < limit }
            ?: yearMatches.firstOrNull()

        var year: Int? = yearMatch?.groupValues?.get(1)?.toInt()
        var cut = minOf(limit, yearMatch?.range?.first ?: Int.MAX_VALUE, body.length)
        var title = cleanTitle(body.substring(0, cut))
        if (title.isBlank()) {
            // A title that is itself a year (for example "2012") is not a release year.
            year = null
            cut = minOf(limit, body.length)
            title = cleanTitle(body.substring(0, cut))
        }

        return ReleaseInfo(
            title = title,
            year = year,
            season = season,
            episode = episode,
            resolution = resolution,
            codec = codecMatch?.let { normalizeCodec(it.value) },
            source = sourceMatch?.let { normalizeSource(it.value) },
            group = group,
            bitDepth = bitDepthMatch?.groupValues?.get(1)?.toIntOrNull(),
            originalFileName = originalName
        )
    }

    private fun normalizeSeparators(value: String): String =
        value.map { char ->
            if (char.isLetterOrDigit() || char == '\'' || char == '’' || char == '&') char else ' '
        }.joinToString("")

    private fun isTechnicalToken(token: String): Boolean {
        val normalized = token.lowercase().filter { it.isLetterOrDigit() }
        return normalized in NOT_A_GROUP ||
            RESOLUTION.matches(normalized) ||
            DIMENSION_RESOLUTION.matches(normalized) ||
            normalized.all { it.isDigit() }
    }

    private fun cleanTitle(raw: String): String = raw
        .map { char ->
            if (char.isLetterOrDigit() || char == '\'' || char == '’' || char == '&') char else ' '
        }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun normalizeCodec(raw: String): String? = when (raw.lowercase().filter { it.isLetterOrDigit() }) {
        "x264", "h264", "avc" -> "h264"
        "x265", "h265", "hevc" -> "hevc"
        "xvid", "divx" -> "xvid"
        "av1" -> "av1"
        else -> null
    }

    fun normalizeSource(raw: String): String? = when (raw.lowercase().filter { it.isLetterOrDigit() }) {
        "bluray", "bdrip", "brrip", "remux" -> "bluray"
        "webdl", "web" -> "webdl"
        "webrip" -> "webrip"
        "hdtv" -> "hdtv"
        "dvdrip" -> "dvd"
        "hdrip" -> "hdrip"
        else -> null
    }
}
