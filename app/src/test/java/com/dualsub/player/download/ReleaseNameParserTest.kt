package com.dualsub.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNameParserTest {

    @Test
    fun seriesWithAllTags() {
        val r = ReleaseNameParser.parse("Show.Name.S02E05.1080p.WEB-DL.x264-GROUP.mkv")
        assertEquals("Show Name", r.title)
        assertEquals(2, r.season)
        assertEquals(5, r.episode)
        assertEquals(1080, r.resolution)
        assertEquals("webdl", r.source)
        assertEquals("h264", r.codec)
        assertEquals("GROUP", r.group)
        assertNull(r.year)
        assertTrue(r.isSeries)
    }

    @Test
    fun movieWithYearAndDottedCodec() {
        val r = ReleaseNameParser.parse("The.Movie.Title.2019.720p.BluRay.H.264-SPARKS.mkv")
        assertEquals("The Movie Title", r.title)
        assertEquals(2019, r.year)
        assertEquals(720, r.resolution)
        assertEquals("bluray", r.source)
        assertEquals("h264", r.codec)
        assertEquals("SPARKS", r.group)
        assertFalse(r.isSeries)
    }

    @Test
    fun yearThatIsPartOfTheTitle() {
        val r = ReleaseNameParser.parse("Blade.Runner.2049.2017.2160p.UHD.BluRay.x265-GRP.mkv")
        assertEquals("Blade Runner 2049", r.title)
        assertEquals(2017, r.year)
        assertEquals(2160, r.resolution)
        assertEquals("hevc", r.codec)
    }

    @Test
    fun titleThatLooksLikeAYear() {
        val r = ReleaseNameParser.parse("2012.1080p.BluRay.x264.mkv")
        assertEquals("2012", r.title)
        assertNull(r.year)
        assertEquals(1080, r.resolution)
    }

    @Test
    fun threeXSevenStyleAndSpaces() {
        val r = ReleaseNameParser.parse("Another Show 3x07 HDTV XviD-LOL.avi")
        assertEquals("Another Show", r.title)
        assertEquals(3, r.season)
        assertEquals(7, r.episode)
        assertEquals("hdtv", r.source)
        assertEquals("xvid", r.codec)
        assertEquals("LOL", r.group)
    }

    @Test
    fun bracketsAndNoGroup() {
        val r = ReleaseNameParser.parse("Movie Name (2021) 1080p WEBRip.mp4")
        assertEquals("Movie Name", r.title)
        assertEquals(2021, r.year)
        assertEquals("webrip", r.source)
        assertNull(r.group)
    }

    @Test
    fun webDlIsNotAGroup() {
        val r = ReleaseNameParser.parse("Movie.Name.2020.1080p.WEB-DL.mkv")
        assertNull(r.group)
        assertEquals("webdl", r.source)
    }

    @Test
    fun resolutionIsNotMistakenForAnEpisodeStyle() {
        val r = ReleaseNameParser.parse("Clip.1920x1080.mkv")
        assertFalse(r.isSeries)
        assertEquals(1080, r.resolution)
    }

    @Test
    fun keepsOriginalFileNameAndExtractsResolutionAndCodecForSubDlSearch() {
        val original = "Show.Name.S02E05.1080p.WEB-DL.x265-GROUP.mkv"
        val r = ReleaseNameParser.parse("/storage/emulated/0/Movies/$original")
        assertEquals("Show Name", r.title)
        assertEquals(2, r.season)
        assertEquals(5, r.episode)
        assertEquals(1080, r.resolution)
        assertEquals("hevc", r.codec)
        assertEquals("webdl", r.source)
        assertEquals("GROUP", r.group)
        assertEquals(original, r.originalFileName)
    }

    @Test
    fun extractsResolutionFromWidthAndHeightFilenameTags() {
        val fullHd = ReleaseNameParser.parse("Movie.Title.2019.1920x1080.BluRay.x264-GRP.mkv")
        assertEquals(1080, fullHd.resolution)
        assertEquals("Movie Title", fullHd.title)
        assertEquals("h264", fullHd.codec)

        val hd = ReleaseNameParser.parse("Another.Movie.1280x720.WEBRip.x265.mkv")
        assertEquals(720, hd.resolution)
        assertEquals("hevc", hd.codec)
    }

    @Test
    fun extracts2160AndAv1Tags() {
        val r = ReleaseNameParser.parse("Film.2024.4K.WEB-DL.AV1-GRP.mkv")
        assertEquals(2160, r.resolution)
        assertEquals("av1", r.codec)
        assertEquals("webdl", r.source)
    }

    @Test
    fun supportsWindowsStylePaths() {
        val r = ReleaseNameParser.parse("D:\\Movies\\Film.2020.720p.WEB-DL.x264.mkv")
        assertEquals("Film", r.title)
        assertEquals(2020, r.year)
        assertEquals(720, r.resolution)
    }

    @Test
    fun handlesSeparatedSourceTagsAndUhd() {
        val r = ReleaseNameParser.parse("Film.Title.2023.UHD.WEB.DL.H.265-GROUP.mkv")
        assertEquals(2160, r.resolution)
        assertEquals("webdl", r.source)
        assertEquals("hevc", r.codec)

        val rip = ReleaseNameParser.parse("Another.Movie.2021.720p.WEB.Rip.x264-GRP.mkv")
        assertEquals("webrip", rip.source)
        assertEquals("h264", rip.codec)
    }

    @Test
    fun detectsHevcWhenCodecUsesDifferentSeparators() {
        val filenames = listOf(
            "The-Movie-Name-2024-1080p-WEB-DL-x-265-GROUP.mkv",
            "The_Movie_Name_2024_1920x1080_WEB_DL_x_265_GROUP.mkv",
            "The.Movie.Name.2024.1080p.WEB.DL.x.265.GROUP.mkv",
            "The+Movie+Name+2024+1080p+WEB+DL+H.265.mkv",
            "The Movie Name 2024 1080p WEB DL H-265.mkv",
            "The.Movie.Name.2024.1080p.Blu-Ray.X_265-GROUP.mkv"
        )

        filenames.forEach { filename ->
            val parsed = ReleaseNameParser.parse(filename)
            assertEquals("The Movie Name", parsed.title)
            assertEquals(2024, parsed.year)
            assertEquals(1080, parsed.resolution)
            assertEquals("hevc", parsed.codec)
            val expectedSource = if (filename.contains("WEB", true)) "webdl" else "bluray"
            assertEquals(expectedSource, parsed.source)
        }
    }

    @Test
    fun normalizesAnyCommonTitleSeparatorAndKeepsOriginalFilename() {
        val filename = "A-Title_Name+With.Different Separators_2022-720p-x265.mkv"
        val parsed = ReleaseNameParser.parse(filename)
        assertEquals("A Title Name With Different Separators", parsed.title)
        assertEquals(2022, parsed.year)
        assertEquals(720, parsed.resolution)
        assertEquals("hevc", parsed.codec)
        assertEquals(filename, parsed.originalFileName)
    }

    @Test
    fun parsesWidthHeightWithSpacesAroundTheX() {
        val parsed = ReleaseNameParser.parse("Movie_Name_2023_1920 x 1080_WEB-DL_x265.mkv")
        assertEquals("Movie Name", parsed.title)
        assertEquals(2023, parsed.year)
        assertEquals(1080, parsed.resolution)
        assertEquals("hevc", parsed.codec)
    }

    @Test
    fun detectsSeriesMarkersWhenSeparatorsSplitTheLettersAndNumbers() {
        val parsed = ReleaseNameParser.parse("Another-Show-S_02_E_05-1080p-x-265.mkv")
        assertEquals("Another Show", parsed.title)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
        assertEquals(1080, parsed.resolution)
        assertEquals("hevc", parsed.codec)
    }

    @Test
    fun hyphenatedTitleWordsAreNotMistakenForAReleaseGroup() {
        val parsed = ReleaseNameParser.parse("Spider-Man.mkv")
        assertEquals("Spider Man", parsed.title)
        assertNull(parsed.group)

        val apostrophe = ReleaseNameParser.parse("Schindler's.List.1993.1080p.BluRay.x265.mkv")
        assertEquals("Schindler's List", apostrophe.title)
        assertEquals(1993, apostrophe.year)
    }


    @Test
    fun extractsTenBitAndX265FromFilenameWithDifferentSeparators() {
        val names = listOf(
            "Example.Movie.2024.1080p.10bit.x265-GROUP.mkv",
            "Example-Movie-2024-1080p-10-bit-x-265-GROUP.mkv",
            "Example_Movie_2024_1080p_10_bit_x_265_GROUP.mkv",
            "Example Movie 2024 1080p 10 bit H.265-GROUP.mkv",
            "Example.Movie.2024.1080p.x265.10bit-GROUP.mkv"
        )

        names.forEach { name ->
            val parsed = ReleaseNameParser.parse(name)
            assertEquals("Example Movie", parsed.title)
            assertEquals(2024, parsed.year)
            assertEquals(1080, parsed.resolution)
            assertEquals(10, parsed.bitDepth)
            assertEquals("hevc", parsed.codec)
            assertEquals("GROUP", parsed.group)
        }
    }

    @Test
    fun extractsEightAndTwelveBitDepthsToo() {
        assertEquals(8, ReleaseNameParser.parse("Film.2020.8bit.x264-GRP.mkv").bitDepth)
        assertEquals(12, ReleaseNameParser.parse("Film.2020.12-bit.x265-GRP.mkv").bitDepth)
    }

}
