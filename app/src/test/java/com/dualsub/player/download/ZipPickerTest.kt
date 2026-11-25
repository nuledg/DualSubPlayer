package com.dualsub.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipPickerTest {

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val wanted = ReleaseNameParser.parse("Show.Name.S02E05.1080p.WEB-DL.x264-GROUP.mkv")

    @Test
    fun recognisesZipFiles() {
        assertTrue(ZipPicker.isZip(zip("a.srt" to "x")))
        assertFalse(ZipPicker.isZip("1\n00:00:01,000 --> 00:00:02,000\nHi".toByteArray()))
        assertFalse(ZipPicker.isZip(ByteArray(0)))
    }

    @Test
    fun picksTheRightEpisodeAndTheBestRelease() {
        val picked = ZipPicker.pick(
            zip(
                "Show.Name.S02E04.720p.srt" to "episode 4",
                "Show.Name.S02E05.720p.HDTV.srt" to "episode 5 hdtv",
                "Show.Name.S02E05.1080p.WEB-DL.x264-GROUP.srt" to "episode 5 best",
                "readme.txt" to "not a subtitle",
                "__MACOSX/Show.Name.S02E05.srt" to "junk"
            ),
            wanted
        )
        assertNotNull(picked)
        assertEquals("episode 5 best", String(picked!!.bytes))
    }

    @Test
    fun aSingleSubtitleIsUsedWhateverItIsCalled() {
        val picked = ZipPicker.pick(zip("whatever.srt" to "only one"), wanted)
        assertEquals("only one", String(picked!!.bytes))
    }

    @Test
    fun zipWithoutSubtitlesGivesNothing() {
        assertNull(ZipPicker.pick(zip("readme.txt" to "x"), wanted))
        assertNull(ZipPicker.pick(zip("Show.Name.S02E01.srt" to "other episode"), wanted))
    }

    @Test
    fun srtIsPreferredOverOtherFormatsForTheSameEpisode() {
        val picked = ZipPicker.pick(
            zip("Show.S02E05.ass" to "ass version", "Show.S02E05.srt" to "srt version"),
            wanted
        )
        assertEquals("srt version", String(picked!!.bytes))
    }
    @Test
    fun picksTheLanguageTheUserSelectedWhenZipContainsMultipleLanguages() {
        val archive = zip(
            "Show.Name.S02E05.en.srt" to "English subtitle",
            "Show.Name.S02E05.fa.srt" to "Persian subtitle"
        )
        val english = ZipPicker.pick(archive, wanted, "en")
        val persian = ZipPicker.pick(archive, wanted, "fa")
        assertEquals("English subtitle", String(english!!.bytes))
        assertEquals("Persian subtitle", String(persian!!.bytes))
    }

    @Test
    fun exactLanguageWinsOverUntaggedSubtitleInZip() {
        val picked = ZipPicker.pick(
            zip(
                "Show.Name.S02E05.srt" to "untagged subtitle",
                "Show.Name.S02E05.fa.srt" to "Persian subtitle"
            ),
            wanted,
            "fa"
        )
        assertEquals("Persian subtitle", String(picked!!.bytes))
    }

    @Test
    fun wordsInsideMovieTitleAreNotMistakenForLanguageTags() {
        val picked = ZipPicker.pick(
            zip(
                "The.English.Patient.S02E05.srt" to "untagged title subtitle",
                "The.English.Patient.S02E05.fa.srt" to "Persian subtitle"
            ),
            wanted,
            "fa"
        )
        assertEquals("Persian subtitle", String(picked!!.bytes))
    }

}
