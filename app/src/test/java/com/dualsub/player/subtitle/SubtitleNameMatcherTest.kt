package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleNameMatcherTest {

    private fun matches(video: String, sub: String) = SubtitleNameMatcher.score(video, sub) != null

    // ---- Exact names (the old behavior) ----

    @Test
    fun exactVideoNameStillMatchesAndRanksFirst() {
        val video = "Inception.2010.1080p.BluRay.x264-GRP.mkv"
        assertEquals(SubtitleNameMatcher.EXACT_SCORE, SubtitleNameMatcher.score(video, "Inception.2010.1080p.BluRay.x264-GRP.en.srt"))
        assertEquals(SubtitleNameMatcher.EXACT_SCORE, SubtitleNameMatcher.score(video, "inception.2010.1080p.bluray.x264-grp.fa.srt"))
        assertEquals(SubtitleNameMatcher.EXACT_SCORE, SubtitleNameMatcher.score(video, "Inception.2010.1080p.BluRay.x264-GRP.srt"))
    }

    // ---- Series ----

    @Test
    fun sameEpisodeWithDifferentReleaseTagsMatches() {
        val video = "Breaking.Bad.S02E05.1080p.BluRay.x265-AAA.mkv"
        assertTrue(matches(video, "Breaking.Bad.S02E05.720p.WEB-DL.x264-BBB.en.srt"))
        assertTrue(matches(video, "Breaking Bad - S02E05 - Breakage.fa.srt"))
        assertTrue(matches(video, "breaking_bad_s02e05.srt"))
        assertTrue(matches(video, "Breaking.Bad.2x05.English.srt"))
    }

    @Test
    fun otherEpisodeOrSeasonNeverMatches() {
        val video = "Breaking.Bad.S02E05.1080p.mkv"
        assertNull(SubtitleNameMatcher.score(video, "Breaking.Bad.S02E06.en.srt"))
        assertNull(SubtitleNameMatcher.score(video, "Breaking.Bad.S03E05.en.srt"))
        assertNull(SubtitleNameMatcher.score(video, "Breaking.Bad.en.srt"))
    }

    @Test
    fun otherSeriesNeverMatches() {
        val video = "Breaking.Bad.S02E05.mkv"
        assertNull(SubtitleNameMatcher.score(video, "Better.Call.Saul.S02E05.en.srt"))
        // The subtitle title must not be longer than the video title with extra words.
        assertNull(SubtitleNameMatcher.score(video, "Breaking.Bad.Extras.S02E05.en.srt"))
    }

    @Test
    fun episodeOnlyNameIsAcceptedForSeries() {
        assertTrue(matches("Show.Name.S01E02.mkv", "S01E02.en.srt"))
        assertNull(SubtitleNameMatcher.score("Show.Name.S01E02.mkv", "S01E03.en.srt"))
    }

    @Test
    fun apostropheAndAmpersandDoNotBreakTheTitle() {
        assertTrue(matches("Grey's.Anatomy.S01E01.mkv", "Greys.Anatomy.S01E01.en.srt"))
        assertTrue(matches("Law.&.Order.S01E01.mkv", "Law.and.Order.S01E01.en.srt"))
    }

    // ---- Movies ----

    @Test
    fun sameMovieWithAndWithoutTagsMatches() {
        val video = "Inception.2010.1080p.BluRay.x264-GRP.mkv"
        assertTrue(matches(video, "Inception.2010.720p.WEB.en.srt"))
        assertTrue(matches(video, "Inception (2010).fa.srt"))
        assertTrue(matches(video, "Inception.en.srt"))
        assertTrue(matches(video, "Inception.English.HI.srt"))
        assertTrue(matches(video, "Inception.forced.srt"))
    }

    @Test
    fun sequelsRemakesAndOtherCutsDoNotMatch() {
        assertNull(SubtitleNameMatcher.score("Toy.Story.1995.mkv", "Toy.Story.2.1999.en.srt"))
        assertNull(SubtitleNameMatcher.score("Toy.Story.mkv", "Toy.Story.2.en.srt"))
        assertNull(SubtitleNameMatcher.score("Toy.Story.2.1999.mkv", "Toy.Story.1995.en.srt"))
        assertNull(SubtitleNameMatcher.score("Dune.1984.mkv", "Dune.2021.en.srt"))
        assertNull(SubtitleNameMatcher.score("Inception.2010.mkv", "Inception.Extended.Cut.en.srt"))
    }

    @Test
    fun movieDoesNotTakeAnEpisodeSubtitle() {
        assertNull(SubtitleNameMatcher.score("Fargo.1996.mkv", "Fargo.S01E01.en.srt"))
    }

    @Test
    fun titleWordThatLooksLikeALanguageStaysPartOfTheTitle() {
        val video = "The.English.Patient.1996.mkv"
        assertTrue(matches(video, "The.English.Patient.fa.srt"))
        assertNull(SubtitleNameMatcher.score(video, "The.English.fa.srt"))
    }

    @Test
    fun nonLatinTitlesMatch() {
        assertTrue(matches("جدایی نادر از سیمین 2011.mkv", "جدایی نادر از سیمین.fa.srt"))
    }

    @Test
    fun slowHorsesExampleMatchesSubtitleContainingExactSeriesEpisodeIdentity() {
        val video = "Slow.Horses.S03E03.10bit.x265.720p.WEBRip.2CH.PSA.Farsi.Sub.Film2Media.mkv"
        assertTrue(matches(video, "Slow.Horses.S03E03.en.srt"))
        assertTrue(matches(video, "Slow.Horses.S03E03.Farsi.Sub.Film2Media.srt"))
        assertTrue(matches(video, "Downloaded.Slow.Horses.S03E03.1080p.WEB-DL.fa.srt"))
        assertNull(SubtitleNameMatcher.score(video, "Downloaded.Slow.Horses.S03E04.fa.srt"))
        assertNull(SubtitleNameMatcher.score(video, "Downloaded.Slow.Horses.S04E03.fa.srt"))
        assertNull(SubtitleNameMatcher.score(video, "Downloaded.Slow.Horses.Extras.S03E03.fa.srt"))
    }

    @Test
    fun prefixedMovieSubtitleCanMatchWithoutAllowingSequelWords() {
        val video = "Inception.2010.1080p.BluRay.x264-GRP.mkv"
        assertTrue(matches(video, "Downloaded.Inception.2010.fa.srt"))
        assertTrue(matches(video, "Subs.Inception.720p.WEB-DL.en.srt"))
        assertNull(SubtitleNameMatcher.score("Toy.Story.mkv", "Downloaded.Toy.Story.2.en.srt"))
        assertNull(SubtitleNameMatcher.score("Dune.1984.mkv", "Subs.Dune.2021.en.srt"))
    }

    // ---- Ranking ----

    @Test
    fun sameReleaseRanksAboveOtherReleases() {
        val video = "Show.Name.S01E01.1080p.BluRay.x265-AAA.mkv"
        val same = SubtitleNameMatcher.score(video, "Show.Name.S01E01.1080p.BluRay.x265-AAA.en.srt")
        val sameTagsOtherGroup = SubtitleNameMatcher.score(video, "Show.Name.S01E01.1080p.BluRay.x265-BBB.en.srt")
        val other = SubtitleNameMatcher.score(video, "Show.Name.S01E01.720p.WEB.x264-CCC.en.srt")
        assertNotNull(same)
        assertNotNull(sameTagsOtherGroup)
        assertNotNull(other)
        assertTrue(same!! > sameTagsOtherGroup!!)
        assertTrue(sameTagsOtherGroup > other!!)
        assertTrue(other > 0)
    }

    @Test
    fun tokensIgnorePunctuationAndCase() {
        assertEquals(listOf("greys", "anatomy"), SubtitleNameMatcher.tokens("Grey's  ANATOMY"))
        assertEquals(listOf("law", "and", "order"), SubtitleNameMatcher.tokens("Law & Order"))
    }
}
