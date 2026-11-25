package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun srtReadsTimesAndTextAndRemovesTags() {
        val srt = "1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Hello</i> {\\an8}world\r\n\r\n" +
            "2\r\n00:00:04,000 --> 00:00:06,000\r\nLine A\r\nLine B\r\n"
        val cues = SubtitleParser.parse(srt, "a.srt")
        assertEquals(2, cues.size)
        assertEquals(Cue(1000L, 3500L, "Hello world"), cues[0])
        assertEquals("Line A\nLine B", cues[1].text)
    }

    @Test
    fun srtWithMissingBlankLineKeepsTheNextCueNumberOutOfTheText() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nFirst\n2\n00:00:03,000 --> 00:00:04,000\nSecond\n"
        val cues = SubtitleParser.parse(srt, "a.srt")
        assertEquals(2, cues.size)
        assertEquals("First", cues[0].text)
        assertEquals("Second", cues[1].text)
    }

    @Test
    fun srtWithByteOrderMarkIsRead() {
        val cues = SubtitleParser.parse("\uFEFF1\n00:00:01,000 --> 00:00:02,000\nHi\n", "a.srt")
        assertEquals(1, cues.size)
    }

    @Test
    fun webVttSkipsHeaderNotesAndIds() {
        val vtt = "WEBVTT\n\nNOTE a comment\n\nintro\n01:02.500 --> 01:04.000 align:start\nHi\n\n" +
            "00:00:10.000 --> 00:00:12.000\nYo\n"
        val cues = SubtitleParser.parse(vtt, "a.vtt")
        assertEquals(2, cues.size)
        assertEquals(10_000L, cues[0].startMs)   // sorted by start time
        assertEquals(62_500L, cues[1].startMs)
        assertEquals("Hi", cues[1].text)
    }

    @Test
    fun assReadsDialogueWithCommasBreaksAndIgnoresDrawingsAndComments() {
        val ass = "[Script Info]\nTitle: x\n[Events]\n" +
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\\i1}Hello, world\\Nsecond\n" +
            "Dialogue: 0,0:00:05.00,0:00:06.05,Default,,0,0,0,,{\\p1}m 0 0 l 1 1\n" +
            "Comment: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,not shown\n"
        val cues = SubtitleParser.parse(ass, "a.ass")
        assertEquals(1, cues.size)
        assertEquals(Cue(1500L, 3000L, "Hello, world\nsecond"), cues[0])
    }

    @Test
    fun assIsRecognisedByContentEvenWithAnSrtName() {
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hi\n"
        assertEquals(1, SubtitleParser.parse(ass, "movie.fa.srt").size)
    }

    @Test
    fun timestampsInAllFormats() {
        assertEquals(3_723_456L, SubtitleParser.parseTimestamp("01:02:03,456"))
        assertEquals(62_500L, SubtitleParser.parseTimestamp("01:02.500"))
        assertEquals(1_500L, SubtitleParser.parseTimestamp("0:00:01.50"))
        assertEquals(1_050L, SubtitleParser.parseTimestamp("0:00:01.05"))
        assertEquals(1_500L, SubtitleParser.parseTimestamp("00:00:01,5"))
    }

    @Test
    fun emptyAndBrokenInputGiveNoCues() {
        assertTrue(SubtitleParser.parse("", "a.srt").isEmpty())
        assertTrue(SubtitleParser.parse("not a subtitle file", "a.srt").isEmpty())
        // End before start is dropped.
        assertTrue(SubtitleParser.parse("1\n00:00:05,000 --> 00:00:02,000\nx\n", "a.srt").isEmpty())
    }

    @Test
    fun persianTextIsKept() {
        val cues = SubtitleParser.parse("1\n00:00:01,000 --> 00:00:02,000\nسلام دنیا\n", "a.fa.srt")
        assertEquals("سلام دنیا", cues[0].text)
    }
}
