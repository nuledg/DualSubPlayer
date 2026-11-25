package com.dualsub.player.download

import org.junit.Assert.assertEquals
import org.junit.Test

/** The video title is split into name and season/episode (or year); the subtitle window shows it. */
class ReleaseInfoDisplayTest {

    private fun show(fileName: String) = ReleaseNameParser.parse(fileName).displayName

    @Test
    fun seriesShowsNameSeasonAndEpisode() {
        assertEquals("Breaking Bad  S02E05", show("Breaking.Bad.S02E05.1080p.BluRay.x265-AAA.mkv"))
        assertEquals("Grey's Anatomy  S11E03", show("Grey's Anatomy - 11x03 - Title.mp4"))
        assertEquals("Show Name  S01E10", show("show_name_s01e10_720p.mkv"))
    }

    @Test
    fun moviesShowNameAndYear() {
        assertEquals("Inception (2010)", show("Inception.2010.1080p.BluRay.x264-GRP.mkv"))
        assertEquals("The Dark Knight", show("The.Dark.Knight.1080p.mkv"))
    }

    @Test
    fun seasonAndEpisodeAreSplitOut() {
        val info = ReleaseNameParser.parse("Breaking.Bad.S02E05.1080p.mkv")
        assertEquals("Breaking Bad", info.title)
        assertEquals(2, info.season)
        assertEquals(5, info.episode)
    }

    @Test
    fun fileNameIsUsedWhenNoTitleCanBeRead() {
        assertEquals("S01E02.mkv", show("S01E02.mkv"))
    }
}
