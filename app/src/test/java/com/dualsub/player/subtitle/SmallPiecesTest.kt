package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SmallPiecesTest {

    // ---- SrtWriter ----

    @Test
    fun srtWriterAndParserAgree() {
        val cues = listOf(
            Cue(500, 1500, "Hello"),
            Cue(3_723_456, 3_725_000, "Two\nlines"),
            Cue(4_000_000, 4_001_000, "سلام")
        )
        val text = SrtWriter.write(cues)
        assertTrue(text.contains("01:02:03,456 --> 01:02:05,000"))
        assertEquals(cues, SubtitleParser.parse(text, "x.srt"))
    }

    // ---- Language codes ----

    @Test
    fun embeddedLanguageCodesAreNormalised() {
        assertEquals("en", LanguageCodes.normalize("eng"))
        assertEquals("en", LanguageCodes.normalize("en-US"))
        assertEquals("fa", LanguageCodes.normalize("per"))
        assertEquals("fa", LanguageCodes.normalize("fas"))
        assertEquals("es", LanguageCodes.normalize("spa"))
        assertEquals("xx", LanguageCodes.normalize("xx"))
        assertNull(LanguageCodes.normalize("und"))
        assertNull(LanguageCodes.normalize(""))
        assertNull(LanguageCodes.normalize(null))
    }

    @Test
    fun languageFromSubtitleFileNames() {
        assertEquals("en", LanguageCodes.fromFileName("en.srt"))
        assertEquals("fa", LanguageCodes.fromFileName("fa.srt"))
        assertEquals("fa", LanguageCodes.fromFileName("Movie.Name.2020.Persian.srt"))
        assertEquals("en", LanguageCodes.fromFileName("Movie.eng.hi.srt"))
        assertNull(LanguageCodes.fromFileName("srt"))
        assertEquals("de", LanguageCodes.fromFileName("de.srt"))   // any language is known now
        assertNull(LanguageCodes.fromFileName("xx.srt"))
    }

    // ---- Subtitle style ----

    @Test
    fun defaultStylesPutHelperSubtitleAboveMainInYellow() {
        val s1 = SubtitleStyle.defaultFor(SubtitleSlot.S1)
        val s2 = SubtitleStyle.defaultFor(SubtitleSlot.S2)
        assertEquals(SubtitleStyle.YELLOW, s2.colorArgb)
        assertEquals(SubtitleStyle.WHITE, s1.colorArgb)
        assertTrue(s2.yPercent < s1.yPercent)
    }

    @Test
    fun styleSurvivesSavingAndLoading() {
        val base = SubtitleStyle.defaultFor(SubtitleSlot.S1)
        val custom = base.copy(
            font = SubtitleStyle.FONT_CUSTOM, customFontPath = "/data/files/fonts/font_s1_17.ttf",
            sizeSp = 31.5f, bold = true, outlineWidth = 3f, shadow = false,
            backgroundRgb = 0x0D1B3E, backgroundOpacity = 0.25f, xPercent = 12.5f, yPercent = 40f
        )
        assertEquals(custom, SubtitleStyle.parse(custom.serialize(), base))
    }

    @Test
    fun brokenStyleTextFallsBack() {
        val fallback = SubtitleStyle.defaultFor(SubtitleSlot.S2)
        assertEquals(fallback, SubtitleStyle.parse(null, fallback))
        assertEquals(fallback, SubtitleStyle.parse("  ", fallback))
        assertEquals(fallback, SubtitleStyle.parse("size=abc;;x=;=5;junk", fallback))
        val partial = SubtitleStyle.parse("size=30;bold=1", fallback)
        assertEquals(30f, partial.sizeSp, 0.0001f)
        assertTrue(partial.bold)
        assertEquals(fallback.colorArgb, partial.colorArgb)
    }

    @Test
    fun styleValuesAreKeptInRange() {
        val wild = SubtitleStyle.parse("size=500;outline=-4;bgOpacity=7;x=-20;y=250", SubtitleStyle.defaultFor(SubtitleSlot.S1))
        assertEquals(48f, wild.sizeSp, 0.0001f)
        assertEquals(0f, wild.outlineWidth, 0.0001f)
        assertEquals(1f, wild.backgroundOpacity, 0.0001f)
        assertEquals(0f, wild.xPercent, 0.0001f)
        assertEquals(100f, wild.yPercent, 0.0001f)
    }

    // ---- Text decoding ----

    // No yeh in this text: Windows-1256 cannot store the Persian yeh.
    private val persian = "سلام گل پژمان چ"

    @Test
    fun utf8IsRead() {
        assertEquals(persian, TextDecoder.decode(persian.toByteArray(Charsets.UTF_8), null))
    }

    @Test
    fun byteOrderMarksAreHandled() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(persian, TextDecoder.decode(bom + persian.toByteArray(Charsets.UTF_8), null))

        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + persian.toByteArray(Charsets.UTF_16LE)
        assertEquals(persian, TextDecoder.decode(le, null))
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + persian.toByteArray(Charsets.UTF_16BE)
        assertEquals(persian, TextDecoder.decode(be, null))
    }

    @Test
    fun arabicYehAndKafBecomePersianInPersianText() {
        val old = "\u064A\u0643".toByteArray(Charset.forName("windows-1256"))
        assertEquals("\u06CC\u06A9", TextDecoder.decode(old, "fa"))
        // Other languages are left alone.
        assertEquals("\u064A\u0643", TextDecoder.decode(old, "ar"))
    }

    @Test
    fun persianWindows1256FilesAreConvertedToUtf8() {
        val old = persian.toByteArray(Charset.forName("windows-1256"))
        assertEquals(persian, TextDecoder.decode(old, "fa"))
        assertEquals(persian, TextDecoder.decode(old, "ar"))
    }
}
