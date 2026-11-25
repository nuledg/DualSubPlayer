package com.dualsub.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleRankerTest {

    private fun candidate(
        name: String,
        language: String = "en",
        season: Int? = null,
        episode: Int? = null,
        fullSeason: Boolean = false,
        downloads: Int? = null,
        year: Int? = null,
        hearingImpaired: Boolean = false
    ) = SubtitleCandidate("test", name, language, name, season, episode, year, downloads, hearingImpaired, fullSeason)

    private val episode = ReleaseNameParser.parse("Show.Name.S02E05.1080p.WEB-DL.x264-GROUP.mkv")
    private val movie = ReleaseNameParser.parse("The.Movie.Title.2019.1080p.BluRay.x264-SPARKS.mkv")

    @Test
    fun seriesMustMatchSeasonAndEpisode() {
        val ranked = SubtitleRanker.rank(
            episode,
            listOf(
                candidate("Show.Name.S02E05.720p.HDTV.x264-LOL", season = 2, episode = 5),
                candidate("Show.Name.S02E06.1080p.WEB-DL.x264-GROUP", season = 2, episode = 6),
                candidate("Show.Name.S01E05.1080p.WEB-DL.x264-GROUP", season = 1, episode = 5),
                // Numbers missing in the data are read from the release name
                candidate("Show.Name.S02E05.1080p.WEB-DL.x264-GROUP")
            )
        )
        assertEquals(2, ranked.size)
        assertTrue(ranked.all { it.candidate.releaseName.contains("S02E05") })
    }

    @Test
    fun matchingResolutionCodecSourceAndGroupWinsTheRanking() {
        val ranked = SubtitleRanker.rank(
            episode,
            listOf(
                candidate("Show.Name.S02E05.720p.HDTV.x264-LOL", season = 2, episode = 5),
                candidate("Show.Name.S02E05.1080p.WEB-DL.x264-OTHER", season = 2, episode = 5),
                candidate("Show.Name.S02E05.1080p.WEB-DL.x264-GROUP", season = 2, episode = 5)
            )
        )
        assertTrue(ranked[0].candidate.releaseName.endsWith("GROUP"))
        assertTrue(ranked[1].candidate.releaseName.endsWith("OTHER"))
        assertTrue(ranked[0].score > ranked[1].score)
        assertTrue(ranked[1].score > ranked[2].score)
    }

    @Test
    fun fullSeasonPackIsKeptWithAPenalty() {
        val ranked = SubtitleRanker.rank(
            episode,
            listOf(
                candidate("Show.Name.S02.COMPLETE.1080p.WEB-DL", season = 2, fullSeason = true),
                candidate("Show.Name.S03.COMPLETE.1080p.WEB-DL", season = 3, fullSeason = true),
                candidate("Show.Name.S02E05.1080p.WEB-DL.x264-GROUP", season = 2, episode = 5)
            )
        )
        assertEquals(2, ranked.size)                  // the pack of season 3 is dropped
        assertFalse(ranked[0].seasonPack)
        assertTrue(ranked[1].seasonPack)
    }

    @Test
    fun moviesDropEpisodesAndSinkWrongTitlesAndRemakes() {
        val ranked = SubtitleRanker.rank(
            movie,
            listOf(
                candidate("The.Movie.Title.2019.1080p.BluRay.x264-SPARKS"),
                candidate("The.Movie.Title.2019.720p.WEB-DL.x265-X"),
                candidate("The.Movie.Title.2005.1080p.BluRay.x264-SPARKS"),
                candidate("Different.Film.2019.1080p.BluRay.x264-SPARKS"),
                candidate("The.Movie.Title.S01E02.1080p", season = 1, episode = 2)
            )
        )
        assertEquals(4, ranked.size)
        assertEquals("The.Movie.Title.2019.1080p.BluRay.x264-SPARKS", ranked[0].candidate.releaseName)
        assertTrue(ranked[1].candidate.releaseName.contains("720p"))
        assertTrue(ranked.takeLast(2).all {
            it.candidate.releaseName.startsWith("Different") || it.candidate.releaseName.contains("2005")
        })
    }

    @Test
    fun smallBonusesAndPenalties() {
        val plain = SubtitleRanker.score(movie, candidate("The.Movie.Title.2019.1080p.BluRay.x264-SPARKS"))!!
        val popular = SubtitleRanker.score(movie, candidate("The.Movie.Title.2019.1080p.BluRay.x264-SPARKS", downloads = 5000))!!
        val impaired = SubtitleRanker.score(movie, candidate("The.Movie.Title.2019.1080p.BluRay.x264-SPARKS", hearingImpaired = true))!!
        assertTrue(popular.score > plain.score)
        assertTrue(impaired.score < plain.score)
    }

    @Test
    fun emptyListGivesEmptyRanking() {
        assertTrue(SubtitleRanker.rank(movie, emptyList()).isEmpty())
        assertNull(SubtitleRanker.score(episode, candidate("Show.Name.S09E09", season = 9, episode = 9)))
    }
    @Test
    fun exactResolutionCodecSourceAndGroupFromReleaseTagsAreRankedFirst() {
        val wanted = ReleaseNameParser.parse("Movie.Name.2022.1080p.WEB-DL.x265-GROUP.mkv")
        val ranked = SubtitleRanker.rank(
            wanted,
            listOf(
                candidate("Movie.Name.2022.720p.WEB-DL.x264-OTHER"),
                candidate("Movie.Name.2022.1080p.WEB-DL.x265-OTHER"),
                candidate("Movie.Name.2022.1080p.WEB-DL.x265-GROUP")
            )
        )
        assertEquals("Movie.Name.2022.1080p.WEB-DL.x265-GROUP", ranked.first().candidate.releaseName)
        assertTrue(ranked[0].score > ranked[1].score)
        assertTrue(ranked[1].score > ranked[2].score)
    }

    @Test
    fun seasonPackIsStillTreatedAsPackWhenApiReportsItsFirstEpisode() {
        val wanted = ReleaseNameParser.parse("Show.Name.S02E01.1080p.WEB-DL.x264-GROUP.mkv")
        val packed = candidate("Show.Name.S02.COMPLETE.1080p.WEB-DL.x264-GROUP", season = 2, episode = 1, fullSeason = true)
        val ranked = SubtitleRanker.score(wanted, packed)!!
        assertTrue(ranked.seasonPack)
    }

    @Test
    fun ranksFromUnpackedFileNameWhenSubDlReleaseNameIsGeneric() {
        val wanted = ReleaseNameParser.parse("Movie.Name.2022.1080p.WEB-DL.x265-GROUP.mkv")
        val genericRelease = SubtitleCandidate(
            "subdl", "/subtitle/1/file", "en", "Movie Name subtitle", null, null,
            null, null, false, false,
            "Movie.Name.2022.1080p.WEB-DL.x265-GROUP.srt"
        )
        val differentRelease = candidate("Movie.Name.2022.720p.WEB-DL.x264-OTHER")
        val ranked = SubtitleRanker.rank(wanted, listOf(differentRelease, genericRelease))
        assertEquals("/subtitle/1/file", ranked.first().candidate.ref)
    }


    @Test
    fun matchingBitDepthGetsAnAdditionalRankingBonus() {
        val wanted = ReleaseNameParser.parse("Film.Name.2024.1080p.10bit.x265-GROUP.mkv")
        val ranked = SubtitleRanker.rank(
            wanted,
            listOf(
                candidate("Film.Name.2024.1080p.8bit.x265-GROUP"),
                candidate("Film.Name.2024.1080p.10bit.x265-GROUP")
            )
        )
        assertTrue(ranked.first().candidate.releaseName.contains("10bit"))
        assertEquals(12, ranked.first().score - ranked.last().score)
    }


    @Test
    fun bitDepthCanBeReadFromTheUnpackedSubtitleFileName() {
        val wanted = ReleaseNameParser.parse("Film.Name.2024.1080p.10bit.x265-GROUP.mkv")
        val genericRelease = SubtitleCandidate(
            "subdl", "best", "en", "Film Name subtitle", null, null,
            null, null, false, false,
            "Film.Name.2024.1080p.10bit.x265-GROUP.srt"
        )
        val wrongBitDepth = SubtitleCandidate(
            "subdl", "other", "en", "Film.Name.2024.1080p.8bit.x265-GROUP", null, null,
            2024, null, false, false
        )

        val ranked = SubtitleRanker.rank(wanted, listOf(wrongBitDepth, genericRelease))
        assertEquals("best", ranked.first().candidate.ref)
    }

}
