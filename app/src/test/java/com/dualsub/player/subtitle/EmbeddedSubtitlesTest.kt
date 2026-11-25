package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EmbeddedSubtitlesTest {

    private val english = listOf(
        Cue(500, 1500, "Hello there"),
        Cue(2000, 4000, "Second line\nwith two rows"),
        Cue(5000, 7250, "Third, with comma")
    )
    private val persian = listOf(
        Cue(600, 1600, "سلام"),
        Cue(2100, 4100, "خط دوم"),
        Cue(5100, 7300, "خط سوم")
    )

    // ---- Real files made with ffmpeg ----

    @Test
    fun mkvWithTwoSubRipTracks() {
        val source = fixtureSource("srt_two_tracks.mkv")
        val tracks = EmbeddedSubtitles.listTracks(source)

        assertEquals(2, tracks.size)
        assertEquals(ContainerType.MKV, tracks[0].container)
        assertEquals("subrip", tracks[0].codec)
        assertEquals("en", tracks[0].language)
        assertTrue(tracks[0].isText)
        assertEquals(0, tracks[0].index)
        assertEquals("fa", tracks[1].language)
        assertEquals("Persian", tracks[1].title)
        assertEquals(1, tracks[1].index)

        assertEquals(english, EmbeddedSubtitles.readCues(source, tracks[0]))
        assertEquals(persian, EmbeddedSubtitles.readCues(source, tracks[1]))
    }

    @Test
    fun mkvWithAssAndWebVttTracks() {
        val source = fixtureSource("ass_and_webvtt.mkv")
        val tracks = EmbeddedSubtitles.listTracks(source)

        assertEquals(2, tracks.size)
        assertEquals("ass", tracks[0].codec)
        assertEquals("webvtt", tracks[1].codec)
        assertEquals("fa", tracks[1].language)

        assertEquals(english, EmbeddedSubtitles.readCues(source, tracks[0]))
        assertEquals(persian, EmbeddedSubtitles.readCues(source, tracks[1]))
    }

    @Test
    fun mp4WithMovText() {
        val source = fixtureSource("movtext.mp4")
        val tracks = EmbeddedSubtitles.listTracks(source)

        assertEquals(1, tracks.size)
        assertEquals(ContainerType.MP4, tracks[0].container)
        assertEquals("mov_text", tracks[0].codec)
        assertEquals("en", tracks[0].language)
        assertTrue(tracks[0].isText)

        assertEquals(english, EmbeddedSubtitles.readCues(source, tracks[0]))
    }

    @Test
    fun filesThatAreNotVideoContainersGiveNoTracks() {
        assertTrue(EmbeddedSubtitles.listTracks(BytesSource("hello, this is not a video".toByteArray())).isEmpty())
        assertTrue(EmbeddedSubtitles.listTracks(BytesSource(ByteArray(0))).isEmpty())
        assertTrue(EmbeddedSubtitles.listTracks(BytesSource(fixtureBytes("en.srt"))).isEmpty())
    }

    @Test
    fun aCutOffFileDoesNotCrash() {
        val bytes = fixtureBytes("srt_two_tracks.mkv")
        val cut = BytesSource(bytes.copyOf(bytes.size / 2))
        val tracks = EmbeddedSubtitles.listTracks(cut)
        assertEquals(2, tracks.size)
        // Reading must finish without an exception. It just finds fewer cues.
        EmbeddedSubtitles.readCues(cut, tracks[0])
    }

    // ---- Files built by hand for cases no encoder makes ----

    private fun handMadeFile(): ByteArray = MkvBuilder.file(
        listOf(
            MkvBuilder.info(),
            MkvBuilder.tracks(
                MkvBuilder.videoTrack(5),
                MkvBuilder.subtitleTrack(1, "S_TEXT/UTF8", "eng", name = "English"),
                MkvBuilder.subtitleTrack(2, "S_HDMV/PGS", "fre"),
                MkvBuilder.subtitleTrack(3, "S_TEXT/ASS", "und"),
                MkvBuilder.subtitleTrack(4, "S_TEXT/UTF8", "eng", zlib = true),
                MkvBuilder.subtitleTrack(6, "S_VOBSUB", "spa")
            ),
            // Clusters of unknown size, as in live recordings
            MkvBuilder.masterUnknownSize(
                0x1F43B675,
                MkvBuilder.clusterTimecode(1000),
                MkvBuilder.blockGroup(1, 0, "Hello".toByteArray(), 1500),
                MkvBuilder.simpleBlock(1, 2000, "No duration".toByteArray()),
                MkvBuilder.blockGroup(1, 4000, "<i>Styled</i>".toByteArray(), 1000),
                MkvBuilder.blockGroup(3, 0, "0,0,Default,,0,0,0,,Hello, world\\Nsecond".toByteArray(), 2000),
                MkvBuilder.blockGroup(4, 0, MkvBuilder.zlib("Compressed line".toByteArray()), 800),
                MkvBuilder.simpleBlock(5, 100, ByteArray(500) { 7 }) // a video frame, must be skipped
            ),
            MkvBuilder.masterUnknownSize(
                0x1F43B675,
                MkvBuilder.clusterTimecode(10_000),
                MkvBuilder.blockGroup(1, 500, "Later".toByteArray(), 1000),
                MkvBuilder.simpleBlock(1, 5000, "Last".toByteArray())
            )
        )
    )

    @Test
    fun handMadeFileListsTextAndPictureTracks() {
        val tracks = EmbeddedSubtitles.listTracks(BytesSource(handMadeFile()))

        assertEquals(listOf(1L, 2L, 3L, 4L, 6L), tracks.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4), tracks.map { it.index })
        assertEquals("English", tracks[0].title)

        val pgs = tracks[1]
        assertEquals("pgs", pgs.codec)
        assertFalse(pgs.isText)
        assertEquals("fr", pgs.language)

        assertNull(tracks[2].language)                // "und" means unknown
        assertEquals("ass", tracks[2].codec)
        assertEquals("vobsub", tracks[4].codec)
        assertFalse(tracks[4].isText)
    }

    @Test
    fun handMadeFileGivesCuesWithDurationsAndGuessedEnds() {
        val source = BytesSource(handMadeFile())
        val tracks = EmbeddedSubtitles.listTracks(source)

        assertEquals(
            listOf(
                Cue(1000, 2500, "Hello"),
                Cue(3000, 5000, "No duration"),     // ends when the next cue starts
                Cue(5000, 6000, "Styled"),          // tags removed
                Cue(10_500, 11_500, "Later"),       // second cluster
                Cue(15_000, 18_000, "Last")         // last cue without duration: 3 seconds
            ),
            EmbeddedSubtitles.readCues(source, tracks[0])
        )
        assertEquals(
            listOf(Cue(1000, 3000, "Hello, world\nsecond")),
            EmbeddedSubtitles.readCues(source, tracks[2])
        )
    }

    @Test
    fun zlibCompressedBlocksAreUnpacked() {
        val source = BytesSource(handMadeFile())
        val tracks = EmbeddedSubtitles.listTracks(source)
        assertEquals(listOf(Cue(1000, 1800, "Compressed line")), EmbeddedSubtitles.readCues(source, tracks[3]))
    }

    @Test
    fun pictureTracksCannotBeReadAsText() {
        val source = BytesSource(handMadeFile())
        val pgs = EmbeddedSubtitles.listTracks(source)[1]
        try {
            EmbeddedSubtitles.readCues(source, pgs)
            fail("Expected an exception")
        } catch (expected: IllegalArgumentException) {
            // good: picture subtitles have no text
        }
    }

    @Test
    fun timecodeScaleIsUsed() {
        val file = MkvBuilder.file(
            listOf(
                MkvBuilder.info(timecodeScale = 2_000_000L), // each unit is 2 ms
                MkvBuilder.tracks(MkvBuilder.subtitleTrack(1, "S_TEXT/UTF8", "eng")),
                MkvBuilder.masterUnknownSize(
                    0x1F43B675,
                    MkvBuilder.clusterTimecode(500),
                    MkvBuilder.blockGroup(1, 0, "Scaled".toByteArray(), 250)
                )
            )
        )
        val source = BytesSource(file)
        val cues = EmbeddedSubtitles.readCues(source, EmbeddedSubtitles.listTracks(source)[0])
        assertEquals(listOf(Cue(1000, 1500, "Scaled")), cues)
    }
}
