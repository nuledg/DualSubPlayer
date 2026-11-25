package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefinementTest {

    // ---- Forward mode ----

    @Test
    fun forwardModeLastsUntilTheSentenceEnds() {
        val f = ForwardSession()
        assertFalse(f.active)
        f.start(6000)
        assertTrue(f.active)
        f.onPosition(5999); assertTrue(f.active)
        f.onPosition(6000); assertFalse(f.active)
    }

    // ---- When subtitles are shown ----

    @Test
    fun visibilityFollowsTheTickedSituations() {
        val helperDefault = SubtitleVisibility.defaultFor(SubtitleSlot.S2)
        assertFalse(SubtitleVisibility.isVisible(helperDefault, paused = false, rewindActive = false, forwardActive = false))
        assertFalse(SubtitleVisibility.isVisible(helperDefault, paused = true, rewindActive = false, forwardActive = false))
        assertTrue(SubtitleVisibility.isVisible(helperDefault, paused = false, rewindActive = true, forwardActive = false))
        assertFalse(SubtitleVisibility.isVisible(helperDefault, paused = false, rewindActive = false, forwardActive = true))

        val main = SubtitleVisibility.defaultFor(SubtitleSlot.S1)
        assertTrue(SubtitleVisibility.isVisible(main, paused = false, rewindActive = false, forwardActive = false))
        assertTrue(SubtitleVisibility.isVisible(main, paused = true, rewindActive = false, forwardActive = false))
    }

    @Test
    fun anyCombinationIsPossible() {
        // Both subtitles hidden during normal playback, both shown after a backward or forward tap.
        val mask = SubtitleVisibility.AFTER_REWIND or SubtitleVisibility.AFTER_FORWARD
        assertFalse(SubtitleVisibility.isVisible(mask, false, false, false))
        assertTrue(SubtitleVisibility.isVisible(mask, false, true, false))
        assertTrue(SubtitleVisibility.isVisible(mask, true, false, true))
        // Shown only while paused
        assertTrue(SubtitleVisibility.isVisible(SubtitleVisibility.WHILE_PAUSED, true, false, false))
        assertFalse(SubtitleVisibility.isVisible(SubtitleVisibility.WHILE_PAUSED, false, false, false))
        // Never
        assertFalse(SubtitleVisibility.isVisible(0, true, true, true))
    }

    // ---- Languages ----

    @Test
    fun anyLanguageWorks() {
        assertEquals("es", LanguageCodes.fromFileName("Movie.2020.spa.srt"))
        assertEquals("de", LanguageCodes.fromFileName("Movie.German.srt"))
        assertEquals("ja", LanguageCodes.fromFileName("Movie.ja.srt"))
        assertEquals("pt", LanguageCodes.normalize("por"))
        assertEquals("zh", LanguageCodes.normalize("chi"))
        assertEquals("fa", LanguageCodes.fromFileName("Movie.farsi.srt"))
        assertNull(LanguageCodes.fromFileName("Movie.2020.srt"))
        assertEquals("Persian (فارسی)", LanguageCodes.displayName("fa"))
        assertEquals("English", LanguageCodes.displayName("en"))
        assertEquals("XX", LanguageCodes.displayName("xx"))
        assertTrue(LanguageCodes.all().size > 60)
        assertEquals(LanguageCodes.all().size, LanguageCodes.all().map { it.code }.toSet().size)
    }

    @Test
    fun hearingImpairedTagIsNotHindi() {
        assertEquals("en", LanguageCodes.fromFileName("Movie.en.hi.srt"))
        assertEquals("hi", LanguageCodes.fromFileName("Movie.hi.srt"))
        // Only look at the last tokens when the name still holds a title
        assertNull(LanguageCodes.fromFileName("Is.It.Safe.2020.srt", maxTokensFromEnd = 2))
    }

    @Test
    fun oldCodePagesOfOtherLanguagesAreDecoded() {
        val russian = "Привет, как дела? Это проверка кодировки."
        assertEquals(russian, TextDecoder.decode(russian.toByteArray(charset("windows-1251")), "ru"))
        val turkish = "Çok güzel bir gün, şimdi gidelim. İyi akşamlar."
        assertEquals(turkish, TextDecoder.decode(turkish.toByteArray(charset("windows-1254")), "tr"))
        val polish = "Zażółć gęślą jaźń, dzień dobry."
        assertEquals(polish, TextDecoder.decode(polish.toByteArray(charset("windows-1250")), "pl"))
    }
}
